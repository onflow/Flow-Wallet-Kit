/**
 * Base error class for Flow Wallet Kit
 */
export class WalletError extends Error {
  constructor(
    public readonly code: WalletErrorCode,
    public override readonly message: string,
    public override readonly cause?: unknown
  ) {
    super(message);
    this.name = 'WalletError';
    
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, WalletError);
    }
  }
}

/**
 * Error codes matching the Android/iOS implementations
 */
export enum WalletErrorCode {
  // General Errors
  NoImplement = 0,
  EmptyKeychain = 1,
  EmptyKey = 2,
  EmptySignKey = 3,

  // Cryptographic Errors
  UnsupportedHashAlgorithm = 4,
  UnsupportedSignatureAlgorithm = 5,
  InitChaChaPolyFailed = 6,
  InitHDWalletFailed = 7,
  InitPrivateKeyFailed = 8,
  RestoreWalletFailed = 9,
  InvalidSignatureAlgorithm = 10,
  InvalidEVMAddress = 11,
  UnsupportedKeyFormat = 33,
  InvalidMnemonic = 34,

  // Authentication Errors
  InvalidPassword = 12,
  InvalidPrivateKey = 13,
  InvalidKeyStoreJSON = 14,
  InvalidKeyStorePassword = 15,
  SignError = 16,
  InitPublicKeyFailed = 17,
  FailedPassSecurityCheck = 32,

  // Network Errors
  IncorrectKeyIndexerURL = 18,
  KeyIndexerRequestFailed = 19,
  DecodeKeyIndexerFailed = 20,

  // Storage Errors
  LoadCacheFailed = 21,
  RemoveCacheFailed = 31,
  InvalidWalletType = 22,

  // Connection Errors
  InvalidConnectionType = 23,
  ConnectionFailed = 24,
  DisconnectionFailed = 25,
  InvalidDeepLink = 26,
  SessionExpired = 27,
  InvalidSession = 28,
  NetworkNotSupported = 29,
  ConnectionTimeout = 30,

  // Additional TypeScript-specific errors
  EncryptionFailed = 35,
  DecryptionFailed = 36,
  InvalidNonce = 37,
  InvalidSalt = 38,
  InvalidTag = 39,
  CryptoNotAvailable = 40,
}

/**
 * Predefined wallet errors for common scenarios
 */
export const WalletErrors = {
  // General Errors
  NoImplement: new WalletError(WalletErrorCode.NoImplement, 'Operation or feature not implemented'),
  EmptyKeychain: new WalletError(WalletErrorCode.EmptyKeychain, 'No keys found in keychain'),
  EmptyKey: new WalletError(WalletErrorCode.EmptyKey, 'Key data is empty or invalid'),
  EmptySignKey: new WalletError(WalletErrorCode.EmptySignKey, 'Signing key is empty or not available'),

  // Cryptographic Errors
  UnsupportedHashAlgorithm: new WalletError(WalletErrorCode.UnsupportedHashAlgorithm, 'Hash algorithm not supported'),
  UnsupportedSignatureAlgorithm: new WalletError(WalletErrorCode.UnsupportedSignatureAlgorithm, 'Signature algorithm not supported'),
  InitChaChaPolyFailed: new WalletError(WalletErrorCode.InitChaChaPolyFailed, 'Failed to initialize ChaCha20-Poly1305'),
  InitHDWalletFailed: new WalletError(WalletErrorCode.InitHDWalletFailed, 'Failed to initialize HD wallet'),
  InitPrivateKeyFailed: new WalletError(WalletErrorCode.InitPrivateKeyFailed, 'Failed to initialize private key'),
  RestoreWalletFailed: new WalletError(WalletErrorCode.RestoreWalletFailed, 'Failed to restore wallet from backup'),
  InvalidSignatureAlgorithm: new WalletError(WalletErrorCode.InvalidSignatureAlgorithm, 'Invalid signature algorithm specified'),
  InvalidEVMAddress: new WalletError(WalletErrorCode.InvalidEVMAddress, 'Invalid EVM address'),
  UnsupportedKeyFormat: new WalletError(WalletErrorCode.UnsupportedKeyFormat, 'Key format not supported'),
  InvalidMnemonic: new WalletError(WalletErrorCode.InvalidMnemonic, 'Invalid mnemonic phrase'),

  // Authentication Errors
  InvalidPassword: new WalletError(WalletErrorCode.InvalidPassword, 'Invalid password provided'),
  InvalidPrivateKey: new WalletError(WalletErrorCode.InvalidPrivateKey, 'Invalid private key format'),
  InvalidKeyStoreJSON: new WalletError(WalletErrorCode.InvalidKeyStoreJSON, 'Invalid KeyStore JSON format'),
  InvalidKeyStorePassword: new WalletError(WalletErrorCode.InvalidKeyStorePassword, 'Invalid KeyStore password'),
  SignError: new WalletError(WalletErrorCode.SignError, 'Error during signing operation'),
  InitPublicKeyFailed: new WalletError(WalletErrorCode.InitPublicKeyFailed, 'Failed to initialize public key'),
  FailedPassSecurityCheck: new WalletError(WalletErrorCode.FailedPassSecurityCheck, 'Security check failed'),

  // Storage Errors
  LoadCacheFailed: new WalletError(WalletErrorCode.LoadCacheFailed, 'Failed to load data from cache'),
  RemoveCacheFailed: new WalletError(WalletErrorCode.RemoveCacheFailed, 'Failed to remove data from cache'),
  InvalidWalletType: new WalletError(WalletErrorCode.InvalidWalletType, 'Invalid wallet type for operation'),

  // TypeScript-specific errors
  EncryptionFailed: new WalletError(WalletErrorCode.EncryptionFailed, 'Encryption operation failed'),
  DecryptionFailed: new WalletError(WalletErrorCode.DecryptionFailed, 'Decryption operation failed'),
  InvalidNonce: new WalletError(WalletErrorCode.InvalidNonce, 'Invalid nonce provided'),
  InvalidSalt: new WalletError(WalletErrorCode.InvalidSalt, 'Invalid salt provided'),
  InvalidTag: new WalletError(WalletErrorCode.InvalidTag, 'Invalid authentication tag'),
  CryptoNotAvailable: new WalletError(WalletErrorCode.CryptoNotAvailable, 'Crypto API not available in this environment'),
};

/**
 * Helper function to create a new WalletError with a custom message
 */
export function createWalletError(code: WalletErrorCode, message: string, cause?: unknown): WalletError {
  return new WalletError(code, message, cause);
}

/**
 * Type guard to check if an error is a WalletError
 */
export function isWalletError(error: unknown): error is WalletError {
  return error instanceof WalletError;
}