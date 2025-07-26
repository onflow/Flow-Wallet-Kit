/**
 * Network configuration and management
 */

import { ChainId, ChainConfig, CHAIN_CONFIGS } from '../types/network.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';

/**
 * Network manager for handling chain configurations
 */
export class NetworkManager {
  private static instance: NetworkManager;
  private configs: Map<ChainId, ChainConfig>;
  private currentChain: ChainId;

  private constructor() {
    this.configs = new Map();
    this.currentChain = ChainId.MAINNET;
    this.initializeDefaultConfigs();
  }

  /**
   * Get singleton instance
   */
  static getInstance(): NetworkManager {
    if (!NetworkManager.instance) {
      NetworkManager.instance = new NetworkManager();
    }
    return NetworkManager.instance;
  }

  /**
   * Initialize default chain configurations
   */
  private initializeDefaultConfigs(): void {
    // Add default configs
    this.configs.set(ChainId.MAINNET, CHAIN_CONFIGS[ChainId.MAINNET]!);
    this.configs.set(ChainId.TESTNET, CHAIN_CONFIGS[ChainId.TESTNET]!);
    this.configs.set(ChainId.EMULATOR, CHAIN_CONFIGS[ChainId.EMULATOR]!);
  }

  /**
   * Get configuration for a chain
   */
  getChainConfig(chainId: ChainId): ChainConfig {
    const config = this.configs.get(chainId);
    if (!config) {
      throw new WalletError(
        WalletErrorCode.InvalidChainId,
        `No configuration found for chain: ${chainId}`
      );
    }
    return config;
  }

  /**
   * Add custom chain configuration
   */
  addChainConfig(chainId: ChainId, config: ChainConfig): void {
    this.configs.set(chainId, config);
  }

  /**
   * Get current chain
   */
  getCurrentChain(): ChainId {
    return this.currentChain;
  }

  /**
   * Set current chain
   */
  setCurrentChain(chainId: ChainId): void {
    if (!this.configs.has(chainId)) {
      throw new WalletError(
        WalletErrorCode.InvalidChainId,
        `No configuration found for chain: ${chainId}`
      );
    }
    this.currentChain = chainId;
  }

  /**
   * Get all configured chains
   */
  getConfiguredChains(): ChainId[] {
    return Array.from(this.configs.keys());
  }

  /**
   * Get Key Indexer URL for a chain
   */
  getKeyIndexerUrl(chainId: ChainId): string {
    const config = this.getChainConfig(chainId);
    return config.keyIndexerUrl;
  }

  /**
   * Get Access Node URL for a chain
   */
  getAccessNodeUrl(chainId: ChainId): string {
    const config = this.getChainConfig(chainId);
    return config.accessNode;
  }

  /**
   * Check if a chain is configured
   */
  hasChain(chainId: ChainId): boolean {
    return this.configs.has(chainId);
  }

  /**
   * Reset to default configurations
   */
  reset(): void {
    this.configs.clear();
    this.currentChain = ChainId.MAINNET;
    this.initializeDefaultConfigs();
  }
}