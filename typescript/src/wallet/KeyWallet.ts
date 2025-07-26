/**
 * Key wallet implementation - wallet with signing capabilities
 */

import { Wallet } from './Wallet.js';
import type { 
  KeyWallet as IKeyWallet,
  WalletType,
  WalletConfiguration
} from '../types/wallet.js';
import type { ChainId } from '../types/network.js';
import type { KeyProtocol, SigningAlgorithm } from '../types/key.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { bytesToHex } from '../utils/crypto.js';

/**
 * Wallet with signing capabilities
 */
export class KeyWallet extends Wallet implements IKeyWallet {
  readonly type = WalletType.KEY;
  readonly key: KeyProtocol;

  constructor(
    key: KeyProtocol,
    networks: ChainId[],
    configuration?: WalletConfiguration
  ) {
    super(networks, configuration);
    this.key = key;
  }

  /**
   * Get public keys for account discovery
   */
  protected getPublicKeys(): string[] {
    const publicKeys: string[] = [];
    
    // Get public keys for both supported algorithms
    const p256Key = this.key.publicKey(SigningAlgorithm.ECDSA_P256);
    const secp256k1Key = this.key.publicKey(SigningAlgorithm.ECDSA_SECP256K1);
    
    if (p256Key) {
      publicKeys.push(bytesToHex(p256Key));
    }
    
    if (secp256k1Key) {
      publicKeys.push(bytesToHex(secp256k1Key));
    }
    
    return publicKeys;
  }

  /**
   * Fetch accounts for all networks
   */
  async fetchAccounts(): Promise<void> {
    const publicKeys = this.getPublicKeys();
    
    if (publicKeys.length === 0) {
      throw new WalletError(
        WalletErrorCode.InvalidPublicKey,
        'No public keys available from key provider'
      );
    }

    // Fetch accounts for each network in parallel
    const fetchPromises = Array.from(this._networks).map(async chainId => {
      try {
        // Find accounts for all public keys
        const accountsMap = await this._keyIndexer.findAccountsByMultipleKeys(
          publicKeys,
          chainId
        );
        
        // Collect all unique accounts
        const accountsSet = new Set<string>();
        const accounts: import('../types/wallet.js').Account[] = [];
        
        for (const [publicKey, flowAccounts] of accountsMap) {
          for (const flowAccount of flowAccounts) {
            if (!accountsSet.has(flowAccount.address)) {
              accountsSet.add(flowAccount.address);
              accounts.push(this.createAccountFromKey(flowAccount, chainId, this.key));
            }
          }
        }
        
        this.setAccounts(chainId, accounts);
      } catch (error) {
        // Log error but don't fail the entire fetch
        console.error(`Failed to fetch accounts for ${chainId}:`, error);
        this.setAccounts(chainId, []);
      }
    });
    
    await Promise.all(fetchPromises);
  }

  /**
   * Sign a message
   */
  async sign(
    message: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: import('../types/key.js').HashingAlgorithm
  ): Promise<Uint8Array> {
    if (!this.isConnected()) {
      throw new WalletError(
        WalletErrorCode.WalletNotConnected,
        'Wallet must be connected to sign'
      );
    }
    
    return this.key.sign(message, signAlgo, hashAlgo);
  }

  /**
   * Get wallet display information
   */
  getDisplayInfo(): {
    type: WalletType;
    keyType: import('../types/key.js').KeyType;
    isConnected: boolean;
    accountCount: number;
  } {
    let accountCount = 0;
    for (const accounts of this._accounts.values()) {
      accountCount += accounts.length;
    }
    
    return {
      type: this.type,
      keyType: this.key.keyType,
      isConnected: this.isConnected(),
      accountCount
    };
  }

  /**
   * Create a new key wallet
   */
  static async create(
    key: KeyProtocol,
    networks: ChainId[],
    configuration?: WalletConfiguration
  ): Promise<KeyWallet> {
    const wallet = new KeyWallet(key, networks, configuration);
    await wallet.connect();
    return wallet;
  }
}