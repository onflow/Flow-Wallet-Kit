/**
 * Implementation of AES-GCM authenticated encryption
 * Provides industry-standard encryption with strong security guarantees
 */

import { SymmetricEncryption } from './SymmetricEncryption';
import { Hasher } from './Hasher';
import { 
  WalletError, 
  WalletErrorCode, 
  getCrypto, 
  generateNonce,
  concatBytes 
} from '../utils';

/**
 * AES-GCM cipher implementation using Web Crypto API
 */
export class AESGCMCipher implements SymmetricEncryption {
  private static readonly KEY_SIZE = 32; // 256 bits
  private static readonly NONCE_SIZE = 12; // 96 bits
  private static readonly TAG_SIZE = 128; // 128 bits
  private static readonly ALGORITHM = 'AES-GCM';

  private readonly _key: Uint8Array;
  private cryptoKey: CryptoKey | null = null;

  /**
   * Create a new AES-GCM cipher with the given password
   * @param password Password to derive key from
   */
  constructor(password: string) {
    // Derive key from password using SHA-256 (matching Android/iOS implementation)
    this._key = Hasher.sha256(new TextEncoder().encode(password));
  }

  /**
   * Get the symmetric key
   */
  get key(): Uint8Array {
    return this._key;
  }

  /**
   * Get the key size in bits
   */
  get keySize(): number {
    return AESGCMCipher.KEY_SIZE * 8;
  }

  /**
   * Get or create the CryptoKey for Web Crypto operations
   */
  private async getCryptoKey(): Promise<CryptoKey> {
    if (!this.cryptoKey) {
      const crypto = getCrypto();
      this.cryptoKey = await crypto.subtle.importKey(
        'raw',
        this._key,
        { name: AESGCMCipher.ALGORITHM },
        false,
        ['encrypt', 'decrypt']
      );
    }
    return this.cryptoKey;
  }

  /**
   * Encrypt data using AES-GCM
   * @param data Data to encrypt
   * @returns Encrypted data with nonce prepended
   */
  async encrypt(data: Uint8Array): Promise<Uint8Array> {
    try {
      const crypto = getCrypto();
      const cryptoKey = await this.getCryptoKey();
      const nonce = generateNonce(AESGCMCipher.NONCE_SIZE);

      const encrypted = await crypto.subtle.encrypt(
        {
          name: AESGCMCipher.ALGORITHM,
          iv: nonce,
          tagLength: AESGCMCipher.TAG_SIZE
        },
        cryptoKey,
        data
      );

      // Combine nonce and encrypted data (ciphertext + tag)
      return concatBytes(nonce, new Uint8Array(encrypted));
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.EncryptionFailed,
        `Encryption failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Decrypt and authenticate data using AES-GCM
   * @param combinedData Encrypted data with nonce prepended
   * @returns Original decrypted data
   */
  async decrypt(combinedData: Uint8Array): Promise<Uint8Array> {
    try {
      if (combinedData.length < AESGCMCipher.NONCE_SIZE) {
        throw new WalletError(
          WalletErrorCode.InvalidNonce,
          'Combined data too short to contain nonce'
        );
      }

      const crypto = getCrypto();
      const cryptoKey = await this.getCryptoKey();

      // Extract nonce and encrypted data
      const nonce = combinedData.slice(0, AESGCMCipher.NONCE_SIZE);
      const encrypted = combinedData.slice(AESGCMCipher.NONCE_SIZE);

      const decrypted = await crypto.subtle.decrypt(
        {
          name: AESGCMCipher.ALGORITHM,
          iv: nonce,
          tagLength: AESGCMCipher.TAG_SIZE
        },
        cryptoKey,
        encrypted
      );

      return new Uint8Array(decrypted);
    } catch (error) {
      if (error instanceof WalletError) {
        throw error;
      }
      throw new WalletError(
        WalletErrorCode.DecryptionFailed,
        `Decryption failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Create an AES-GCM cipher from a raw key
   * @param key Raw key bytes (must be 32 bytes)
   * @returns AES-GCM cipher instance
   */
  static fromKey(key: Uint8Array): AESGCMCipher {
    if (key.length !== this.KEY_SIZE) {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        `Invalid key size: expected ${this.KEY_SIZE} bytes, got ${key.length}`
      );
    }
    
    const cipher = new AESGCMCipher('');
    // Override the key directly using Object.defineProperty to bypass readonly
    Object.defineProperty(cipher, '_key', {
      value: key,
      writable: false,
      enumerable: false,
      configurable: false
    });
    return cipher;
  }
}