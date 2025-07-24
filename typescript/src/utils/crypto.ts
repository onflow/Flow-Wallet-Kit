/**
 * Crypto utility functions for secure random generation and environment detection
 */

import { WalletError, WalletErrorCode } from './errors';

/**
 * Check if we're in a Node.js environment
 */
export const isNode = typeof globalThis !== 'undefined' && 
  globalThis.process?.versions?.node !== undefined;

/**
 * Check if Web Crypto API is available
 */
export const isWebCryptoAvailable = (): boolean => {
  if (typeof globalThis !== 'undefined' && globalThis.crypto?.subtle) {
    return true;
  }
  if (isNode) {
    try {
      const crypto = require('crypto');
      return !!crypto.webcrypto?.subtle;
    } catch {
      return false;
    }
  }
  return false;
};

/**
 * Get the crypto implementation for the current environment
 */
export const getCrypto = (): Crypto => {
  if (typeof globalThis !== 'undefined' && globalThis.crypto) {
    return globalThis.crypto;
  }
  if (isNode) {
    try {
      const { webcrypto } = require('crypto');
      if (webcrypto) {
        return webcrypto as Crypto;
      }
    } catch (e) {
      throw new WalletError(
        WalletErrorCode.CryptoNotAvailable,
        'Web Crypto API not available in Node.js environment',
        e
      );
    }
  }
  throw new WalletError(
    WalletErrorCode.CryptoNotAvailable,
    'Crypto API not available in this environment'
  );
};

/**
 * Generate cryptographically secure random bytes
 * @param length Number of bytes to generate
 * @returns Random bytes
 */
export function getRandomBytes(length: number): Uint8Array {
  if (length <= 0) {
    throw new WalletError(
      WalletErrorCode.InvalidNonce,
      'Length must be greater than 0'
    );
  }

  const crypto = getCrypto();
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

/**
 * Generate a random salt for key derivation
 * @param length Salt length in bytes (default: 32)
 * @returns Random salt
 */
export function generateSalt(length: number = 32): Uint8Array {
  return getRandomBytes(length);
}

/**
 * Generate a random nonce/IV
 * @param length Nonce length in bytes
 * @returns Random nonce
 */
export function generateNonce(length: number): Uint8Array {
  return getRandomBytes(length);
}

/**
 * Convert ArrayBuffer to base64 string
 */
export function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  if (isNode) {
    return Buffer.from(bytes).toString('base64');
  }
  
  // Browser environment
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]!);
  }
  return btoa(binary);
}

/**
 * Convert base64 string to ArrayBuffer
 */
export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  if (isNode) {
    const buffer = Buffer.from(base64, 'base64');
    return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
  }
  
  // Browser environment
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Convert hex string to Uint8Array
 */
export function hexToBytes(hex: string): Uint8Array {
  if (hex.length % 2 !== 0) {
    throw new WalletError(
      WalletErrorCode.InvalidPrivateKey,
      'Invalid hex string length'
    );
  }
  
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
  }
  return bytes;
}

/**
 * Convert Uint8Array to hex string
 */
export function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Concatenate multiple Uint8Arrays
 */
export function concatBytes(...arrays: Uint8Array[]): Uint8Array {
  const totalLength = arrays.reduce((acc, arr) => acc + arr.length, 0);
  const result = new Uint8Array(totalLength);
  let offset = 0;
  
  for (const arr of arrays) {
    result.set(arr, offset);
    offset += arr.length;
  }
  
  return result;
}

/**
 * Compare two byte arrays for equality in constant time
 */
export function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a[i]! ^ b[i]!;
  }
  
  return result === 0;
}

/**
 * Derive a key from a password using PBKDF2
 * @param password Password to derive key from
 * @param salt Salt for key derivation
 * @param iterations Number of iterations (default: 100000)
 * @param keyLength Desired key length in bytes (default: 32)
 * @returns Derived key
 */
export async function deriveKey(
  password: string,
  salt: Uint8Array,
  iterations: number = 100000,
  keyLength: number = 32
): Promise<Uint8Array> {
  const crypto = getCrypto();
  
  const encoder = new TextEncoder();
  const passwordBuffer = encoder.encode(password);
  
  // Import password as a key
  const passwordKey = await crypto.subtle.importKey(
    'raw',
    passwordBuffer,
    'PBKDF2',
    false,
    ['deriveBits']
  );
  
  // Derive bits
  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt,
      iterations,
      hash: 'SHA-256'
    },
    passwordKey,
    keyLength * 8
  );
  
  return new Uint8Array(derivedBits);
}

/**
 * Simple string to bytes conversion
 */
export function stringToBytes(str: string): Uint8Array {
  return new TextEncoder().encode(str);
}

/**
 * Simple bytes to string conversion
 */
export function bytesToString(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}