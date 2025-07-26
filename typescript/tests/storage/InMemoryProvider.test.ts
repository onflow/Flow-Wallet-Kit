/**
 * Flow Wallet Kit - In-Memory Storage Provider Tests
 * 
 * Comprehensive test suite for the InMemoryProvider storage implementation.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { InMemoryProvider } from '../../src/storage/InMemoryProvider.js';
import { SecurityLevel } from '../../src/types/storage.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { stringToBytes, bytesToString } from '../../src/utils/crypto.js';

describe('InMemoryProvider', () => {
  let provider: InMemoryProvider;
  
  beforeEach(() => {
    provider = new InMemoryProvider();
  });
  
  describe('Constructor and Security Level', () => {
    it('should create an instance with correct security level', () => {
      expect(provider).toBeInstanceOf(InMemoryProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.IN_MEMORY);
    });
    
    it('should start with empty storage', () => {
      expect(provider.size).toBe(0);
      expect(provider.allKeys).toHaveLength(0);
    });
  });
  
  describe('Set and Get Operations', () => {
    it('should store and retrieve data correctly', async () => {
      const key = 'testKey';
      const data = stringToBytes('Hello, World!');
      
      await provider.set(key, data);
      const retrieved = await provider.get(key);
      
      expect(retrieved).not.toBeNull();
      expect(bytesToString(retrieved!)).toBe('Hello, World!');
    });
    
    it('should return null for non-existent keys', async () => {
      const result = await provider.get('nonExistent');
      expect(result).toBeNull();
    });
    
    it('should overwrite existing data', async () => {
      const key = 'overwriteTest';
      const data1 = stringToBytes('First value');
      const data2 = stringToBytes('Second value');
      
      await provider.set(key, data1);
      await provider.set(key, data2);
      
      const retrieved = await provider.get(key);
      expect(bytesToString(retrieved!)).toBe('Second value');
    });
    
    it('should clone data to prevent external modifications', async () => {
      const key = 'cloneTest';
      const originalData = new Uint8Array([1, 2, 3, 4, 5]);
      
      await provider.set(key, originalData);
      originalData[0] = 99; // Modify original
      
      const retrieved = await provider.get(key);
      expect(retrieved![0]).toBe(1); // Should not be affected
    });
    
    it('should handle empty values', async () => {
      const key = 'emptyTest';
      const emptyData = new Uint8Array(0);
      
      await expect(provider.set(key, emptyData))
        .rejects.toThrow(WalletError);
    });
    
    it('should handle special characters in keys', async () => {
      const specialKeys = [
        'key-with-dashes',
        'key_with_underscores',
        'key.with.dots',
        'key:with:colons',
        'key/with/slashes',
        'key@with@at',
        'key#with#hash',
        'key$with$dollar',
        'key%with%percent',
        'key with spaces',
        '中文密钥',
        '🔑emoji-key'
      ];
      
      const data = stringToBytes('test data');
      
      for (const key of specialKeys) {
        await provider.set(key, data);
        const retrieved = await provider.get(key);
        expect(retrieved).not.toBeNull();
        expect(bytesToString(retrieved!)).toBe('test data');
      }
    });
  });
  
  describe('Key Validation', () => {
    it('should reject empty keys', async () => {
      const data = stringToBytes('test');
      
      await expect(provider.set('', data))
        .rejects.toThrow(WalletError);
      await expect(provider.get(''))
        .rejects.toThrow(WalletError);
      await expect(provider.remove(''))
        .rejects.toThrow(WalletError);
    });
    
    it('should reject null/undefined keys', async () => {
      const data = stringToBytes('test');
      
      await expect(provider.set(null as any, data))
        .rejects.toThrow(WalletError);
      await expect(provider.get(undefined as any))
        .rejects.toThrow(WalletError);
    });
    
    it('should reject keys exceeding 255 characters', async () => {
      const longKey = 'x'.repeat(256);
      const data = stringToBytes('test');
      
      await expect(provider.set(longKey, data))
        .rejects.toThrow(WalletError);
    });
  });
  
  describe('Data Validation', () => {
    it('should reject non-Uint8Array data', async () => {
      const key = 'testKey';
      
      await expect(provider.set(key, 'string' as any))
        .rejects.toThrow(WalletError);
      await expect(provider.set(key, [1, 2, 3] as any))
        .rejects.toThrow(WalletError);
      await expect(provider.set(key, {} as any))
        .rejects.toThrow(WalletError);
    });
  });
  
  describe('Remove Operations', () => {
    it('should remove data correctly', async () => {
      const key = 'removeTest';
      const data = stringToBytes('data to remove');
      
      await provider.set(key, data);
      expect(await provider.has(key)).toBe(true);
      
      await provider.remove(key);
      expect(await provider.has(key)).toBe(false);
      expect(await provider.get(key)).toBeNull();
    });
    
    it('should handle removing non-existent keys', async () => {
      // Should not throw
      await expect(provider.remove('nonExistent'))
        .resolves.toBeUndefined();
    });
    
    it('should remove all data correctly', async () => {
      const keys = ['key1', 'key2', 'key3'];
      const data = stringToBytes('test data');
      
      for (const key of keys) {
        await provider.set(key, data);
      }
      
      expect(provider.size).toBe(3);
      
      await provider.removeAll();
      
      expect(provider.size).toBe(0);
      for (const key of keys) {
        expect(await provider.get(key)).toBeNull();
      }
    });
  });
  
  describe('Key Management', () => {
    it('should list all keys correctly', async () => {
      const keys = ['alpha', 'beta', 'gamma'];
      const data = stringToBytes('test');
      
      for (const key of keys) {
        await provider.set(key, data);
      }
      
      const allKeys = provider.allKeys;
      expect(allKeys).toHaveLength(3);
      expect(allKeys).toContain('alpha');
      expect(allKeys).toContain('beta');
      expect(allKeys).toContain('gamma');
    });
    
    it('should find keys by keyword', async () => {
      const keys = [
        'user:alice:wallet',
        'user:bob:wallet',
        'user:alice:settings',
        'system:config',
        'cache:data'
      ];
      const data = stringToBytes('test');
      
      for (const key of keys) {
        await provider.set(key, data);
      }
      
      const aliceKeys = await provider.findKey('alice');
      expect(aliceKeys).toHaveLength(2);
      expect(aliceKeys).toContain('user:alice:wallet');
      expect(aliceKeys).toContain('user:alice:settings');
      
      const walletKeys = await provider.findKey('wallet');
      expect(walletKeys).toHaveLength(2);
      expect(walletKeys).toContain('user:alice:wallet');
      expect(walletKeys).toContain('user:bob:wallet');
      
      const userKeys = await provider.findKey('user');
      expect(userKeys).toHaveLength(3);
    });
    
    it('should handle case-insensitive search', async () => {
      const keys = ['TestKey', 'TESTKEY', 'testkey'];
      const data = stringToBytes('test');
      
      for (const key of keys) {
        await provider.set(key, data);
      }
      
      const found = await provider.findKey('test');
      expect(found).toHaveLength(3);
    });
    
    it('should return empty array for no matches', async () => {
      await provider.set('key', stringToBytes('data'));
      
      const result = await provider.findKey('nonexistent');
      expect(result).toHaveLength(0);
    });
    
    it('should return empty array for empty keyword', async () => {
      await provider.set('key', stringToBytes('data'));
      
      const result = await provider.findKey('');
      expect(result).toHaveLength(0);
    });
  });
  
  describe('Has Method', () => {
    it('should correctly check key existence', async () => {
      const key = 'existenceTest';
      const data = stringToBytes('exists');
      
      expect(await provider.has(key)).toBe(false);
      
      await provider.set(key, data);
      expect(await provider.has(key)).toBe(true);
      
      await provider.remove(key);
      expect(await provider.has(key)).toBe(false);
    });
  });
  
  describe('Concurrent Operations', () => {
    it('should handle concurrent set operations', async () => {
      const operations = [];
      
      for (let i = 0; i < 100; i++) {
        operations.push(
          provider.set(`concurrent-${i}`, stringToBytes(`data-${i}`))
        );
      }
      
      await Promise.all(operations);
      
      expect(provider.size).toBe(100);
      
      // Verify all data is correct
      for (let i = 0; i < 100; i++) {
        const data = await provider.get(`concurrent-${i}`);
        expect(bytesToString(data!)).toBe(`data-${i}`);
      }
    });
    
    it('should handle concurrent mixed operations', async () => {
      // Setup initial data
      const setupOps = [];
      for (let i = 0; i < 50; i++) {
        setupOps.push(
          provider.set(`mixed-${i}`, stringToBytes(`initial-${i}`))
        );
      }
      await Promise.all(setupOps);
      
      // Concurrent mixed operations
      const mixedOps = [];
      
      // Updates
      for (let i = 0; i < 25; i++) {
        mixedOps.push(
          provider.set(`mixed-${i}`, stringToBytes(`updated-${i}`))
        );
      }
      
      // Reads
      for (let i = 25; i < 50; i++) {
        mixedOps.push(provider.get(`mixed-${i}`));
      }
      
      // Removes
      for (let i = 40; i < 50; i++) {
        mixedOps.push(provider.remove(`mixed-${i}`));
      }
      
      // New additions
      for (let i = 50; i < 60; i++) {
        mixedOps.push(
          provider.set(`mixed-${i}`, stringToBytes(`new-${i}`))
        );
      }
      
      await Promise.all(mixedOps);
      
      // Verify final state
      expect(provider.size).toBe(50); // 0-39 exist, 40-49 removed, 50-59 added
      
      // Check updated values
      for (let i = 0; i < 25; i++) {
        const data = await provider.get(`mixed-${i}`);
        expect(bytesToString(data!)).toBe(`updated-${i}`);
      }
      
      // Check removed values
      for (let i = 40; i < 50; i++) {
        expect(await provider.get(`mixed-${i}`)).toBeNull();
      }
      
      // Check new values
      for (let i = 50; i < 60; i++) {
        const data = await provider.get(`mixed-${i}`);
        expect(bytesToString(data!)).toBe(`new-${i}`);
      }
    });
  });
  
  describe('Large Data Handling', () => {
    it('should handle large data values', async () => {
      const key = 'largeData';
      // Create 1MB of data
      const largeData = new Uint8Array(1024 * 1024);
      for (let i = 0; i < largeData.length; i++) {
        largeData[i] = i % 256;
      }
      
      await provider.set(key, largeData);
      const retrieved = await provider.get(key);
      
      expect(retrieved).not.toBeNull();
      expect(retrieved!.length).toBe(largeData.length);
      expect(retrieved).toEqual(largeData);
    });
    
    it('should handle many keys', async () => {
      const numKeys = 1000;
      const data = stringToBytes('test');
      
      for (let i = 0; i < numKeys; i++) {
        await provider.set(`key-${i}`, data);
      }
      
      expect(provider.size).toBe(numKeys);
      expect(provider.allKeys).toHaveLength(numKeys);
      
      // Verify random samples
      const samples = [0, 250, 500, 750, 999];
      for (const i of samples) {
        const retrieved = await provider.get(`key-${i}`);
        expect(retrieved).not.toBeNull();
      }
    });
  });
  
  describe('Error Handling', () => {
    it('should wrap errors with WalletError', async () => {
      try {
        await provider.get('');
        expect.fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(WalletError);
        expect((error as WalletError).code).toBe(WalletErrorCode.LoadCacheFailed);
      }
    });
  });
});
