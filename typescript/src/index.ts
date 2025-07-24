/**
 * Flow Wallet Kit - TypeScript SDK
 * 
 * A cross-platform TypeScript SDK for integrating Flow blockchain wallet functionality
 * into web and Node.js applications. It provides secure interfaces for managing Flow
 * accounts and handling transactions across networks.
 * 
 * @packageDocumentation
 */

// Re-export all types
export * from './types/index.js';

// Export version
export const VERSION = '0.1.0';

/**
 * Flow Wallet Kit configuration
 */
export interface FlowWalletKitConfig {
  /**
   * Default network to use
   */
  defaultNetwork?: import('./types/network.js').ChainId;
  
  /**
   * Enable debug logging
   */
  debug?: boolean;
  
  /**
   * Custom RPC endpoints (overrides defaults)
   */
  rpcEndpoints?: Partial<Record<import('./types/network.js').ChainId, string>>;
  
  /**
   * Custom key indexer endpoints (overrides defaults)
   */
  keyIndexerEndpoints?: Partial<Record<import('./types/network.js').ChainId, string>>;
}

/**
 * Initialize Flow Wallet Kit
 * @param config - Configuration options
 */
export function init(config?: FlowWalletKitConfig): void {
  // Implementation will be added in subsequent modules
  console.log('Flow Wallet Kit initialized', config);
}

// Future exports will include:
// - Storage implementations (MemoryStorage, IndexedDBStorage, etc.)
// - Key implementations (PrivateKey, SeedPhraseKey, SecureElementKey)
// - Wallet implementations (KeyWallet, WatchWallet)
// - Network service implementation
// - Crypto utilities
// - Error classes