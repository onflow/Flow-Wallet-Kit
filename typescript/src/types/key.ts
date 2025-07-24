/**
 * Flow Wallet Kit - Key Management Types
 * 
 * This module defines the core types and interfaces for cryptographic key management.
 * It provides a unified interface for different types of keys (Secure Element, Seed Phrase, Private Key, etc.)
 * and their associated operations.
 */

import type { StorageProtocol } from './storage.js';

/**
 * Types of cryptographic keys supported
 */
export enum KeyType {
  /**
   * Key stored in device's secure element (e.g., Web Crypto API non-extractable keys)
   */
  SECURE_ELEMENT = 'SECURE_ELEMENT',
  
  /**
   * Key derived from a BIP39 seed phrase
   */
  SEED_PHRASE = 'SEED_PHRASE',
  
  /**
   * Raw private key
   */
  PRIVATE_KEY = 'PRIVATE_KEY',
  
  /**
   * Key stored in encrypted JSON keystore format
   */
  KEY_STORE = 'KEY_STORE'
}

/**
 * Supported signature algorithms (matching Flow blockchain)
 */
export enum SigningAlgorithm {
  ECDSA_P256 = 'ECDSA_P256',
  ECDSA_SECP256K1 = 'ECDSA_SECP256K1'
}

/**
 * Supported hash algorithms (matching Flow blockchain)
 */
export enum HashingAlgorithm {
  SHA2_256 = 'SHA2_256',
  SHA3_256 = 'SHA3_256'
}

/**
 * Base interface for key creation options
 */
export interface KeyCreationOptions {
  /**
   * Optional entropy for key generation
   */
  entropy?: Uint8Array;
}

/**
 * Options for creating a seed phrase key
 */
export interface SeedPhraseKeyOptions extends KeyCreationOptions {
  /**
   * Number of words in the mnemonic (12, 15, 18, 21, or 24)
   */
  wordCount?: 12 | 15 | 18 | 21 | 24;
  
  /**
   * BIP39 passphrase (optional)
   */
  passphrase?: string;
  
  /**
   * Derivation path (default: "m/44'/539'/0'/0/0")
   */
  derivationPath?: string;
}

/**
 * Options for creating a private key
 */
export interface PrivateKeyOptions extends KeyCreationOptions {
  /**
   * The signature algorithm to use
   */
  signAlgo?: SigningAlgorithm;
}

/**
 * Protocol defining the interface for cryptographic key management
 * Provides a unified interface for different types of keys
 * 
 * @typeParam TKey - The concrete key type
 * @typeParam TSecret - The type used for secret key material
 * @typeParam TAdvance - The type used for advanced key creation options
 */
export interface KeyProtocol<
  TKey = unknown,
  TSecret = Uint8Array,
  TAdvance = KeyCreationOptions
> {
  /**
   * The concrete key instance
   */
  readonly key: TKey;
  
  /**
   * The secret key material (may be encrypted or hardware-protected)
   */
  readonly secret: TSecret;
  
  /**
   * Advanced options used for key creation
   */
  readonly advance: TAdvance;
  
  /**
   * The type of key implementation
   */
  readonly keyType: KeyType;
  
  /**
   * Storage implementation for persisting key data
   */
  storage: StorageProtocol;
  
  /**
   * Check if the key is hardware-backed
   */
  readonly isHardwareBacked: boolean;
  
  /**
   * Get the key's unique identifier
   */
  readonly id: string;
  
  // Key Creation and Recovery
  
  /**
   * Create a new key with advanced options
   * @param advance - Advanced creation options
   * @param storage - Storage implementation
   * @returns New key instance
   * @throws {Error} if creation fails
   */
  create(advance: TAdvance, storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Create a new key with default options
   * @param storage - Storage implementation
   * @returns New key instance
   * @throws {Error} if creation fails
   */
  create(storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Create and store a new key
   * @param id - Unique identifier for the key
   * @param password - Password for encrypting the key
   * @param storage - Storage implementation
   * @returns New key instance
   * @throws {Error} if creation or storage fails
   */
  createAndStore(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Restore a key from secret material
   * @param secret - Secret key material
   * @param storage - Storage implementation
   * @returns Restored key instance
   * @throws {Error} if restoration fails
   */
  restore(secret: TSecret, storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  // Key Operations
  
  /**
   * Get the public key for a signature algorithm
   * @param signAlgo - Signature algorithm
   * @returns Public key data, or null if not available
   */
  publicKey(signAlgo: SigningAlgorithm): Uint8Array | null;
  
  /**
   * Get the private key for a signature algorithm
   * @param signAlgo - Signature algorithm
   * @returns Private key data, or null if not available or hardware-backed
   */
  privateKey(signAlgo: SigningAlgorithm): Uint8Array | null;
  
  /**
   * Sign data using specified algorithms
   * @param data - Data to sign
   * @param signAlgo - Signature algorithm
   * @param hashAlgo - Hash algorithm
   * @returns Signature data
   * @throws {Error} if signing fails
   */
  sign(
    data: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): Promise<Uint8Array>;
  
  /**
   * Verify a signature
   * @param signature - Signature to verify
   * @param message - Original message that was signed
   * @param signAlgo - Signature algorithm used
   * @param hashAlgo - Hash algorithm used
   * @returns Whether the signature is valid
   */
  isValidSignature(
    signature: Uint8Array,
    message: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): boolean;
  
  /**
   * Store the key
   * @param id - Unique identifier for the key
   * @param password - Password for encrypting the key
   * @throws {Error} if storage fails
   */
  store(id: string, password: string): Promise<void>;
  
  /**
   * Remove a stored key
   * @param id - Unique identifier of the key to remove
   * @throws {Error} if removal fails
   */
  remove(id: string): Promise<void>;
  
  /**
   * Get all stored key identifiers
   * @returns Array of key identifiers
   */
  allKeys(): string[];
}

/**
 * Static methods for key operations
 * These are typically implemented as static methods on key classes
 */
export interface KeyStatic<
  TKey = unknown,
  TSecret = Uint8Array,
  TAdvance = KeyCreationOptions
> {
  /**
   * Create a new key with advanced options
   */
  create(advance: TAdvance, storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Create a new key with default options
   */
  create(storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Create and store a new key
   */
  createAndStore(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Retrieve a stored key
   */
  get(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Restore a key from secret material
   */
  restore(secret: TSecret, storage: StorageProtocol): Promise<KeyProtocol<TKey, TSecret, TAdvance>>;
}