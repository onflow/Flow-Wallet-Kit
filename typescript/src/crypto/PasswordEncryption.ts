/**
 * Password-based encryption for vault/keyring data
 * Similar to FRW's vault pattern and browser-passworder approach
 */

import { AESGCMCipher } from './AESGCMCipher';
import { ChaChaPolyCipher } from './ChaChaPolyCipher';
import { SymmetricEncryption } from './SymmetricEncryption';
import { 
  WalletError, 
  WalletErrorCode, 
  generateSalt,
  deriveKey,
  arrayBufferToBase64,
  base64ToArrayBuffer,
  stringToBytes,
  bytesToString
} from '../utils';

/**
 * Encryption algorithm options
 */
export enum EncryptionAlgorithm {
  AES_GCM = 'AES-GCM',
  CHACHA20_POLY1305 = 'ChaCha20-Poly1305'
}

/**
 * Encrypted vault data structure
 */
export interface EncryptedVault {
  /**
   * Algorithm used for encryption
   */
  algorithm: EncryptionAlgorithm;
  
  /**
   * Salt used for key derivation (base64)
   */
  salt: string;
  
  /**
   * Number of PBKDF2 iterations
   */
  iterations: number;
  
  /**
   * Encrypted data (base64)
   */
  data: string;
  
  /**
   * Version for migration support
   */
  version: number;
}

/**
 * Options for password encryption
 */
export interface PasswordEncryptionOptions {
  /**
   * Encryption algorithm to use
   */
  algorithm?: EncryptionAlgorithm;
  
  /**
   * Number of PBKDF2 iterations (default: 100000)
   */
  iterations?: number;
  
  /**
   * Salt length in bytes (default: 32)
   */
  saltLength?: number;
}

/**
 * Password-based encryption for sensitive data
 * Provides secure encryption using PBKDF2 key derivation
 */
export class PasswordEncryption {
  private static readonly DEFAULT_ITERATIONS = 100000;
  private static readonly DEFAULT_SALT_LENGTH = 32;
  private static readonly CURRENT_VERSION = 1;

  /**
   * Encrypt data with a password
   * @param password Password for encryption
   * @param data Data to encrypt
   * @param options Encryption options
   * @returns Encrypted vault data
   */
  static async encrypt(
    password: string,
    data: any,
    options: PasswordEncryptionOptions = {}
  ): Promise<EncryptedVault> {
    const {
      algorithm = EncryptionAlgorithm.AES_GCM,
      iterations = this.DEFAULT_ITERATIONS,
      saltLength = this.DEFAULT_SALT_LENGTH
    } = options;

    try {
      // Generate salt
      const salt = generateSalt(saltLength);
      
      // Derive key from password
      const key = await deriveKey(password, salt, iterations);
      
      // Create cipher based on algorithm
      const cipher = this.createCipher(algorithm, key);
      
      // Serialize data to JSON
      const jsonData = JSON.stringify(data);
      const dataBytes = stringToBytes(jsonData);
      
      // Encrypt data
      const encryptedData = await cipher.encrypt(dataBytes);
      
      // Create vault structure
      return {
        algorithm,
        salt: arrayBufferToBase64(salt.buffer),
        iterations,
        data: arrayBufferToBase64(encryptedData.buffer),
        version: this.CURRENT_VERSION
      };
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.EncryptionFailed,
        `Password encryption failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Decrypt data with a password
   * @param password Password for decryption
   * @param vault Encrypted vault data
   * @returns Decrypted data
   */
  static async decrypt<T = any>(
    password: string,
    vault: EncryptedVault
  ): Promise<T> {
    try {
      // Validate vault version
      if (vault.version !== this.CURRENT_VERSION) {
        throw new WalletError(
          WalletErrorCode.InvalidKeyStoreJSON,
          `Unsupported vault version: ${vault.version}`
        );
      }
      
      // Decode salt
      const salt = new Uint8Array(base64ToArrayBuffer(vault.salt));
      
      // Derive key from password
      const key = await deriveKey(password, salt, vault.iterations);
      
      // Create cipher based on algorithm
      const cipher = this.createCipher(vault.algorithm, key);
      
      // Decode and decrypt data
      const encryptedData = new Uint8Array(base64ToArrayBuffer(vault.data));
      const decryptedData = await cipher.decrypt(encryptedData);
      
      // Parse JSON data
      const jsonData = bytesToString(decryptedData);
      return JSON.parse(jsonData) as T;
    } catch (error) {
      if (error instanceof WalletError) {
        throw error;
      }
      throw new WalletError(
        WalletErrorCode.DecryptionFailed,
        `Password decryption failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Re-encrypt data with a new password
   * @param oldPassword Current password
   * @param newPassword New password
   * @param vault Current encrypted vault
   * @param options New encryption options
   * @returns Re-encrypted vault data
   */
  static async reEncrypt(
    oldPassword: string,
    newPassword: string,
    vault: EncryptedVault,
    options?: PasswordEncryptionOptions
  ): Promise<EncryptedVault> {
    // Decrypt with old password
    const data = await this.decrypt(oldPassword, vault);
    
    // Re-encrypt with new password
    return this.encrypt(newPassword, data, options);
  }

  /**
   * Verify password against encrypted vault
   * @param password Password to verify
   * @param vault Encrypted vault
   * @returns True if password is correct
   */
  static async verifyPassword(
    password: string,
    vault: EncryptedVault
  ): Promise<boolean> {
    try {
      await this.decrypt(password, vault);
      return true;
    } catch (error) {
      if (error instanceof WalletError && 
          (error.code === WalletErrorCode.DecryptionFailed || 
           error.code === WalletErrorCode.InvalidTag)) {
        return false;
      }
      throw error;
    }
  }

  /**
   * Create cipher instance based on algorithm
   */
  private static createCipher(
    algorithm: EncryptionAlgorithm,
    key: Uint8Array
  ): SymmetricEncryption {
    switch (algorithm) {
      case EncryptionAlgorithm.AES_GCM:
        return AESGCMCipher.fromKey(key);
      
      case EncryptionAlgorithm.CHACHA20_POLY1305:
        return ChaChaPolyCipher.fromKey(key);
      
      default:
        throw new WalletError(
          WalletErrorCode.UnsupportedSignatureAlgorithm,
          `Unsupported encryption algorithm: ${algorithm}`
        );
    }
  }

  /**
   * Simple encrypt/decrypt methods for quick usage
   */
  
  /**
   * Quick encrypt with default settings
   * @param password Password
   * @param data Data to encrypt
   * @returns Base64 encrypted string
   */
  static async simpleEncrypt(password: string, data: any): Promise<string> {
    const vault = await this.encrypt(password, data);
    return JSON.stringify(vault);
  }

  /**
   * Quick decrypt from base64 string
   * @param password Password
   * @param encryptedString Base64 encrypted string
   * @returns Decrypted data
   */
  static async simpleDecrypt<T = any>(
    password: string, 
    encryptedString: string
  ): Promise<T> {
    const vault = JSON.parse(encryptedString) as EncryptedVault;
    return this.decrypt<T>(password, vault);
  }
}