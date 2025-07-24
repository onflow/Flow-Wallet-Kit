/**
 * Protocol defining symmetric encryption operations
 * Provides a unified interface for different symmetric encryption algorithms
 */

/**
 * Interface for symmetric encryption implementations
 */
export interface SymmetricEncryption {
  /**
   * Symmetric key used for encryption/decryption
   */
  readonly key: Uint8Array;

  /**
   * Size of the symmetric key in bits
   */
  readonly keySize: number;

  /**
   * Encrypt data using the symmetric key
   * @param data Data to encrypt
   * @returns Encrypted data with authentication tag and nonce
   */
  encrypt(data: Uint8Array): Promise<Uint8Array>;

  /**
   * Decrypt data using the symmetric key
   * @param combinedData Encrypted data with authentication tag and nonce
   * @returns Original decrypted data
   */
  decrypt(combinedData: Uint8Array): Promise<Uint8Array>;
}

/**
 * Combined encrypted data format
 */
export interface EncryptedData {
  /**
   * Initialization vector/nonce
   */
  nonce: Uint8Array;
  
  /**
   * Encrypted ciphertext with authentication tag
   */
  ciphertext: Uint8Array;
}