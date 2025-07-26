/**
 * Flow Wallet Kit - Storage Protocol Tests
 * 
 * Comprehensive test suite for the BaseStorageProtocol implementation
 * and factory functions.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BaseStorageProtocol } from '../../src/storage/StorageProtocol.js';
import { 
  InMemoryProvider,
  FileSystemProvider,
  EncryptedStorageProvider,
  createStorageProvider,
  createEncryptedFileSystemStorage,
  createEncryptedMemoryStorage
} from '../../src/storage/index.js';
import { SecurityLevel } from '../../src/types/storage.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { stringToBytes } from '../../src/utils/crypto.js';
import * as path from 'path';
import * as os from 'os';

// Mock fs for FileSystemProvider
vi.mock('fs', () => ({
  promises: {
    mkdir: vi.fn().mockResolvedValue(undefined),
    readFile: vi.fn().mockRejectedValue(new Error('File not found')),
    writeFile: vi.fn().mockResolvedValue(undefined),
    unlink: vi.fn().mockResolvedValue(undefined)
  }
}));

// Test implementation of BaseStorageProtocol
class TestStorageProtocol extends BaseStorageProtocol {
  protected readonly storage: Map<string, Uint8Array>;
  public readonly securityLevel = SecurityLevel.STANDARD;
  
  constructor() {
    super();
    this.storage = new Map<string, Uint8Array>();
  }
  
  public async get(key: string): Promise<Uint8Array | null> {
    this.validateKey(key);
    return this.storage.get(key) || null;
  }
  
  public async set(key: string, data: Uint8Array): Promise<void> {
    this.validateKey(key);
    this.validateData(data);
    this.storage.set(key, data);
  }
  
  public async remove(key: string): Promise<void> {
    this.validateKey(key);
    this.storage.delete(key);
  }
  
  public async removeAll(): Promise<void> {
    this.storage.clear();
  }
}

describe('BaseStorageProtocol', () => {
  let storage: TestStorageProtocol;
  
  beforeEach(() => {
    storage = new TestStorageProtocol();
  });
  
  describe('Key Validation', () => {
    it('should validate keys correctly', async () => {
      const validKeys = [
        'simple',
        'with-dashes',
        'with_underscores',
        'with.dots',
        'x'.repeat(255) // Max length
      ];
      
      const data = stringToBytes('test');
      
      for (const key of validKeys) {
        await expect(storage.set(key, data)).resolves.toBeUndefined();
      }
    });
    
    it('should reject invalid keys', async () => {
      const data = stringToBytes('test');
      
      // Empty string
      await expect(storage.set('', data))
        .rejects.toThrow('Storage key must be a non-empty string');
      
      // Null/undefined
      await expect(storage.set(null as any, data))
        .rejects.toThrow('Storage key must be a non-empty string');
      
      await expect(storage.set(undefined as any, data))
        .rejects.toThrow('Storage key must be a non-empty string');
      
      // Non-string
      await expect(storage.set(123 as any, data))
        .rejects.toThrow('Storage key must be a non-empty string');
      
      // Too long
      await expect(storage.set('x'.repeat(256), data))
        .rejects.toThrow('Storage key must not exceed 255 characters');
    });
  });
  
  describe('Data Validation', () => {
    it('should validate data correctly', async () => {
      const validData = [
        new Uint8Array([1]),
        new Uint8Array([1, 2, 3]),
        new Uint8Array(1024) // Large array
      ];
      
      for (const data of validData) {
        await expect(storage.set('key', data)).resolves.toBeUndefined();
      }
    });
    
    it('should reject invalid data', async () => {
      // Not Uint8Array
      await expect(storage.set('key', 'string' as any))
        .rejects.toThrow('Storage data must be a Uint8Array');
      
      await expect(storage.set('key', [1, 2, 3] as any))
        .rejects.toThrow('Storage data must be a Uint8Array');
      
      // Note: Buffer is actually a Uint8Array in Node.js, so we need a different non-Uint8Array type
      await expect(storage.set('key', { length: 5 } as any))
        .rejects.toThrow('Storage data must be a Uint8Array');
      
      // Empty array
      await expect(storage.set('key', new Uint8Array(0)))
        .rejects.toThrow('Storage data must not be empty');
    });
  });
  
  describe('Key Management', () => {
    it('should return all keys', async () => {
      const keys = ['key1', 'key2', 'key3'];
      const data = stringToBytes('test');
      
      for (const key of keys) {
        await storage.set(key, data);
      }
      
      const allKeys = storage.allKeys;
      expect(allKeys).toHaveLength(3);
      expect(allKeys).toContain('key1');
      expect(allKeys).toContain('key2');
      expect(allKeys).toContain('key3');
    });
    
    it('should find keys by keyword', async () => {
      const keys = [
        'user:alice:data',
        'user:bob:data',
        'system:alice:config'
      ];
      const data = stringToBytes('test');
      
      for (const key of keys) {
        await storage.set(key, data);
      }
      
      const aliceKeys = await storage.findKey('alice');
      expect(aliceKeys).toHaveLength(2);
      expect(aliceKeys).toContain('user:alice:data');
      expect(aliceKeys).toContain('system:alice:config');
      
      const userKeys = await storage.findKey('user');
      expect(userKeys).toHaveLength(2);
      expect(userKeys).toContain('user:alice:data');
      expect(userKeys).toContain('user:bob:data');
    });
    
    it('should handle case-insensitive keyword search', async () => {
      await storage.set('TestKey', stringToBytes('data'));
      await storage.set('TESTKEY', stringToBytes('data'));
      await storage.set('testkey', stringToBytes('data'));
      
      const found = await storage.findKey('TEST');
      expect(found).toHaveLength(3);
    });
    
    it('should return empty array for empty keyword', async () => {
      await storage.set('key', stringToBytes('data'));
      
      const found = await storage.findKey('');
      expect(found).toHaveLength(0);
    });
    
    it('should handle errors in findKey', async () => {
      // Mock storage.keys to throw
      Object.defineProperty(storage, 'allKeys', {
        get() {
          throw new Error('Storage error');
        }
      });
      
      await expect(storage.findKey('test'))
        .rejects.toThrow(WalletError);
    });
  });
});

describe('Storage Factory Functions', () => {
  describe('createStorageProvider', () => {
    it('should create InMemoryProvider by default', () => {
      const provider = createStorageProvider();
      expect(provider).toBeInstanceOf(InMemoryProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.IN_MEMORY);
    });
    
    it('should create InMemoryProvider explicitly', () => {
      const provider = createStorageProvider({ type: 'memory' });
      expect(provider).toBeInstanceOf(InMemoryProvider);
    });
    
    it('should create FileSystemProvider', () => {
      const testPath = path.join(os.tmpdir(), 'test-storage');
      const provider = createStorageProvider({
        type: 'filesystem',
        basePath: testPath
      });
      
      expect(provider).toBeInstanceOf(FileSystemProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.STANDARD);
      expect((provider as FileSystemProvider).storagePath).toBe(path.resolve(testPath));
    });
    
    it('should throw error for filesystem without basePath', () => {
      expect(() => createStorageProvider({ type: 'filesystem' }))
        .toThrow('basePath is required for filesystem storage');
    });
    
    it('should create EncryptedStorageProvider', () => {
      const baseProvider = new InMemoryProvider();
      const provider = createStorageProvider({
        type: 'encrypted',
        password: 'test-password',
        baseProvider
      });
      
      expect(provider).toBeInstanceOf(EncryptedStorageProvider);
      expect(provider.securityLevel).toBe(baseProvider.securityLevel);
    });
    
    it('should throw error for encrypted without password', () => {
      const baseProvider = new InMemoryProvider();
      
      expect(() => createStorageProvider({
        type: 'encrypted',
        baseProvider
      })).toThrow('password is required for encrypted storage');
    });
    
    it('should throw error for encrypted without baseProvider', () => {
      expect(() => createStorageProvider({
        type: 'encrypted',
        password: 'test'
      })).toThrow('baseProvider is required for encrypted storage');
    });
    
    it('should handle unknown type by returning InMemoryProvider', () => {
      const provider = createStorageProvider({ type: 'unknown' as any });
      expect(provider).toBeInstanceOf(InMemoryProvider);
    });
  });
  
  describe('createEncryptedFileSystemStorage', () => {
    it('should create encrypted filesystem storage', async () => {
      const testPath = path.join(os.tmpdir(), 'encrypted-fs-test');
      const password = 'secure-password';
      
      const provider = createEncryptedFileSystemStorage(testPath, password);
      
      expect(provider).toBeInstanceOf(EncryptedStorageProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.STANDARD);
      
      // Test it works
      await provider.set('test', stringToBytes('encrypted data'));
      const retrieved = await provider.get('test');
      expect(retrieved).not.toBeNull();
    });
    
    it('should use resolved paths', () => {
      const relativePath = './storage';
      const password = 'test';
      
      const provider = createEncryptedFileSystemStorage(relativePath, password);
      const baseProvider = (provider as EncryptedStorageProvider).baseStorageProvider as FileSystemProvider;
      
      expect(path.isAbsolute(baseProvider.storagePath)).toBe(true);
    });
  });
  
  describe('createEncryptedMemoryStorage', () => {
    it('should create encrypted memory storage', async () => {
      const password = 'secure-password';
      
      const provider = createEncryptedMemoryStorage(password);
      
      expect(provider).toBeInstanceOf(EncryptedStorageProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.IN_MEMORY);
      
      // Test it works
      await provider.set('test', stringToBytes('encrypted in memory'));
      const retrieved = await provider.get('test');
      expect(retrieved).not.toBeNull();
    });
    
    it('should not persist data', async () => {
      const password = 'test';
      
      const provider1 = createEncryptedMemoryStorage(password);
      await provider1.set('key', stringToBytes('data'));
      
      const provider2 = createEncryptedMemoryStorage(password);
      const retrieved = await provider2.get('key');
      
      expect(retrieved).toBeNull(); // New instance has no data
    });
  });
});

describe('Storage Protocol Interface Compliance', () => {
  const providers = [
    { name: 'InMemoryProvider', create: () => new InMemoryProvider() },
    { 
      name: 'FileSystemProvider', 
      create: () => new FileSystemProvider(path.join(os.tmpdir(), 'test-' + Date.now()))
    },
    {
      name: 'EncryptedStorageProvider',
      create: () => new EncryptedStorageProvider(new InMemoryProvider(), 'password')
    }
  ];
  
  providers.forEach(({ name, create }) => {
    describe(name, () => {
      let provider: any;
      
      beforeEach(() => {
        provider = create();
      });
      
      it('should implement all required methods', () => {
        expect(typeof provider.get).toBe('function');
        expect(typeof provider.set).toBe('function');
        expect(typeof provider.remove).toBe('function');
        expect(typeof provider.removeAll).toBe('function');
        expect(typeof provider.findKey).toBe('function');
        expect(typeof provider.securityLevel).toBe('string');
        expect(provider.allKeys).toBeDefined();
      });
      
      it('should handle basic operations correctly', async () => {
        const key = 'test-key';
        const data = stringToBytes('test-data');
        
        // Set
        await provider.set(key, data);
        
        // Get
        const retrieved = await provider.get(key);
        expect(retrieved).toEqual(data);
        
        // Remove
        await provider.remove(key);
        expect(await provider.get(key)).toBeNull();
        
        // Set multiple
        await provider.set('key1', data);
        await provider.set('key2', data);
        
        // Remove all
        await provider.removeAll();
        expect(await provider.get('key1')).toBeNull();
        expect(await provider.get('key2')).toBeNull();
      });
      
      it('should return correct security level', () => {
        const validLevels = Object.values(SecurityLevel);
        expect(validLevels).toContain(provider.securityLevel);
      });
    });
  });
});
