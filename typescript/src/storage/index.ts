/**
 * Flow Wallet Kit - Storage Module Exports
 * 
 * This module exports all storage providers and related utilities.
 */

// Base storage protocol
export { BaseStorageProtocol } from './StorageProtocol.js';

// Storage providers
export { InMemoryProvider } from './InMemoryProvider.js';
export { FileSystemProvider } from './FileSystemProvider.js';
export { EncryptedStorageProvider } from './EncryptedStorageProvider.js';

// Re-export storage types
export type { StorageProtocol } from '../types/storage.js';
export { SecurityLevel } from '../types/storage.js';

/**
 * Factory function to create a storage provider based on environment
 * @param options - Configuration options
 * @returns A storage provider instance
 */
export function createStorageProvider(options?: {
  type?: 'memory' | 'filesystem' | 'encrypted';
  basePath?: string;
  password?: string;
  baseProvider?: import('../types/storage.js').StorageProtocol;
}): import('../types/storage.js').StorageProtocol {
  const { type = 'memory', basePath, password, baseProvider } = options || {};
  
  switch (type) {
    case 'memory':
      return new InMemoryProvider();
      
    case 'filesystem':
      if (!basePath) {
        throw new Error('basePath is required for filesystem storage');
      }
      return new FileSystemProvider(basePath);
      
    case 'encrypted':
      if (!password) {
        throw new Error('password is required for encrypted storage');
      }
      if (!baseProvider) {
        throw new Error('baseProvider is required for encrypted storage');
      }
      return new EncryptedStorageProvider(baseProvider, password);
      
    default:
      return new InMemoryProvider();
  }
}

/**
 * Helper to create an encrypted file system storage
 * @param basePath - Base directory for storage
 * @param password - Encryption password
 * @returns Encrypted file system storage provider
 */
export function createEncryptedFileSystemStorage(
  basePath: string,
  password: string
): EncryptedStorageProvider {
  const fileProvider = new FileSystemProvider(basePath);
  return new EncryptedStorageProvider(fileProvider, password);
}

/**
 * Helper to create an encrypted memory storage
 * @param password - Encryption password
 * @returns Encrypted memory storage provider
 */
export function createEncryptedMemoryStorage(
  password: string
): EncryptedStorageProvider {
  const memoryProvider = new InMemoryProvider();
  return new EncryptedStorageProvider(memoryProvider, password);
}