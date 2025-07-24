/**
 * Flow Wallet Kit - Storage Protocol Base Implementation
 * 
 * This module provides a base implementation of the StorageProtocol interface
 * that can be extended by concrete storage providers.
 */

import { StorageProtocol, SecurityLevel } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';

/**
 * Abstract base class for storage implementations
 * Provides common functionality and validation
 */
export abstract class BaseStorageProtocol implements StorageProtocol {
  /**
   * Internal storage map for keys
   */
  protected abstract readonly storage: Map<string, Uint8Array>;
  
  /**
   * Security level of the storage implementation
   */
  public abstract readonly securityLevel: SecurityLevel;
  
  /**
   * Get all keys currently stored
   */
  public get allKeys(): readonly string[] {
    return Array.from(this.storage.keys());
  }
  
  /**
   * Find keys matching a keyword
   * @param keyword - Search term to match against keys
   * @returns List of matching keys
   */
  public async findKey(keyword: string): Promise<readonly string[]> {
    try {
      if (!keyword) {
        return [];
      }
      
      const lowerKeyword = keyword.toLowerCase();
      const matchingKeys = this.allKeys.filter(key => 
        key.toLowerCase().includes(lowerKeyword)
      );
      
      return matchingKeys;
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to find keys: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Get data from storage
   * @param key - The key to retrieve data for
   * @returns The stored data, or null if not found
   */
  public abstract get(key: string): Promise<Uint8Array | null>;
  
  /**
   * Store data in storage
   * @param key - The key to store data under
   * @param data - The data to store
   */
  public abstract set(key: string, data: Uint8Array): Promise<void>;
  
  /**
   * Remove data from storage
   * @param key - The key to remove data for
   */
  public abstract remove(key: string): Promise<void>;
  
  /**
   * Remove all data from storage
   */
  public abstract removeAll(): Promise<void>;
  
  /**
   * Validate storage key
   * @param key - Key to validate
   * @throws {WalletError} if key is invalid
   */
  protected validateKey(key: string): void {
    if (!key || typeof key !== 'string') {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        'Storage key must be a non-empty string'
      );
    }
    
    if (key.length > 255) {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        'Storage key must not exceed 255 characters'
      );
    }
  }
  
  /**
   * Validate storage data
   * @param data - Data to validate
   * @throws {WalletError} if data is invalid
   */
  protected validateData(data: Uint8Array): void {
    if (!(data instanceof Uint8Array)) {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        'Storage data must be a Uint8Array'
      );
    }
    
    if (data.length === 0) {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        'Storage data must not be empty'
      );
    }
  }
}