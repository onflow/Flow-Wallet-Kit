/**
 * Base wallet implementation
 */

import type { 
  Wallet as IWallet,
  WalletType,
  WalletConfiguration,
  WalletState,
  Account as IAccount
} from '../types/wallet.js';
import type { ChainId } from '../types/network.js';
import type { KeyProtocol } from '../types/key.js';
import { Account } from './Account.js';
import { KeyIndexer } from '../network/KeyIndexer.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { bytesToHex } from '../utils/crypto.js';

/**
 * Base wallet implementation
 */
export abstract class Wallet implements IWallet {
  abstract readonly type: WalletType;
  
  protected _state: WalletState = WalletState.DISCONNECTED;
  protected _accounts: Map<ChainId, IAccount[]> = new Map();
  protected _networks: Set<ChainId>;
  protected _currentNetwork: ChainId;
  protected _keyIndexer: KeyIndexer;
  protected _configuration: WalletConfiguration;

  constructor(
    networks: ChainId[],
    configuration: WalletConfiguration = {}
  ) {
    this._networks = new Set(networks);
    this._currentNetwork = networks[0] || ChainId.MAINNET;
    this._keyIndexer = new KeyIndexer();
    this._configuration = configuration;
  }

  /**
   * Get wallet state
   */
  get state(): WalletState {
    return this._state;
  }

  /**
   * Get supported networks
   */
  get networks(): readonly ChainId[] {
    return Array.from(this._networks);
  }

  /**
   * Get current network
   */
  get currentNetwork(): ChainId {
    return this._currentNetwork;
  }

  /**
   * Set current network
   */
  setCurrentNetwork(chainId: ChainId): void {
    if (!this._networks.has(chainId)) {
      throw new WalletError(
        WalletErrorCode.InvalidChainId,
        `Network ${chainId} is not supported by this wallet`
      );
    }
    this._currentNetwork = chainId;
  }

  /**
   * Get accounts for a network
   */
  getAccounts(chainId?: ChainId): readonly IAccount[] {
    const network = chainId || this._currentNetwork;
    return this._accounts.get(network) || [];
  }

  /**
   * Get all accounts across all networks
   */
  getAllAccounts(): Map<ChainId, readonly IAccount[]> {
    const result = new Map<ChainId, readonly IAccount[]>();
    for (const [chainId, accounts] of this._accounts) {
      result.set(chainId, accounts);
    }
    return result;
  }

  /**
   * Get account by address
   */
  getAccount(address: string, chainId?: ChainId): IAccount | null {
    const network = chainId || this._currentNetwork;
    const accounts = this._accounts.get(network) || [];
    return accounts.find(acc => acc.address === address) || null;
  }

  /**
   * Connect wallet
   */
  async connect(): Promise<void> {
    if (this._state === WalletState.CONNECTED) {
      return;
    }

    this._state = WalletState.CONNECTING;
    
    try {
      await this.fetchAccounts();
      this._state = WalletState.CONNECTED;
    } catch (error) {
      this._state = WalletState.DISCONNECTED;
      throw error;
    }
  }

  /**
   * Disconnect wallet
   */
  async disconnect(): Promise<void> {
    this._state = WalletState.DISCONNECTED;
    this._accounts.clear();
    this._keyIndexer.clearCache();
  }

  /**
   * Check if wallet is connected
   */
  isConnected(): boolean {
    return this._state === WalletState.CONNECTED;
  }

  /**
   * Check if wallet is connecting
   */
  isConnecting(): boolean {
    return this._state === WalletState.CONNECTING;
  }

  /**
   * Add network support
   */
  addNetwork(chainId: ChainId): void {
    this._networks.add(chainId);
  }

  /**
   * Remove network support
   */
  removeNetwork(chainId: ChainId): void {
    if (chainId === this._currentNetwork) {
      throw new WalletError(
        WalletErrorCode.InvalidChainId,
        'Cannot remove current network'
      );
    }
    this._networks.delete(chainId);
    this._accounts.delete(chainId);
  }

  /**
   * Set configuration
   */
  setConfiguration(config: Partial<WalletConfiguration>): void {
    this._configuration = { ...this._configuration, ...config };
  }

  /**
   * Get configuration
   */
  getConfiguration(): WalletConfiguration {
    return { ...this._configuration };
  }

  /**
   * Fetch accounts (to be implemented by subclasses)
   */
  abstract fetchAccounts(): Promise<void>;

  /**
   * Protected method to set accounts
   */
  protected setAccounts(chainId: ChainId, accounts: IAccount[]): void {
    this._accounts.set(chainId, accounts);
  }

  /**
   * Protected method to clear accounts
   */
  protected clearAccounts(chainId?: ChainId): void {
    if (chainId) {
      this._accounts.delete(chainId);
    } else {
      this._accounts.clear();
    }
  }

  /**
   * Get public keys for account discovery
   */
  protected abstract getPublicKeys(): string[];

  /**
   * Create account from key (for key wallets)
   */
  protected createAccountFromKey(
    flowAccount: import('../types/network.js').FlowAccount,
    chainId: ChainId,
    key: KeyProtocol
  ): IAccount {
    return Account.fromFlowAccount(flowAccount, chainId, key);
  }

  /**
   * Create watch-only account
   */
  protected createWatchAccount(
    flowAccount: import('../types/network.js').FlowAccount,
    chainId: ChainId
  ): IAccount {
    return Account.fromFlowAccount(flowAccount, chainId, null);
  }
}