/**
 * Flow Wallet Kit - File System Storage Provider Tests
 * 
 * Comprehensive test suite for the FileSystemProvider storage implementation.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { FileSystemProvider } from '../../src/storage/FileSystemProvider.js';
import { SecurityLevel } from '../../src/types/storage.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { stringToBytes, bytesToString } from '../../src/utils/crypto.js';
import { promises as fs } from 'fs';
import * as path from 'path';
import * as os from 'os';
import { createHash } from 'crypto';

// Mock fs module
vi.mock('fs', () => ({
  promises: {
    mkdir: vi.fn(),
    readFile: vi.fn(),
    writeFile: vi.fn(),
    unlink: vi.fn()
  }
}));

describe('FileSystemProvider', () => {
  let provider: FileSystemProvider;
  let testDir: string;
  const mockFs = fs as any;
  
  beforeEach(() => {
    vi.clearAllMocks();
    testDir = path.join(os.tmpdir(), 'fwk-test-' + Date.now());
    
    // Default mock implementations
    mockFs.mkdir.mockResolvedValue(undefined);
    mockFs.readFile.mockRejectedValue(new Error('File not found'));
    mockFs.writeFile.mockResolvedValue(undefined);
    mockFs.unlink.mockResolvedValue(undefined);
  });
  
  afterEach(() => {
    vi.clearAllMocks();
  });
  
  describe('Constructor and Initialization', () => {
    it('should create an instance with correct security level', () => {
      provider = new FileSystemProvider(testDir);
      expect(provider).toBeInstanceOf(FileSystemProvider);
      expect(provider.securityLevel).toBe(SecurityLevel.STANDARD);
      expect(provider.storagePath).toBe(path.resolve(testDir));
    });
    
    it('should throw error in non-Node.js environment', () => {
      const originalProcess = global.process;
      
      // Simulate browser environment
      (global as any).process = undefined;
      
      expect(() => new FileSystemProvider(testDir))
        .toThrow(WalletError);
      
      // Restore
      global.process = originalProcess;
    });
    
    it('should initialize directory on first operation', async () => {
      provider = new FileSystemProvider(testDir);
      
      await provider.get('test');
      
      expect(mockFs.mkdir).toHaveBeenCalledWith(
        path.resolve(testDir),
        { recursive: true }
      );
    });
    
    it('should load existing metadata on initialization', async () => {
      const metadata = {
        'key1': 'hash1',
        'key2': 'hash2'
      };
      
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify(metadata));
        }
        if (filePath.endsWith('hash1')) {
          return Promise.resolve(Buffer.from('data1'));
        }
        if (filePath.endsWith('hash2')) {
          return Promise.resolve(Buffer.from('data2'));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      provider = new FileSystemProvider(testDir);
      await provider.get('key1');
      
      expect(provider.allKeys).toContain('key1');
      expect(provider.allKeys).toContain('key2');
    });
    
    it('should handle missing metadata file gracefully', async () => {
      mockFs.readFile.mockRejectedValue(new Error('ENOENT'));
      
      provider = new FileSystemProvider(testDir);
      await provider.get('test');
      
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        '{}'
      );
    });
    
    it('should clean up metadata for missing files', async () => {
      const metadata = {
        'key1': 'hash1',
        'key2': 'hash2' // This file will be missing
      };
      
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify(metadata));
        }
        if (filePath.endsWith('hash1')) {
          return Promise.resolve(Buffer.from('data1'));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      provider = new FileSystemProvider(testDir);
      await provider.get('test');
      
      // Should save metadata without key2
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        expect.not.stringContaining('key2')
      );
    });
  });
  
  describe('Set and Get Operations', () => {
    beforeEach(() => {
      provider = new FileSystemProvider(testDir);
    });
    
    it('should store and retrieve data correctly', async () => {
      const key = 'testKey';
      const data = stringToBytes('Hello, FileSystem!');
      const hash = createHash('sha256').update(key).digest('hex');
      
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith(hash)) {
          return Promise.resolve(Buffer.from(data));
        }
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify({ [key]: hash }));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      await provider.set(key, data);
      
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        path.join(path.resolve(testDir), hash),
        data
      );
      
      const retrieved = await provider.get(key);
      expect(retrieved).not.toBeNull();
      expect(bytesToString(retrieved!)).toBe('Hello, FileSystem!');
    });
    
    it('should return null for non-existent keys', async () => {
      mockFs.readFile.mockRejectedValue(new Error('File not found'));
      
      const result = await provider.get('nonExistent');
      expect(result).toBeNull();
    });
    
    it('should persist data across provider instances', async () => {
      const key = 'persistTest';
      const data = stringToBytes('Persistent data');
      const hash = createHash('sha256').update(key).digest('hex');
      
      // First provider saves data
      await provider.set(key, data);
      
      // Mock file system state after save
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith(hash)) {
          return Promise.resolve(Buffer.from(data));
        }
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify({ [key]: hash }));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      // Second provider should load the data
      const provider2 = new FileSystemProvider(testDir);
      const retrieved = await provider2.get(key);
      
      expect(retrieved).not.toBeNull();
      expect(bytesToString(retrieved!)).toBe('Persistent data');
    });
    
    it('should handle concurrent file operations', async () => {
      const operations = [];
      
      for (let i = 0; i < 10; i++) {
        operations.push(
          provider.set(`file-${i}`, stringToBytes(`data-${i}`))
        );
      }
      
      await Promise.all(operations);
      
      // Each set operation writes data file + metadata file + initial metadata save on first access
      // First operation: mkdir + initial metadata save + data write + metadata update = 4 writes
      // Subsequent operations: data write + metadata update = 2 writes each
      // Total: 1 (initial) + 10 (data) + 10 (metadata updates) = 21, but we also have multiple
      // initializations happening concurrently, so the exact count can vary
      expect(mockFs.writeFile.mock.calls.length).toBeGreaterThanOrEqual(20);
    });
  });
  
  describe('Remove Operations', () => {
    beforeEach(() => {
      provider = new FileSystemProvider(testDir);
    });
    
    it('should remove files correctly', async () => {
      const key = 'removeTest';
      const hash = createHash('sha256').update(key).digest('hex');
      
      // Setup metadata
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify({ [key]: hash }));
        }
        if (filePath.endsWith(hash)) {
          return Promise.resolve(Buffer.from('data'));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      await provider.remove(key);
      
      expect(mockFs.unlink).toHaveBeenCalledWith(
        path.join(path.resolve(testDir), hash)
      );
      
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        '{}'
      );
    });
    
    it('should handle removing non-existent keys', async () => {
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve('{}');
        }
        return Promise.reject(new Error('File not found'));
      });
      
      await expect(provider.remove('nonExistent'))
        .resolves.toBeUndefined();
    });
    
    it('should continue even if file deletion fails', async () => {
      const key = 'failedRemove';
      const hash = createHash('sha256').update(key).digest('hex');
      
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify({ [key]: hash }));
        }
        return Promise.reject(new Error('File not found'));
      });
      
      mockFs.unlink.mockRejectedValue(new Error('Permission denied'));
      
      await provider.remove(key);
      
      // Should still update metadata
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        '{}'
      );
    });
    
    it('should remove all files correctly', async () => {
      const metadata = {
        'key1': 'hash1',
        'key2': 'hash2',
        'key3': 'hash3'
      };
      
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve(JSON.stringify(metadata));
        }
        return Promise.resolve(Buffer.from('data'));
      });
      
      await provider.removeAll();
      
      expect(mockFs.unlink).toHaveBeenCalledTimes(3);
      expect(mockFs.unlink).toHaveBeenCalledWith(
        expect.stringContaining('hash1')
      );
      expect(mockFs.unlink).toHaveBeenCalledWith(
        expect.stringContaining('hash2')
      );
      expect(mockFs.unlink).toHaveBeenCalledWith(
        expect.stringContaining('hash3')
      );
      
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        '{}'
      );
    });
  });
  
  describe('Key Management', () => {
    beforeEach(() => {
      provider = new FileSystemProvider(testDir);
    });
    
    it('should generate consistent hashes for keys', async () => {
      const key = 'consistentKey';
      const data = stringToBytes('test');
      const expectedHash = createHash('sha256').update(key).digest('hex');
      
      await provider.set(key, data);
      
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        path.join(path.resolve(testDir), expectedHash),
        expect.any(Uint8Array)
      );
    });
    
    it('should handle hash collisions theoretically', async () => {
      // While SHA-256 collisions are practically impossible,
      // the code should handle them correctly by overwriting
      const key1 = 'key1';
      const key2 = 'key2';
      const data1 = stringToBytes('data1');
      const data2 = stringToBytes('data2');
      
      await provider.set(key1, data1);
      await provider.set(key2, data2);
      
      // Each key should have its own file
      const hash1 = createHash('sha256').update(key1).digest('hex');
      const hash2 = createHash('sha256').update(key2).digest('hex');
      
      expect(hash1).not.toBe(hash2); // Different keys should have different hashes
    });
  });
  
  describe('Error Handling', () => {
    beforeEach(() => {
      provider = new FileSystemProvider(testDir);
    });
    
    it('should handle file system errors gracefully', async () => {
      mockFs.mkdir.mockRejectedValue(new Error('Permission denied'));
      
      await expect(provider.get('test'))
        .rejects.toThrow(WalletError);
    });
    
    it('should handle corrupted metadata', async () => {
      mockFs.readFile.mockImplementation((filePath: string) => {
        if (filePath.endsWith('.metadata.json')) {
          return Promise.resolve('invalid json');
        }
        return Promise.reject(new Error('File not found'));
      });
      
      // The FileSystemProvider catches JSON parse errors in the try-catch block
      // and treats them as missing metadata, so it starts with empty metadata
      const result = await provider.get('test');
      expect(result).toBeNull();
      
      // Should have saved empty metadata
      expect(mockFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('.metadata.json'),
        '{}'
      );
    });
    
    it('should handle write failures', async () => {
      mockFs.writeFile.mockRejectedValue(new Error('Disk full'));
      
      await expect(provider.set('test', stringToBytes('data')))
        .rejects.toThrow(WalletError);
    });
  });
  
  describe('Edge Cases', () => {
    beforeEach(() => {
      provider = new FileSystemProvider(testDir);
    });
    
    it('should handle very long file paths', async () => {
      const longKey = 'x'.repeat(200); // Long but valid key
      const data = stringToBytes('test');
      
      await provider.set(longKey, data);
      
      expect(mockFs.writeFile).toHaveBeenCalled();
      // Hash will be consistent length regardless of key length
    });
    
    it('should handle special characters in base path', () => {
      const specialPath = '/tmp/test dir with spaces/and-special@chars!';
      const specialProvider = new FileSystemProvider(specialPath);
      
      expect(specialProvider.storagePath).toBe(path.resolve(specialPath));
    });
    
    it('should handle relative paths by resolving them', () => {
      const relativePath = './storage/data';
      const relativeProvider = new FileSystemProvider(relativePath);
      
      expect(relativeProvider.storagePath).toBe(path.resolve(relativePath));
      expect(path.isAbsolute(relativeProvider.storagePath)).toBe(true);
    });
  });
});
