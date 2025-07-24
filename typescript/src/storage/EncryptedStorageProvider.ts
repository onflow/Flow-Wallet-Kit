/**
 * Flow Wallet Kit - Encrypted Storage Provider
 * 
 * This module provides an encrypted wrapper for any storage provider.
 * It transparently encrypts data before storage and decrypts on retrieval.
 */

import { StorageProtocol, SecurityLevel } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { PasswordEncryption, EncryptionAlgorithm, PasswordEncryptionOptions } from '../crypto/PasswordEncryption.js';
import { stringToBytes, bytesToString } from '../utils/crypto.js';

/**
 * Metadata stored with encrypted data
 */
interface EncryptedMetadata {
  /**
   * Original data length before encryption
   */
  originalLength: number;
  
  /**
   * Timestamp of encryption
   */
  timestamp: number;
  
  /**
   * Version for future compatibility
   */
  version: number;
}

/**
 * Encrypted storage provider
 * Wraps any storage provider with transparent encryption/decryption
 */
export class EncryptedStorageProvider implements StorageProtocol {
  private static readonly METADATA_PREFIX = '__encrypted_metadata__';
  private static readonly CURRENT_VERSION = 1;
  
  private readonly baseProvider: StorageProtocol;
  private readonly password: string;
  private readonly encryptionOptions: PasswordEncryptionOptions;
  
  /**
   * Create a new encrypted storage provider
   * @param baseProvider - The underlying storage provider to wrap
   * @param password - Password for encryption/decryption
   * @param options - Optional encryption settings
   */
  constructor(
    baseProvider: StorageProtocol,
    password: string,
    options?: PasswordEncryptionOptions
  ) {
    if (!baseProvider) {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        'Base storage provider is required'
      );
    }
    
    if (!password || typeof password !== 'string') {
      throw new WalletError(
        WalletErrorCode.InvalidPassword,
        'Password must be a non-empty string'
      );
    }
    
    this.baseProvider = baseProvider;
    this.password = password;
    this.encryptionOptions = {
      algorithm: EncryptionAlgorithm.AES_GCM,
      iterations: 100000,
      saltLength: 32,
      ...options
    };
  }
  
  /**
   * Get the security level from the base provider
   */
  get securityLevel(): SecurityLevel {
    return this.baseProvider.securityLevel;
  }
  
  /**
   * Get all keys currently stored
   * Filters out metadata keys
   */
  get allKeys(): readonly string[] {
    return this.baseProvider.allKeys.filter(
      key => !key.startsWith(EncryptedStorageProvider.METADATA_PREFIX)
    );
  }
  
  /**
   * Find keys matching a keyword
   * @param keyword - Search term to match against keys
   * @returns List of matching keys
   */
  public async findKey(keyword: string): Promise<readonly string[]> {
    const allKeys = await this.baseProvider.findKey(keyword);
    return allKeys.filter(
      key => !key.startsWith(EncryptedStorageProvider.METADATA_PREFIX)
    );
  }
  
  /**
   * Get data from storage and decrypt it
   * @param key - The key to retrieve data for
   * @returns The decrypted data, or null if not found
   */
  public async get(key: string): Promise<Uint8Array | null> {
    try {
      // Get encrypted data
      const encryptedData = await this.baseProvider.get(key);
      if (!encryptedData) {
        return null;
      }
      
      // Get metadata
      const metadataKey = this.getMetadataKey(key);
      const metadataBytes = await this.baseProvider.get(metadataKey);
      if (!metadataBytes) {
        throw new WalletError(
          WalletErrorCode.LoadCacheFailed,
          'Encrypted data found but metadata is missing'
        );
      }
      
      // Parse metadata
      const metadataJson = bytesToString(metadataBytes);
      const metadata = JSON.parse(metadataJson) as EncryptedMetadata;
      
      // Validate version
      if (metadata.version !== EncryptedStorageProvider.CURRENT_VERSION) {
        throw new WalletError(
          WalletErrorCode.InvalidKeyStoreJSON,
          `Unsupported encrypted storage version: ${metadata.version}`
        );
      }
      
      // Decrypt data
      const encryptedString = bytesToString(encryptedData);
      const decryptedData = await PasswordEncryption.simpleDecrypt<number[]>(
        this.password,
        encryptedString
      );
      
      // Convert back to Uint8Array
      return new Uint8Array(decryptedData);
    } catch (error) {
      if (error instanceof WalletError && error.code === WalletErrorCode.DecryptionFailed) {
        throw new WalletError(
          WalletErrorCode.InvalidPassword,
          'Failed to decrypt data: Invalid password',
          error
        );
      }
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to get encrypted data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Encrypt and store data
   * @param key - The key to store data under
   * @param data - The data to encrypt and store
   */
  public async set(key: string, data: Uint8Array): Promise<void> {
    try {
      // Create metadata
      const metadata: EncryptedMetadata = {
        originalLength: data.length,
        timestamp: Date.now(),
        version: EncryptedStorageProvider.CURRENT_VERSION
      };
      
      // Convert Uint8Array to number array for JSON serialization
      const dataArray = Array.from(data);
      
      // Encrypt data
      const encryptedString = await PasswordEncryption.simpleEncrypt(
        this.password,
        dataArray
      );
      const encryptedData = stringToBytes(encryptedString);
      
      // Store encrypted data
      await this.baseProvider.set(key, encryptedData);
      
      // Store metadata
      const metadataKey = this.getMetadataKey(key);
      const metadataBytes = stringToBytes(JSON.stringify(metadata));
      await this.baseProvider.set(metadataKey, metadataBytes);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to set encrypted data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Remove data and its metadata from storage
   * @param key - The key to remove data for
   */
  public async remove(key: string): Promise<void> {
    try {
      // Remove data
      await this.baseProvider.remove(key);
      
      // Remove metadata
      const metadataKey = this.getMetadataKey(key);
      await this.baseProvider.remove(metadataKey);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.RemoveCacheFailed,
        `Failed to remove encrypted data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Remove all data from storage
   */
  public async removeAll(): Promise<void> {
    try {
      await this.baseProvider.removeAll();
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.RemoveCacheFailed,
        `Failed to remove all encrypted data: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Change the encryption password
   * Re-encrypts all stored data with the new password
   * @param newPassword - The new password to use
   * @param newOptions - Optional new encryption settings
   */
  public async changePassword(
    newPassword: string,
    newOptions?: PasswordEncryptionOptions
  ): Promise<void> {
    try {
      if (!newPassword || typeof newPassword !== 'string') {
        throw new WalletError(
          WalletErrorCode.InvalidPassword,
          'New password must be a non-empty string'
        );
      }
      
      // Get all keys
      const keys = this.allKeys;
      
      // Re-encrypt all data
      const reEncryptedData: Map<string, Uint8Array> = new Map();
      
      for (const key of keys) {
        const data = await this.get(key);
        if (data) {
          reEncryptedData.set(key, data);
        }
      }
      
      // Update password and options
      (this as any).password = newPassword;
      if (newOptions) {
        Object.assign(this.encryptionOptions, newOptions);
      }
      
      // Store all data with new encryption
      for (const [key, data] of reEncryptedData.entries()) {
        await this.set(key, data);
      }
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.LoadCacheFailed,
        `Failed to change password: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Get the metadata key for a given data key
   */
  private getMetadataKey(key: string): string {
    return `${EncryptedStorageProvider.METADATA_PREFIX}${key}`;
  }
  
  /**
   * Get the underlying base provider
   * Useful for testing or advanced operations
   */
  public get baseStorageProvider(): StorageProtocol {
    return this.baseProvider;
  }
}