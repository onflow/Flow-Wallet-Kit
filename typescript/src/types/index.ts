/**
 * Flow Wallet Kit - TypeScript Types
 * 
 * Main export file for all type definitions.
 * This module re-exports all types and interfaces from the individual type modules.
 * 
 * @packageDocumentation
 */

// Storage types
export type {
  StorageProtocol,
  Cacheable
} from './storage.js';

export {
  SecurityLevel
} from './storage.js';

// Key management types
export type {
  KeyProtocol,
  KeyStatic,
  KeyCreationOptions,
  SeedPhraseKeyOptions,
  PrivateKeyOptions
} from './key.js';

export {
  KeyType,
  SigningAlgorithm,
  HashingAlgorithm
} from './key.js';

// Network types
export type {
  ChainConfig,
  AccountPublicKey,
  AccountExpandable,
  AccountLink,
  FlowAccount,
  KeyIndexerResponse,
  KeyIndexerAccount,
  NetworkService
} from './network.js';

export {
  ChainId,
  CHAIN_CONFIGS
} from './network.js';

// Wallet types
export type {
  SecurityCheckDelegate,
  TokenBalance,
  ChildAccount,
  COA,
  AccountCache,
  Account,
  Wallet,
  WatchWallet,
  KeyWallet,
  WalletFactory,
  WalletConfig
} from './wallet.js';

export {
  WalletType
} from './wallet.js';

// Error types
export interface WalletError extends Error {
  /**
   * Error code for programmatic handling
   */
  code: WalletErrorCode;
  
  /**
   * Human-readable error message
   */
  message: string;
  
  /**
   * Optional cause of the error
   */
  cause?: unknown;
}

/**
 * Error codes for wallet operations
 */
export enum WalletErrorCode {
  // General Errors
  NO_IMPLEMENT = 'NO_IMPLEMENT',
  EMPTY_KEYCHAIN = 'EMPTY_KEYCHAIN',
  EMPTY_KEY = 'EMPTY_KEY',
  EMPTY_SIGN_KEY = 'EMPTY_SIGN_KEY',
  
  // Cryptographic Errors
  UNSUPPORTED_HASH_ALGORITHM = 'UNSUPPORTED_HASH_ALGORITHM',
  UNSUPPORTED_SIGNATURE_ALGORITHM = 'UNSUPPORTED_SIGNATURE_ALGORITHM',
  INIT_CHACHA_POLY_FAILED = 'INIT_CHACHA_POLY_FAILED',
  INIT_HD_WALLET_FAILED = 'INIT_HD_WALLET_FAILED',
  INIT_PRIVATE_KEY_FAILED = 'INIT_PRIVATE_KEY_FAILED',
  RESTORE_WALLET_FAILED = 'RESTORE_WALLET_FAILED',
  INVALID_SIGNATURE_ALGORITHM = 'INVALID_SIGNATURE_ALGORITHM',
  INVALID_EVM_ADDRESS = 'INVALID_EVM_ADDRESS',
  UNSUPPORTED_KEY_FORMAT = 'UNSUPPORTED_KEY_FORMAT',
  INVALID_MNEMONIC = 'INVALID_MNEMONIC',
  
  // Authentication Errors
  INVALID_PASSWORD = 'INVALID_PASSWORD',
  INVALID_PRIVATE_KEY = 'INVALID_PRIVATE_KEY',
  INVALID_KEY_STORE_JSON = 'INVALID_KEY_STORE_JSON',
  INVALID_KEY_STORE_PASSWORD = 'INVALID_KEY_STORE_PASSWORD',
  SIGN_ERROR = 'SIGN_ERROR',
  INIT_PUBLIC_KEY_FAILED = 'INIT_PUBLIC_KEY_FAILED',
  FAILED_PASS_SECURITY_CHECK = 'FAILED_PASS_SECURITY_CHECK',
  
  // Network Errors
  INCORRECT_KEY_INDEXER_URL = 'INCORRECT_KEY_INDEXER_URL',
  KEY_INDEXER_REQUEST_FAILED = 'KEY_INDEXER_REQUEST_FAILED',
  DECODE_KEY_INDEXER_FAILED = 'DECODE_KEY_INDEXER_FAILED',
  
  // Storage Errors
  LOAD_CACHE_FAILED = 'LOAD_CACHE_FAILED',
  REMOVE_CACHE_FAILED = 'REMOVE_CACHE_FAILED',
  INVALID_WALLET_TYPE = 'INVALID_WALLET_TYPE',
  
  // Connection Errors
  INVALID_CONNECTION_TYPE = 'INVALID_CONNECTION_TYPE',
  CONNECTION_FAILED = 'CONNECTION_FAILED',
  DISCONNECTION_FAILED = 'DISCONNECTION_FAILED',
  INVALID_DEEP_LINK = 'INVALID_DEEP_LINK',
  SESSION_EXPIRED = 'SESSION_EXPIRED',
  INVALID_SESSION = 'INVALID_SESSION',
  NETWORK_NOT_SUPPORTED = 'NETWORK_NOT_SUPPORTED',
  CONNECTION_TIMEOUT = 'CONNECTION_TIMEOUT'
}

/**
 * Utility type for nullable values
 */
export type Nullable<T> = T | null;

/**
 * Utility type for optional async operations
 */
export type MaybePromise<T> = T | Promise<T>;

/**
 * Hex string type (for addresses, public keys, etc.)
 */
export type HexString = string;

/**
 * Base64 string type (for encoded data)
 */
export type Base64String = string;