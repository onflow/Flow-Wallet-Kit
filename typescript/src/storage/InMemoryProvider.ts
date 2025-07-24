/**
 * Flow Wallet Kit - In-Memory Storage Provider
 * 
 * This module provides an in-memory storage implementation for testing
 * and temporary storage scenarios. Data is lost when the application closes.
 */

import { SecurityLevel } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { BaseStorageProtocol } from './StorageProtocol.js';

/**
 * In-memory storage provider
 * Stores data in memory without persistence
 * Suitable for testing and temporary storage needs
 */
export class InMemoryProvider extends BaseStorageProtocol {
  protected readonly storage: Map<string, Uint8Array>;
  public readonly securityLevel = SecurityLevel.IN_MEMORY;
  
  /**
   * Create a new in-memory storage provider
   */
  constructor() {
    super();
    this.storage = new Map<string, Uint8Array>();
  }
  
  /**
   * Get data from storage
   * @param key - The key to retrieve data for
   * @returns The stored data, or null if not found
   */
  public async get(key: string): Promise<Uint8Array | null> {
    try {
      this.validateKey(key);
      const data = this.storage.get(key);
      return data ? new Uint8Array(data) : null;
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to get data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Store data in storage
   * @param key - The key to store data under
   * @param data - The data to store
   */
  public async set(key: string, data: Uint8Array): Promise<void> {
    try {
      this.validateKey(key);
      this.validateData(data);
      
      // Clone the data to prevent external modifications
      const clonedData = new Uint8Array(data);
      this.storage.set(key, clonedData);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to set data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Remove data from storage
   * @param key - The key to remove data for
   */
  public async remove(key: string): Promise<void> {
    try {
      this.validateKey(key);
      this.storage.delete(key);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.RemoveCacheFailed,
        `Failed to remove data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Remove all data from storage
   */
  public async removeAll(): Promise<void> {
    try {
      this.storage.clear();
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.RemoveCacheFailed,
        `Failed to remove all data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Get the current size of the storage
   * @returns Number of items in storage
   */
  public get size(): number {
    return this.storage.size;
  }
  
  /**
   * Check if a key exists in storage
   * @param key - Key to check
   * @returns True if key exists
   */
  public async has(key: string): Promise<boolean> {
    try {
      this.validateKey(key);
      return this.storage.has(key);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to check key existence: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
}