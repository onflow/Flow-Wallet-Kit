/**
 * Flow account implementation
 */

import type { 
  Account as IAccount,
  ChildAccount,
  COA,
  SecurityCheckDelegate,
  TokenBalance,
  AccountCache
} from '../types/wallet.js';
import type { 
  FlowAccount,
  ChainId
} from '../types/network.js';
import type { KeyProtocol, SigningAlgorithm, HashingAlgorithm } from '../types/key.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { bytesToHex } from '../utils/crypto.js';

/**
 * Flow account implementation
 */
export class Account implements IAccount {
  readonly account: FlowAccount;
  readonly chainID: ChainId;
  readonly key: KeyProtocol | null;
  readonly keyIndex: number = 0;
  
  private _childAccounts: ChildAccount[] | null = null;
  private _coa: COA | null = null;
  private _securityDelegate?: SecurityCheckDelegate;
  private _tokenBalances: TokenBalance[] = [];
  private _isLoading: boolean = false;

  constructor(
    flowAccount: FlowAccount,
    chainId: ChainId,
    key: KeyProtocol | null = null
  ) {
    this.account = flowAccount;
    this.chainID = chainId;
    this.key = key;
  }

  /**
   * Get account address
   */
  get address(): string {
    return this.account.address;
  }

  /**
   * Get hex address
   */
  get hexAddr(): string {
    return this.account.address;
  }

  /**
   * Get loading state
   */
  get isLoading(): boolean {
    return this._isLoading;
  }

  /**
   * Set loading state
   */
  setLoading(loading: boolean): void {
    this._isLoading = loading;
  }

  /**
   * Get token balances
   */
  get tokenBalances(): readonly TokenBalance[] {
    return this._tokenBalances;
  }

  /**
   * Get child accounts
   */
  get childs(): ChildAccount[] | null {
    return this._childAccounts;
  }

  /**
   * Get COA (Cadence Owned Account)
   */
  get coa(): COA | null {
    return this._coa;
  }

  /**
   * Check if account has child accounts
   */
  get hasChild(): boolean {
    return this._childAccounts !== null && this._childAccounts.length > 0;
  }

  /**
   * Check if account has COA
   */
  get hasCOA(): boolean {
    return this._coa !== null;
  }

  /**
   * Check if account has any linked accounts
   */
  get hasLinkedAccounts(): boolean {
    return this.hasChild || this.hasCOA;
  }

  /**
   * Check if account can sign transactions
   */
  get canSign(): boolean {
    return this.key !== null;
  }

  /**
   * Check if account has full weight key
   */
  get hasFullWeightKey(): boolean {
    if (!this.account.keys) return false;
    
    // Keys is a Set in the type definition
    for (const key of this.account.keys) {
      if (key.weight >= 1000) {
        return true;
      }
    }
    return false;
  }

  /**
   * Sign a transaction
   */
  async sign(
    transaction: unknown,
    bytes: Uint8Array
  ): Promise<Uint8Array> {
    if (!this.key) {
      throw new WalletError(
        WalletErrorCode.EmptySignKey,
        'Account does not have signing capability'
      );
    }

    // Use security delegate if configured
    if (this._securityDelegate) {
      const approved = await this._securityDelegate.verify();
      if (!approved) {
        throw new WalletError(
          WalletErrorCode.SecurityCheckFailed,
          'Security check failed'
        );
      }
    }

    // Get the first key's algorithms (simplified for now)
    const firstKey = Array.from(this.account.keys)[0];
    if (!firstKey) {
      throw new WalletError(
        WalletErrorCode.EmptySignKey,
        'No keys found on account'
      );
    }

    return this.key.sign(bytes, firstKey.signAlgo, firstKey.hashAlgo);
  }

  /**
   * Get public key for signing
   */
  getPublicKey(signAlgo: SigningAlgorithm): string | null {
    if (!this.key) {
      return null;
    }

    const publicKey = this.key.publicKey(signAlgo);
    return publicKey ? bytesToHex(publicKey) : null;
  }

