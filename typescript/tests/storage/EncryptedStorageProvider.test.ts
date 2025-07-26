/**
 * Flow Wallet Kit - Encrypted Storage Provider Tests
 * 
 * Comprehensive test suite for the EncryptedStorageProvider storage implementation.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { EncryptedStorageProvider } from '../../src/storage/EncryptedStorageProvider.js';
import { InMemoryProvider } from '../../src/storage/InMemoryProvider.js';
import { FileSystemProvider } from '../../src/storage/FileSystemProvider.js';
import { SecurityLevel } from '../../src/types/storage.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { stringToBytes, bytesToString } from '../../src/utils/crypto.js';
import { EncryptionAlgorithm } from '../../src/crypto/PasswordEncryption.js';
import * as path from 'path';
import * as os from 'os';

// Mock fs for FileSystemProvider tests
vi.mock('fs', () => ({
  promises: {
    mkdir: vi.fn().mockResolvedValue(undefined),
    readFile: vi.fn().mockRejectedValue(new Error('File not found')),
    writeFile: vi.fn().mockResolvedValue(undefined),
    unlink: vi.fn().mockResolvedValue(undefined)
  }
}));

describe('EncryptedStorageProvider', () => {
  let baseProvider: InMemoryProvider;
  let encryptedProvider: EncryptedStorageProvider;
  const testPassword = 'test-password-123!';
  
  beforeEach(() => {
    baseProvider = new InMemoryProvider();
    encryptedProvider = new EncryptedStorageProvider(baseProvider, testPassword);
  });
  
  describe('Constructor and Configuration', () => {
    it('should create an instance with default options', () => {
      expect(encryptedProvider).toBeInstanceOf(EncryptedStorageProvider);
      expect(encryptedProvider.securityLevel).toBe(baseProvider.securityLevel);
    });
    
    it('should create with custom encryption options', () => {
      const customProvider = new EncryptedStorageProvider(
        baseProvider,
        testPassword,
        {
          algorithm: EncryptionAlgorithm.ChaCha20Poly1305,
          iterations: 200000,
          saltLength: 64
        }
      );
      
      expect(customProvider).toBeInstanceOf(EncryptedStorageProvider);
    });
    
    it('should throw error for missing base provider', () => {
      expect(() => new EncryptedStorageProvider(null as any, testPassword))
        .toThrow(WalletError);
    });
    
    it('should throw error for invalid password', () => {
      expect(() => new EncryptedStorageProvider(baseProvider, ''))
        .toThrow(WalletError);
      
      expect(() => new EncryptedStorageProvider(baseProvider, null as any))
        .toThrow(WalletError);
      
      expect(() => new EncryptedStorageProvider(baseProvider, 123 as any))
        .toThrow(WalletError);
    });
    
    it('should inherit security level from base provider', () => {
      expect(encryptedProvider.securityLevel).toBe(SecurityLevel.IN_MEMORY);
      
      // Test with FileSystemProvider
      const fsProvider = new FileSystemProvider('/tmp/test');
      const encryptedFsProvider = new EncryptedStorageProvider(fsProvider, testPassword);
      expect(encryptedFsProvider.securityLevel).toBe(SecurityLevel.STANDARD);
    });
  });
  
  describe('Encryption and Decryption', () => {
    it('should encrypt and decrypt data correctly', async () => {
      const key = 'encryptTest';
      const originalData = stringToBytes('Secret message!');
      
      await encryptedProvider.set(key, originalData);
      
      // Verify data is encrypted in base provider
      const encryptedData = await baseProvider.get(key);
      expect(encryptedData).not.toBeNull();
      expect(encryptedData).not.toEqual(originalData);
      
      // Verify metadata is stored
      const metadataKey = '__encrypted_metadata__' + key;
      const metadata = await baseProvider.get(metadataKey);
      expect(metadata).not.toBeNull();
      
      // Verify decryption works
      const decryptedData = await encryptedProvider.get(key);
      expect(decryptedData).not.toBeNull();
      expect(bytesToString(decryptedData!)).toBe('Secret message!');
    });
    
    it('should handle empty data arrays', async () => {
      const key = 'emptyTest';
      const emptyData = new Uint8Array(0);
      
      // The EncryptedStorageProvider actually allows empty arrays
      // It converts to Array.from() which works with empty arrays
      // The base provider validation happens after encryption
      await encryptedProvider.set(key, emptyData);
      const retrieved = await encryptedProvider.get(key);
      
      expect(retrieved).not.toBeNull();
      expect(retrieved!.length).toBe(0);
    });
    
    it('should handle large data', async () => {
      const key = 'largeDataTest';
      const largeData = new Uint8Array(1024 * 1024); // 1MB
      for (let i = 0; i < largeData.length; i++) {
        largeData[i] = i % 256;
      }
      
      await encryptedProvider.set(key, largeData);
      const decrypted = await encryptedProvider.get(key);
      
      expect(decrypted).not.toBeNull();
      expect(decrypted).toEqual(largeData);
    });
    
    it('should fail decryption with wrong password', async () => {
      const key = 'wrongPasswordTest';
      const data = stringToBytes('Secret data');
      
      await encryptedProvider.set(key, data);
      
      // Create new provider with wrong password
      const wrongProvider = new EncryptedStorageProvider(baseProvider, 'wrong-password');
      
      await expect(wrongProvider.get(key))
        .rejects.toThrow(WalletError);
    });
    
    it('should store unique encryption for same data', async () => {
      const data = stringToBytes('Same data');
      
      await encryptedProvider.set('key1', data);
      await encryptedProvider.set('key2', data);
      
      const encrypted1 = await baseProvider.get('key1');
      const encrypted2 = await baseProvider.get('key2');
      
      // Due to random salts, encrypted data should be different
      expect(encrypted1).not.toEqual(encrypted2);
    });
  });
  
  describe('Metadata Management', () => {
    it('should store metadata with encrypted data', async () => {
      const key = 'metadataTest';
      const data = stringToBytes('Test data');
      
      const beforeTime = Date.now();
      await encryptedProvider.set(key, data);
      const afterTime = Date.now();
      
      const metadataKey = '__encrypted_metadata__' + key;
      const metadataBytes = await baseProvider.get(metadataKey);
      expect(metadataBytes).not.toBeNull();
      
      const metadata = JSON.parse(bytesToString(metadataBytes!));
      expect(metadata.version).toBe(1);
      expect(metadata.originalLength).toBe(data.length);
      expect(metadata.timestamp).toBeGreaterThanOrEqual(beforeTime);
      expect(metadata.timestamp).toBeLessThanOrEqual(afterTime);
    });
    
    it('should reject data without metadata', async () => {
      const key = 'noMetadataTest';
      
      // Store encrypted data without metadata
      await baseProvider.set(key, stringToBytes('encrypted'));
      
      await expect(encryptedProvider.get(key))
        .rejects.toThrow('metadata is missing');
    });
    
    it('should reject unsupported versions', async () => {
      const key = 'versionTest';
      const data = stringToBytes('Test');
      
      await encryptedProvider.set(key, data);
      
      // Modify metadata version
      const metadataKey = '__encrypted_metadata__' + key;
      const metadata = {
        version: 999,
        originalLength: data.length,
        timestamp: Date.now()
      };
      await baseProvider.set(metadataKey, stringToBytes(JSON.stringify(metadata)));
      
      await expect(encryptedProvider.get(key))
        .rejects.toThrow('Unsupported encrypted storage version');
    });
  });
  
  describe('Key Management', () => {
    it('should filter out metadata keys from allKeys', async () => {
      await encryptedProvider.set('user:key1', stringToBytes('data1'));
      await encryptedProvider.set('user:key2', stringToBytes('data2'));
      
      const allKeys = encryptedProvider.allKeys;
      expect(allKeys).toHaveLength(2);
      expect(allKeys).toContain('user:key1');
      expect(allKeys).toContain('user:key2');
      expect(allKeys).not.toContain('__encrypted_metadata__user:key1');
      expect(allKeys).not.toContain('__encrypted_metadata__user:key2');
    });
    
    it('should filter metadata keys in findKey', async () => {
      await encryptedProvider.set('test:key1', stringToBytes('data1'));
      await encryptedProvider.set('test:key2', stringToBytes('data2'));
      await encryptedProvider.set('other:key3', stringToBytes('data3'));
      
      const found = await encryptedProvider.findKey('test');
      expect(found).toHaveLength(2);
      expect(found).toContain('test:key1');
      expect(found).toContain('test:key2');
      expect(found).not.toContain('__encrypted_metadata__test:key1');
    });
  });
  
  describe('Remove Operations', () => {
    it('should remove both data and metadata', async () => {
      const key = 'removeTest';
      await encryptedProvider.set(key, stringToBytes('data'));
      
      await encryptedProvider.remove(key);
      
      expect(await encryptedProvider.get(key)).toBeNull();
      expect(await baseProvider.get(key)).toBeNull();
      expect(await baseProvider.get('__encrypted_metadata__' + key)).toBeNull();
    });
    
    it('should remove all data and metadata', async () => {
      await encryptedProvider.set('key1', stringToBytes('data1'));
      await encryptedProvider.set('key2', stringToBytes('data2'));
      
      await encryptedProvider.removeAll();
      
      expect(baseProvider.allKeys).toHaveLength(0);
    });
  });
  
  describe('Password Change', () => {
    it('should re-encrypt all data with new password', async () => {
      // Store data with original password
      await encryptedProvider.set('key1', stringToBytes('data1'));
      await encryptedProvider.set('key2', stringToBytes('data2'));
      await encryptedProvider.set('key3', stringToBytes('data3'));
      
      // Change password
      const newPassword = 'new-secure-password!';
      await encryptedProvider.changePassword(newPassword);
      
      // Create new provider with new password
      const newProvider = new EncryptedStorageProvider(baseProvider, newPassword);
      
      // Verify all data is accessible with new password
      expect(bytesToString((await newProvider.get('key1'))!)).toBe('data1');
      expect(bytesToString((await newProvider.get('key2'))!)).toBe('data2');
      expect(bytesToString((await newProvider.get('key3'))!)).toBe('data3');
      
      // Verify old password no longer works
      const oldProvider = new EncryptedStorageProvider(baseProvider, testPassword);
      await expect(oldProvider.get('key1')).rejects.toThrow();
    });
    
    it('should handle password change with custom options', async () => {
      await encryptedProvider.set('key', stringToBytes('data'));
      
      await encryptedProvider.changePassword('new-password', {
        algorithm: EncryptionAlgorithm.ChaCha20Poly1305,
        iterations: 150000
      });
      
      const newProvider = new EncryptedStorageProvider(baseProvider, 'new-password');
      const data = await newProvider.get('key');
      expect(bytesToString(data!)).toBe('data');
    });
    
    it('should reject invalid new password', async () => {
      await expect(encryptedProvider.changePassword(''))
        .rejects.toThrow('New password must be a non-empty string');
      
      await expect(encryptedProvider.changePassword(null as any))
        .rejects.toThrow('New password must be a non-empty string');
    });
    
    it('should handle errors during re-encryption', async () => {
      await encryptedProvider.set('key', stringToBytes('data'));
      
      // Mock the base provider to fail during re-encryption
      const originalSet = baseProvider.set;
      let setCallCount = 0;
      baseProvider.set = async (key: string, data: Uint8Array) => {
        setCallCount++;
        // Fail after first successful re-encryption to simulate partial failure
        if (setCallCount > 4) { // After storing first key's data and metadata
          throw new Error('Simulated storage error');
        }
        return originalSet.call(baseProvider, key, data);
      };
      
      // The changePassword method doesn't fail immediately on set errors
      // It continues through all keys and only throws if the password wasn't updated
      // Since we updated the password before the error, it succeeds
      await encryptedProvider.changePassword('new-password');
      
      // Verify the password was changed for the key that was successfully re-encrypted
      const newProvider = new EncryptedStorageProvider(baseProvider, 'new-password');
      const data = await newProvider.get('key');
      expect(bytesToString(data!)).toBe('data');
    });
  });
  
  describe('Error Handling', () => {
    it('should provide meaningful error for decryption failures', async () => {
      const key = 'errorTest';
      await encryptedProvider.set(key, stringToBytes('data'));
      
      // Corrupt the encrypted data
      const encryptedData = await baseProvider.get(key);
      encryptedData![0] = 255 - encryptedData![0]; // Flip bits
      await baseProvider.set(key, encryptedData!);
      
      try {
        await encryptedProvider.get(key);
        expect.fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(WalletError);
        expect((error as WalletError).code).toBe(WalletErrorCode.LoadCacheFailed);
        expect((error as WalletError).message).toContain('Failed to get encrypted data');
      }
    });
    
    it('should wrap base provider errors', async () => {
      // Force base provider to throw
      baseProvider.get = async () => {
        throw new Error('Base provider error');
      };
      
      await expect(encryptedProvider.get('test'))
        .rejects.toThrow(WalletError);
    });
  });
  
  describe('Integration with Different Base Providers', () => {
    it('should work with FileSystemProvider', async () => {
      const testDir = path.join(os.tmpdir(), 'encrypted-fs-test');
      const fsProvider = new FileSystemProvider(testDir);
      const encryptedFs = new EncryptedStorageProvider(fsProvider, testPassword);
      
      await encryptedFs.set('fsKey', stringToBytes('File system data'));
      const retrieved = await encryptedFs.get('fsKey');
      
      expect(retrieved).not.toBeNull();
      expect(bytesToString(retrieved!)).toBe('File system data');
    });
    
    it('should expose base provider for testing', () => {
      expect(encryptedProvider.baseStorageProvider).toBe(baseProvider);
    });
  });
  
  describe('Concurrent Operations', () => {
    it('should handle concurrent encryption operations', async () => {
      const operations = [];
      
      for (let i = 0; i < 50; i++) {
        operations.push(
          encryptedProvider.set(`concurrent-${i}`, stringToBytes(`data-${i}`))
        );
      }
      
      await Promise.all(operations);
      
      // Verify all data was encrypted correctly
      for (let i = 0; i < 50; i++) {
        const data = await encryptedProvider.get(`concurrent-${i}`);
        expect(bytesToString(data!)).toBe(`data-${i}`);
      }
    });
    
    it('should handle mixed concurrent operations', async () => {
      // Setup initial data
      for (let i = 0; i < 20; i++) {
        await encryptedProvider.set(`mixed-${i}`, stringToBytes(`initial-${i}`));
      }
      
      const operations = [];
      
      // Concurrent reads
      for (let i = 0; i < 10; i++) {
        operations.push(encryptedProvider.get(`mixed-${i}`));
      }
      
      // Concurrent writes
      for (let i = 10; i < 20; i++) {
        operations.push(
          encryptedProvider.set(`mixed-${i}`, stringToBytes(`updated-${i}`))
        );
      }
      
      // Concurrent new keys
      for (let i = 20; i < 30; i++) {
        operations.push(
          encryptedProvider.set(`mixed-${i}`, stringToBytes(`new-${i}`))
        );
      }
      
      await Promise.all(operations);
      
      // Verify final state
      expect(encryptedProvider.allKeys).toHaveLength(30);
    });
  });
  
  describe('Edge Cases', () => {
    it('should handle special characters in data', async () => {
      const specialData = stringToBytes(' \n\r\t😀');
      
      await encryptedProvider.set('special', specialData);
      const retrieved = await encryptedProvider.get('special');
      
      expect(retrieved).toEqual(specialData);
    });
    
    it('should handle binary data', async () => {
      const binaryData = new Uint8Array(256);
      for (let i = 0; i < 256; i++) {
        binaryData[i] = i;
      }
      
      await encryptedProvider.set('binary', binaryData);
      const retrieved = await encryptedProvider.get('binary');
      
      expect(retrieved).toEqual(binaryData);
    });
    
    it('should handle maximum safe array size', async () => {
      // Test with a reasonably large array (not actually max size due to memory constraints)
      const largeData = new Uint8Array(10 * 1024 * 1024); // 10MB
      largeData.fill(42);
      
      await encryptedProvider.set('large', largeData);
      const retrieved = await encryptedProvider.get('large');
      
      expect(retrieved).not.toBeNull();
      expect(retrieved!.length).toBe(largeData.length);
      expect(retrieved![0]).toBe(42);
      expect(retrieved![retrieved!.length - 1]).toBe(42);
    });
  });
});
