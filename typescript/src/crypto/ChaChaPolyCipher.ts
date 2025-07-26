/**
 * Implementation of ChaCha20-Poly1305 authenticated encryption
 * Provides high-performance encryption with strong security guarantees
 */

import { chacha20poly1305 } from '@noble/ciphers/chacha';
import { SymmetricEncryption } from './SymmetricEncryption';
import { Hasher } from './Hasher';
import { 
  WalletError, 
  WalletErrorCode, 
  generateNonce,
  concatBytes 
} from '../utils';

/**
 * ChaCha20-Poly1305 cipher implementation using noble-ciphers
 */
export class ChaChaPolyCipher implements SymmetricEncryption {
  private static readonly KEY_SIZE = 32; // 256 bits
  private static readonly NONCE_SIZE = 12; // 96 bits
  private static readonly TAG_SIZE = 16; // 128 bits

  private readonly _key: Uint8Array;

  /**
   * Create a new ChaCha20-Poly1305 cipher with the given password
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
    return ChaChaPolyCipher.KEY_SIZE * 8;
  }

  /**
   * Encrypt data using ChaCha20-Poly1305
   * @param data Data to encrypt
   * @returns Encrypted data with nonce prepended
   */
  async encrypt(data: Uint8Array): Promise<Uint8Array> {
    try {
      const nonce = generateNonce(ChaChaPolyCipher.NONCE_SIZE);
      const cipher = chacha20poly1305(this._key, nonce);
      
      // Encrypt data (includes authentication tag)
      const encrypted = cipher.encrypt(data);
      
      // Combine nonce and encrypted data (ciphertext + tag)
      return concatBytes(nonce, encrypted);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.EncryptionFailed,
        `Encryption failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Decrypt and authenticate data using ChaCha20-Poly1305
   * @param combinedData Encrypted data with nonce prepended
   * @returns Original decrypted data
   */
  async decrypt(combinedData: Uint8Array): Promise<Uint8Array> {
    try {
      if (combinedData.length < ChaChaPolyCipher.NONCE_SIZE + ChaChaPolyCipher.TAG_SIZE) {
        throw new WalletError(
          WalletErrorCode.InvalidNonce,
          'Combined data too short to contain nonce and tag'
        );
      }

      // Extract nonce and encrypted data
      const nonce = combinedData.slice(0, ChaChaPolyCipher.NONCE_SIZE);
      const encrypted = combinedData.slice(ChaChaPolyCipher.NONCE_SIZE);

      const cipher = chacha20poly1305(this._key, nonce);
      
      // Decrypt and verify authentication tag
      const decrypted = cipher.decrypt(encrypted);
      
      return decrypted;
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
   * Create a ChaCha20-Poly1305 cipher from a raw key
   * @param key Raw key bytes (must be 32 bytes)
   * @returns ChaCha20-Poly1305 cipher instance
   */
  static fromKey(key: Uint8Array): ChaChaPolyCipher {
    if (key.length !== this.KEY_SIZE) {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        `Invalid key size: expected ${this.KEY_SIZE} bytes, got ${key.length}`
      );
    }
    
    const cipher = Object.create(ChaChaPolyCipher.prototype);
    // Create a copy of the key to ensure independence
    cipher._key = new Uint8Array(key);
    return cipher;
  }
}