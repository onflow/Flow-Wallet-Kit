/**
 * Flow Wallet Kit - File System Storage Provider
 * 
 * This module provides a file system-based storage implementation for Node.js
 * environments. Data is persisted to disk in a structured directory format.
 */

import { SecurityLevel } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { BaseStorageProtocol } from './StorageProtocol.js';
import { promises as fs } from 'fs';
import * as path from 'path';
import { createHash } from 'crypto';

/**
 * File system storage provider
 * Stores data as files on disk (Node.js only)
 * Each key is hashed and stored as a separate file
 */
export class FileSystemProvider extends BaseStorageProtocol {
  protected readonly storage: Map<string, Uint8Array>;
  public readonly securityLevel = SecurityLevel.STANDARD;
  
  private readonly basePath: string;
  private readonly metadataFile: string;
  private metadata: Map<string, string>; // key -> hash mapping
  
  /**
   * Create a new file system storage provider
   * @param basePath - Base directory for storage files
   */
  constructor(basePath: string) {
    super();
    
    // Check if we're in a Node.js environment
    if (typeof process === 'undefined' || !process.versions || !process.versions.node) {
      throw new WalletError(
        WalletErrorCode.NoImplement,
        'FileSystemProvider is only available in Node.js environments'
      );
    }
    
    this.storage = new Map<string, Uint8Array>();
    this.basePath = path.resolve(basePath);
    this.metadataFile = path.join(this.basePath, '.metadata.json');
    this.metadata = new Map<string, string>();
  }
  
  /**
   * Initialize the storage directory and load metadata
   */
  private async initialize(): Promise<void> {
    try {
      // Create base directory if it doesn't exist
      await fs.mkdir(this.basePath, { recursive: true });
      
      // Load metadata if it exists
      try {
        const metadataContent = await fs.readFile(this.metadataFile, 'utf-8');
        const metadataObj = JSON.parse(metadataContent);
        this.metadata = new Map(Object.entries(metadataObj));
      } catch (error) {
        // Metadata file doesn't exist, start with empty metadata
        this.metadata = new Map<string, string>();
      }
      
      // Sync storage map with metadata
      for (const [key, hash] of this.metadata.entries()) {
        const filePath = path.join(this.basePath, hash);
        try {
          const data = await fs.readFile(filePath);
          this.storage.set(key, new Uint8Array(data));
        } catch (error) {
          // File doesn't exist, remove from metadata
          this.metadata.delete(key);
        }
      }
      
      // Save cleaned metadata
      await this.saveMetadata();
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to initialize file system storage: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Save metadata to disk
   */
  private async saveMetadata(): Promise<void> {
    const metadataObj = Object.fromEntries(this.metadata.entries());
    await fs.writeFile(this.metadataFile, JSON.stringify(metadataObj, null, 2));
  }
  
  /**
   * Hash a key to create a safe filename
   */
  private hashKey(key: string): string {
    return createHash('sha256').update(key).digest('hex');
  }
  
  /**
   * Ensure storage is initialized
   */
  private async ensureInitialized(): Promise<void> {
    if (this.metadata.size === 0 && this.storage.size === 0) {
      await this.initialize();
    }
  }
  
  /**
   * Get data from storage
   * @param key - The key to retrieve data for
   * @returns The stored data, or null if not found
   */
  public async get(key: string): Promise<Uint8Array | null> {
    try {
      this.validateKey(key);
      await this.ensureInitialized();
      
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
      await this.ensureInitialized();
      
      // Clone the data
      const clonedData = new Uint8Array(data);
      
      // Generate hash for filename
      const hash = this.hashKey(key);
      const filePath = path.join(this.basePath, hash);
      
      // Write data to file
      await fs.writeFile(filePath, clonedData);
      
      // Update memory storage and metadata
      this.storage.set(key, clonedData);
      this.metadata.set(key, hash);
      
      // Save metadata
      await this.saveMetadata();
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
      await this.ensureInitialized();
      
      const hash = this.metadata.get(key);
      if (hash) {
        const filePath = path.join(this.basePath, hash);
        
        // Remove file
        try {
          await fs.unlink(filePath);
        } catch (error) {
          // File might not exist, continue anyway
        }
        
        // Update memory storage and metadata
        this.storage.delete(key);
        this.metadata.delete(key);
        
        // Save metadata
        await this.saveMetadata();
      }
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
      await this.ensureInitialized();
      
      // Remove all files
      for (const hash of this.metadata.values()) {
        const filePath = path.join(this.basePath, hash);
        try {
          await fs.unlink(filePath);
        } catch (error) {
          // File might not exist, continue anyway
        }
      }
      
      // Clear memory storage and metadata
      this.storage.clear();
      this.metadata.clear();
      
      // Save empty metadata
      await this.saveMetadata();
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.RemoveCacheFailed,
        `Failed to remove all data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Get the storage directory path
   */
  public get storagePath(): string {
    return this.basePath;
  }
}