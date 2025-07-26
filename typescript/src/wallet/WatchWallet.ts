/**
 * Watch wallet implementation - read-only wallet
 */

import { Wallet } from './Wallet.js';
import type { 
  WatchWallet as IWatchWallet,
  WalletType,
  WalletConfiguration
} from '../types/wallet.js';
import type { ChainId } from '../types/network.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';

/**
 * Read-only wallet for monitoring accounts
 */
export class WatchWallet extends Wallet implements IWatchWallet {
  readonly type = WalletType.WATCH;
  readonly address: string;

  constructor(
    address: string,
    networks: ChainId[],
    configuration?: WalletConfiguration
  ) {
    super(networks, configuration);
    this.address = this.normalizeAddress(address);
  }

  /**
   * Normalize Flow address format
   */
  private normalizeAddress(address: string): string {
    // Remove 0x prefix if present
    let normalized = address.toLowerCase().replace(/^0x/, '');
    
    // Pad with zeros to 16 characters (8 bytes)
    normalized = normalized.padStart(16, '0');
    
    // Add 0x prefix
    return '0x' + normalized;
  }

  /**
   * Validate Flow address format
   */
  private validateAddress(address: string): boolean {
    // Flow addresses are 8 bytes (16 hex characters)
    const cleanAddress = address.replace(/^0x/, '');
    return /^[0-9a-fA-F]{1,16}$/.test(cleanAddress);
  }

  /**
   * Get public keys (not applicable for watch wallets)
   */
  protected getPublicKeys(): string[] {
    return [];
  }

  /**
   * Fetch accounts for all networks
   */
  async fetchAccounts(): Promise<void> {
    if (!this.validateAddress(this.address)) {
      throw new WalletError(
        WalletErrorCode.InvalidAccount,
        `Invalid Flow address: ${this.address}`
      );
    }

    // Fetch account data for each network
    const fetchPromises = Array.from(this._networks).map(async chainId => {
      try {
        // For watch wallets, we need to fetch account data directly
        // This is a simplified implementation - in reality, you'd use Flow SDK
        const flowAccount = await this.fetchAccountData(this.address, chainId);
        
        if (flowAccount) {
          const account = this.createWatchAccount(flowAccount, chainId);
          this.setAccounts(chainId, [account]);
        } else {
          this.setAccounts(chainId, []);
        }
      } catch (error) {
        console.error(`Failed to fetch account for ${chainId}:`, error);
        this.setAccounts(chainId, []);
      }
    });
    
    await Promise.all(fetchPromises);
  }

  /**
   * Fetch account data from Flow blockchain
   */
  private async fetchAccountData(
    address: string,
    chainId: ChainId
  ): Promise<import('../types/network.js').FlowAccount | null> {
    // This is a placeholder implementation
    // In a real implementation, you would use Flow SDK or REST API
    // to fetch actual account data from the blockchain
    
    // For now, return a mock account
    return {
      address: this.address,
      balance: 0,
      code: '',
      keys: new Set(),
      contracts: {}
    };
  }

  /**
   * Get wallet display information
   */
  getDisplayInfo(): {
    type: WalletType;
    address: string;
    shortAddress: string;
    isConnected: boolean;
    accountCount: number;
  } {
    let accountCount = 0;
    for (const accounts of this._accounts.values()) {
      accountCount += accounts.length;
    }
    
    return {
      type: this.type,
      address: this.address,
      shortAddress: `${this.address.slice(0, 6)}...${this.address.slice(-4)}`,
      isConnected: this.isConnected(),
      accountCount
    };
  }

  /**
   * Create a new watch wallet
   */
  static async create(
    address: string,
    networks: ChainId[],
    configuration?: WalletConfiguration
  ): Promise<WatchWallet> {
    const wallet = new WatchWallet(address, networks, configuration);
    await wallet.connect();
    return wallet;
  }
}