  /**
   * Get cache data
   */
  getCacheData(): AccountCache {
    return {
      childs: this._childAccounts,
      coa: this._coa
    };
  }

  /**
   * Set cache data
   */
  setCacheData(data: AccountCache): void {
    this._childAccounts = data.childs;
    this._coa = data.coa;
  }

  /**
   * Add child account
   */
  addChildAccount(child: ChildAccount): void {
    if (!this._childAccounts) {
      this._childAccounts = [];
    }

    // Check if already exists
    const existing = this._childAccounts.find(c => c.address === child.address);
    if (existing) {
      throw new WalletError(
        WalletErrorCode.InvalidAccount,
        `Child account ${child.address} already exists`
      );
    }

    this._childAccounts.push(child);
  }

  /**
   * Remove child account
   */
  removeChildAccount(address: string): boolean {
    if (!this._childAccounts) {
      return false;
    }

    const index = this._childAccounts.findIndex(c => c.address === address);
    if (index === -1) {
      return false;
    }

    this._childAccounts.splice(index, 1);
    return true;
  }

  /**
   * Set COA
   */
  setCOA(coa: COA): void {
    this._coa = coa;
  }

  /**
   * Set security delegate
   */
  setSecurityDelegate(delegate: SecurityCheckDelegate): void {
    this._securityDelegate = delegate;
  }

  /**
   * Update token balance
   */
  updateTokenBalance(balance: TokenBalance): void {
    const index = this._tokenBalances.findIndex(
      b => b.contractAddress === balance.contractAddress
    );
    
    if (index >= 0) {
      this._tokenBalances[index] = balance;
    } else {
      this._tokenBalances.push(balance);
    }
  }

  /**
   * Get token balance
   */
  getTokenBalance(contractAddress: string): TokenBalance | undefined {
    return this._tokenBalances.find(b => b.contractAddress === contractAddress);
  }

  /**
   * Create account from Flow account data
   */
  static fromFlowAccount(
    flowAccount: FlowAccount,
    chainId: ChainId,
    key: KeyProtocol | null = null
  ): Account {
    return new Account(flowAccount, chainId, key);
  }

  /**
   * Export account data
   */
  toJSON(): object {
    return {
      address: this.address,
      chainId: this.chainID,
      balance: this.account.balance,
      canSign: this.canSign,
      keys: Array.from(this.account.keys || []),
      contracts: Object.keys(this.account.contracts || {}),
      childAccounts: this._childAccounts,
      coa: this._coa,
      tokenBalances: this.tokenBalances
    };
  }

  /**
   * Get account display information
   */
  getDisplayInfo(): {
    address: string;
    shortAddress: string;
    chainId: ChainId;
    balance: number;
    canSign: boolean;
  } {
    return {
      address: this.address,
      shortAddress: `${this.address.slice(0, 6)}...${this.address.slice(-4)}`,
      chainId: this.chainID,
      balance: this.account.balance,
      canSign: this.canSign
    };
  }

  /**
   * Check if account has a specific key
   */
  hasKey(publicKey: string): boolean {
    if (!this.account.keys) return false;
    
    for (const key of this.account.keys) {
      if (key.publicKey === publicKey) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get key by public key
   */
  getKey(publicKey: string): AccountPublicKey | undefined {
    if (!this.account.keys) return undefined;
    
    for (const key of this.account.keys) {
      if (key.publicKey === publicKey) {
        return key;
      }
    }
    return undefined;
  }

  /**
   * Clone account
   */
  clone(): Account {
    const cloned = new Account(this.account, this.chainID, this.key);
    cloned._childAccounts = this._childAccounts ? [...this._childAccounts] : null;
    cloned._coa = this._coa;
    cloned._tokenBalances = [...this._tokenBalances];
    cloned._securityDelegate = this._securityDelegate;
    cloned._isLoading = this._isLoading;
    return cloned;
  }
}