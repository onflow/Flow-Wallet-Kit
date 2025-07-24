/**
 * Flow Wallet Kit - Storage Types
 * 
 * This module defines the core types and interfaces for secure data storage.
 * It provides a unified interface for storing and retrieving sensitive data like keys,
 * allowing different storage backends to be used interchangeably.
 */

/**
 * Enum representing different security levels for storage backends
 */
export enum SecurityLevel {
  /**
   * Highest security level - uses hardware-backed encryption
   * (e.g., Android Keystore, Secure Enclave, Web Crypto API with non-extractable keys)
   */
  HARDWARE_BACKED = 'HARDWARE_BACKED',
  
  /**
   * Medium security level - uses standard secure storage
   * (e.g., encrypted IndexedDB, encrypted localStorage)
   */
  STANDARD = 'STANDARD',
  
  /**
   * Lowest security level - uses in-memory storage
   * Data is lost when app is closed
   */
  IN_MEMORY = 'IN_MEMORY'
}

/**
 * Protocol defining storage behavior for wallet data
 * 
 * @remarks
 * Implementations should handle encryption/decryption transparently
 * and throw appropriate errors for failed operations.
 */
export interface StorageProtocol {
  /**
   * Get all keys currently stored in the storage
   */
  readonly allKeys: readonly string[];
  
  /**
   * Get the security level of the storage implementation
   */
  readonly securityLevel: SecurityLevel;
  
  /**
   * Find keys matching a keyword
   * @param keyword - Search term to match against keys
   * @returns List of matching keys
   * @throws {Error} if operation fails
   */
  findKey(keyword: string): Promise<readonly string[]>;
  
  /**
   * Get data from storage
   * @param key - The key to retrieve data for
   * @returns The stored data, or null if not found
   * @throws {Error} if operation fails
   */
  get(key: string): Promise<Uint8Array | null>;
  
  /**
   * Store data in storage
   * @param key - The key to store data under
   * @param data - The data to store
   * @throws {Error} if operation fails
   */
  set(key: string, data: Uint8Array): Promise<void>;
  
  /**
   * Remove data from storage
   * @param key - The key to remove data for
   * @throws {Error} if operation fails
   */
  remove(key: string): Promise<void>;
  
  /**
   * Remove all data from storage
   * @throws {Error} if operation fails
   */
  removeAll(): Promise<void>;
}

/**
 * Interface for objects that can be cached in storage
 * @typeParam T - The type of data to cache
 */
export interface Cacheable<T> {
  /**
   * The data to be cached
   */
  readonly cachedData: T | null;
  
  /**
   * Unique identifier for the cache entry
   */
  readonly cacheId: string;
  
  /**
   * Storage implementation for caching
   */
  readonly storage: StorageProtocol;
  
  /**
   * Cache the current data
   * @throws {Error} if caching fails
   */
  cache(): Promise<void>;
  
  /**
   * Load data from cache
   * @returns The cached data, or null if not found
   * @throws {Error} if loading fails
   */
  loadCache(): Promise<T | null>;
}