/**
 * Implementation of hashing functionality
 * Provides a unified interface for hashing operations throughout the project
 */

import { sha256 as nobleSha256 } from '@noble/hashes/sha256';
import { sha3_256 as nobleSha3_256 } from '@noble/hashes/sha3';
import { keccak_256 as nobleKeccak256 } from '@noble/hashes/sha3';
import { hmac } from '@noble/hashes/hmac';
import { WalletError, WalletErrorCode } from '../utils';

/**
 * Supported hashing algorithms
 */
export enum HashingAlgorithm {
  SHA2_256 = 'SHA2_256',
  SHA3_256 = 'SHA3_256',
  KECCAK_256 = 'KECCAK_256'
}

/**
 * Hasher utility class providing various hashing functions
 */
export class Hasher {
  /**
   * Hash data with the requested algorithm
   * @param data Data to hash
   * @param algorithm Hashing algorithm to use
   * @returns Hash digest
   * @throws WalletError if algorithm is not supported
   */
  static hash(data: Uint8Array, algorithm: HashingAlgorithm): Uint8Array {
    try {
      switch (algorithm) {
        case HashingAlgorithm.SHA2_256:
          return nobleSha256(data);
        
        case HashingAlgorithm.SHA3_256:
          return nobleSha3_256(data);
          
        case HashingAlgorithm.KECCAK_256:
          return nobleKeccak256(data);
          
        default:
          throw new WalletError(
            WalletErrorCode.UnsupportedHashAlgorithm,
            `Unsupported hash algorithm: ${algorithm}`
          );
      }
    } catch (error) {
      if (error instanceof WalletError) {
        throw error;
      }
      throw new WalletError(
        WalletErrorCode.SignError,
        `Hashing failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Convenience wrapper for SHA-256
   * @param data Data to hash
   * @returns SHA-256 hash
   */
  static sha256(data: Uint8Array): Uint8Array {
    return this.hash(data, HashingAlgorithm.SHA2_256);
  }

  /**
   * Convenience wrapper for SHA3-256
   * @param data Data to hash
   * @returns SHA3-256 hash
   */
  static sha3_256(data: Uint8Array): Uint8Array {
    return this.hash(data, HashingAlgorithm.SHA3_256);
  }

  /**
   * Convenience wrapper for Keccak-256 (Ethereum-compatible)
   * @param data Data to hash
   * @returns Keccak-256 hash
   */
  static keccak256(data: Uint8Array): Uint8Array {
    return this.hash(data, HashingAlgorithm.KECCAK_256);
  }

  /**
   * Hash a string using the specified algorithm
   * @param str String to hash
   * @param algorithm Hashing algorithm to use
   * @returns Hash digest
   */
  static hashString(str: string, algorithm: HashingAlgorithm): Uint8Array {
    const encoder = new TextEncoder();
    return this.hash(encoder.encode(str), algorithm);
  }

  /**
   * Double SHA-256 hash (commonly used in Bitcoin)
   * @param data Data to hash
   * @returns Double SHA-256 hash
   */
  static doubleSha256(data: Uint8Array): Uint8Array {
    return this.sha256(this.sha256(data));
  }

  /**
   * HMAC-SHA256
   * @param key Key for HMAC
   * @param data Data to authenticate
   * @returns HMAC digest
   */
  static hmacSha256(key: Uint8Array, data: Uint8Array): Uint8Array {
    try {
      return hmac(nobleSha256, key, data);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.SignError,
        `HMAC-SHA256 failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
}