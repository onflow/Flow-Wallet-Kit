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

// Re-export crypto utilities (except types that conflict with types/index.js)
export {
  // Interfaces
  type SymmetricEncryption,
  type EncryptedData,
  type EncryptedVault,
  type PasswordEncryptionOptions,
  // Classes
  AESGCMCipher,
  ChaChaPolyCipher,
  Hasher,
  PasswordEncryption,
  // Enums (renamed to avoid conflict)
  EncryptionAlgorithm
} from './crypto/index.js';

// Re-export utility functions (except types that conflict with types/index.js)
export {
  // Error classes and utilities (renamed to avoid conflict)
  WalletError as WalletErrorClass,
  WalletErrors,
  createWalletError,
  isWalletError,
  // Crypto utilities
  isNode,
  isWebCryptoAvailable,
  getCrypto,
  getRandomBytes,
  generateSalt,
  generateNonce,
  arrayBufferToBase64,
  base64ToArrayBuffer,
  hexToBytes,
  bytesToHex,
  concatBytes,
  constantTimeEqual,
  deriveKey,
  stringToBytes,
  bytesToString
} from './utils/index.js';

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

// Re-export storage implementations
export {
  // Base protocol
  BaseStorageProtocol,
  // Storage providers
  InMemoryProvider,
  FileSystemProvider,
  EncryptedStorageProvider,
  // Factory functions
  createStorageProvider,
  createEncryptedFileSystemStorage,
  createEncryptedMemoryStorage,
  // Re-export types
  type StorageProtocol,
  SecurityLevel
} from './storage/index.js';

// Re-export key implementations
export {
  // Base classes
  BaseKeyProtocol,
  // Key providers
  PrivateKeyProvider,
  SeedPhraseProvider,
  // Constants
  FLOW_DEFAULT_PATH,
  // Curve utilities
  getCurve,
  getHasher,
  generatePrivateKey,
  derivePublicKey,
  getCompressedPublicKey,
  isValidPrivateKey,
  sign,
  verify,
  privateKeyToHex,
  privateKeyFromHex,
  publicKeyToHex,
  publicKeyFromHex,
  getPublicKeyPoint,
  normalizeSignature,
  // Re-export key types
  KeyType,
  SigningAlgorithm,
  HashingAlgorithm,
  type KeyProtocol,
  type KeyCreationOptions,
  type SeedPhraseKeyOptions,
  type PrivateKeyOptions,
  type KeyStatic
} from './keys/index.js';

// Re-export wallet implementations
export {
  // Base wallet
  Wallet,
  // Wallet implementations
  KeyWallet,
  WatchWallet,
  // Account
  Account,
  // Factory functions
  createKeyWallet,
  createWatchWallet,
  // Types
  WalletType,
  WalletState
} from './wallet/index.js';

// Re-export network implementations
export {
  // Network services
  KeyIndexer,
  NetworkManager,
  // Types
  ChainId
} from './network/index.js';

// Future exports will include:
// - SecureElementKey (Web Crypto API non-extractable keys)
// - Wallet factory functions
// - Transaction builders and signers