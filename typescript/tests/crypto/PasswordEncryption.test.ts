import { describe, it, expect, vi } from 'vitest';
import { 
  PasswordEncryption, 
  EncryptionAlgorithm, 
  EncryptedVault,
  PasswordEncryptionOptions 
} from '../../src/crypto/PasswordEncryption.js';
import { WalletErrorCode } from '../../src/utils/errors.js';
import { hexToBytes, bytesToHex, base64ToArrayBuffer, arrayBufferToBase64 } from '../../src/utils/crypto.js';

describe('PasswordEncryption', () => {
  const testData = {
    string: 'Hello, World!',
    number: 42,
    boolean: true,
    array: [1, 2, 3],
    object: { 
      name: 'Test User',
      email: 'test@example.com',
      nested: {
        value: 'deep value'
      }
    }
  };

  describe('encrypt', () => {
    it('should encrypt data with default options', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      expect(vault).toBeDefined();
      expect(vault.algorithm).toBe(EncryptionAlgorithm.AES_GCM);
      expect(vault.iterations).toBe(100000);
      expect(vault.version).toBe(1);
      expect(vault.salt).toBeDefined();
      expect(vault.data).toBeDefined();
      
      // Verify salt is base64 encoded
      expect(() => base64ToArrayBuffer(vault.salt)).not.toThrow();
      
      // Verify data is base64 encoded
      expect(() => base64ToArrayBuffer(vault.data)).not.toThrow();
    });

    it('should encrypt with custom options', async () => {
      const password = 'custom-password';
      const options: PasswordEncryptionOptions = {
        algorithm: EncryptionAlgorithm.CHACHA20_POLY1305,
        iterations: 50000,
        saltLength: 16
      };
      
      const vault = await PasswordEncryption.encrypt(password, testData, options);
      
      expect(vault.algorithm).toBe(EncryptionAlgorithm.CHACHA20_POLY1305);
      expect(vault.iterations).toBe(50000);
      
      // Check salt length
      const salt = new Uint8Array(base64ToArrayBuffer(vault.salt));
      expect(salt.length).toBe(16);
    });

    it('should produce different vaults for same data with same password', async () => {
      const password = 'same-password';
      
      const vault1 = await PasswordEncryption.encrypt(password, testData);
      const vault2 = await PasswordEncryption.encrypt(password, testData);
      
      // Different salts
      expect(vault1.salt).not.toBe(vault2.salt);
      
      // Different encrypted data (due to different salts and nonces)
      expect(vault1.data).not.toBe(vault2.data);
      
      // Same metadata
      expect(vault1.algorithm).toBe(vault2.algorithm);
      expect(vault1.iterations).toBe(vault2.iterations);
      expect(vault1.version).toBe(vault2.version);
    });

    it('should handle empty data', async () => {
      const password = 'test-password';
      const emptyData = {};
      
      const vault = await PasswordEncryption.encrypt(password, emptyData);
      expect(vault).toBeDefined();
      expect(vault.data).toBeDefined();
    });

    it('should handle null and undefined values', async () => {
      const password = 'test-password';
      const dataWithNulls = {
        nullValue: null,
        undefinedValue: undefined,
        validValue: 'test'
      };
      
      const vault = await PasswordEncryption.encrypt(password, dataWithNulls);
      expect(vault).toBeDefined();
    });

    it('should handle complex nested objects', async () => {
      const password = 'test-password';
      const complexData = {
        users: [
          { id: 1, name: 'User 1', tags: ['admin', 'user'] },
          { id: 2, name: 'User 2', tags: ['user'] }
        ],
        settings: {
          theme: 'dark',
          notifications: {
            email: true,
            push: false
          }
        },
        metadata: new Date().toISOString()
      };
      
      const vault = await PasswordEncryption.encrypt(password, complexData);
      expect(vault).toBeDefined();
    });

    it('should handle encryption errors', async () => {
      const password = 'test-password';
      
      // Create circular reference which JSON.stringify can't handle
      const circularData: any = { a: 1 };
      circularData.circular = circularData;
      
      await expect(PasswordEncryption.encrypt(password, circularData)).rejects.toThrow();
      
      try {
        await PasswordEncryption.encrypt(password, circularData);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.EncryptionFailed);
        expect(error.message).toContain('Password encryption failed');
      }
    });
  });

  describe('decrypt', () => {
    it('should decrypt data with correct password', async () => {
      const password = 'correct-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      const decrypted = await PasswordEncryption.decrypt(password, vault);
      expect(decrypted).toEqual(testData);
    });

    it('should fail with incorrect password', async () => {
      const correctPassword = 'correct-password';
      const wrongPassword = 'wrong-password';
      
      const vault = await PasswordEncryption.encrypt(correctPassword, testData);
      
      await expect(PasswordEncryption.decrypt(wrongPassword, vault)).rejects.toThrow();
      
      try {
        await PasswordEncryption.decrypt(wrongPassword, vault);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.DecryptionFailed);
      }
    });

    it('should decrypt with different encryption algorithms', async () => {
      const password = 'test-password';
      
      // Test AES-GCM
      const aesVault = await PasswordEncryption.encrypt(password, testData, {
        algorithm: EncryptionAlgorithm.AES_GCM
      });
      const aesDecrypted = await PasswordEncryption.decrypt(password, aesVault);
      expect(aesDecrypted).toEqual(testData);
      
      // Test ChaCha20-Poly1305
      const chachaVault = await PasswordEncryption.encrypt(password, testData, {
        algorithm: EncryptionAlgorithm.CHACHA20_POLY1305
      });
      const chachaDecrypted = await PasswordEncryption.decrypt(password, chachaVault);
      expect(chachaDecrypted).toEqual(testData);
    });

    it('should fail with corrupted vault data', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      // Corrupt the encrypted data
      const corruptedVault = { ...vault };
      const data = new Uint8Array(base64ToArrayBuffer(vault.data));
      data[data.length - 1] ^= 0xFF;
      corruptedVault.data = arrayBufferToBase64(data.buffer);
      
      await expect(PasswordEncryption.decrypt(password, corruptedVault)).rejects.toThrow();
    });

    it('should fail with invalid vault version', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      const invalidVault = { ...vault, version: 999 };
      
      await expect(PasswordEncryption.decrypt(password, invalidVault)).rejects.toThrow();
      
      try {
        await PasswordEncryption.decrypt(password, invalidVault);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.InvalidKeyStoreJSON);
        expect(error.message).toContain('Unsupported vault version');
      }
    });

    it('should fail with invalid base64 salt', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      const invalidVault = { ...vault, salt: 'invalid-base64!' };
      
      await expect(PasswordEncryption.decrypt(password, invalidVault)).rejects.toThrow();
    });

    it('should fail with unsupported algorithm', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      const invalidVault = { ...vault, algorithm: 'INVALID-ALGO' as EncryptionAlgorithm };
      
      await expect(PasswordEncryption.decrypt(password, invalidVault)).rejects.toThrow();
      
      try {
        await PasswordEncryption.decrypt(password, invalidVault);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.UnsupportedSignatureAlgorithm);
        expect(error.message).toContain('Unsupported encryption algorithm');
      }
    });

    it('should handle type preservation', async () => {
      const password = 'test-password';
      const typedData = {
        string: 'text',
        number: 123.456,
        integer: 42,
        boolean: true,
        null: null,
        array: [1, 'two', false, null],
        date: new Date().toISOString()
      };
      
      const vault = await PasswordEncryption.encrypt(password, typedData);
      const decrypted = await PasswordEncryption.decrypt(password, vault);
      
      expect(decrypted).toEqual(typedData);
      expect(typeof decrypted.string).toBe('string');
      expect(typeof decrypted.number).toBe('number');
      expect(typeof decrypted.boolean).toBe('boolean');
      expect(decrypted.null).toBeNull();
      expect(Array.isArray(decrypted.array)).toBe(true);
    });
  });

  describe('reEncrypt', () => {
    it('should re-encrypt with new password', async () => {
      const oldPassword = 'old-password';
      const newPassword = 'new-password';
      
      const originalVault = await PasswordEncryption.encrypt(oldPassword, testData);
      const newVault = await PasswordEncryption.reEncrypt(oldPassword, newPassword, originalVault);
      
      // Should not decrypt with old password
      await expect(PasswordEncryption.decrypt(oldPassword, newVault)).rejects.toThrow();
      
      // Should decrypt with new password
      const decrypted = await PasswordEncryption.decrypt(newPassword, newVault);
      expect(decrypted).toEqual(testData);
    });

    it('should re-encrypt with new options', async () => {
      const oldPassword = 'password';
      const newPassword = 'password'; // Same password, different options
      
      const originalVault = await PasswordEncryption.encrypt(oldPassword, testData, {
        algorithm: EncryptionAlgorithm.AES_GCM,
        iterations: 100000
      });
      
      const newVault = await PasswordEncryption.reEncrypt(oldPassword, newPassword, originalVault, {
        algorithm: EncryptionAlgorithm.CHACHA20_POLY1305,
        iterations: 200000
      });
      
      expect(newVault.algorithm).toBe(EncryptionAlgorithm.CHACHA20_POLY1305);
      expect(newVault.iterations).toBe(200000);
      
      const decrypted = await PasswordEncryption.decrypt(newPassword, newVault);
      expect(decrypted).toEqual(testData);
    });

    it('should fail re-encryption with wrong old password', async () => {
      const correctPassword = 'correct-password';
      const wrongPassword = 'wrong-password';
      const newPassword = 'new-password';
      
      const vault = await PasswordEncryption.encrypt(correctPassword, testData);
      
      await expect(
        PasswordEncryption.reEncrypt(wrongPassword, newPassword, vault)
      ).rejects.toThrow();
    });
  });

  describe('verifyPassword', () => {
    it('should verify correct password', async () => {
      const password = 'correct-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      const isValid = await PasswordEncryption.verifyPassword(password, vault);
      expect(isValid).toBe(true);
    });

    it('should reject incorrect password', async () => {
      const correctPassword = 'correct-password';
      const wrongPassword = 'wrong-password';
      
      const vault = await PasswordEncryption.encrypt(correctPassword, testData);
      
      const isValid = await PasswordEncryption.verifyPassword(wrongPassword, vault);
      expect(isValid).toBe(false);
    });

    it('should handle other errors', async () => {
      const password = 'test-password';
      const vault = await PasswordEncryption.encrypt(password, testData);
      
      // Create invalid vault that will cause non-decryption error
      const invalidVault = { ...vault, version: 999 };
      
      // This should throw because of invalid version, not return false
      await expect(
        PasswordEncryption.verifyPassword(password, invalidVault)
      ).rejects.toThrow();
    });
  });

  describe('simple encrypt/decrypt', () => {
    it('should perform simple encryption and decryption', async () => {
      const password = 'simple-password';
      
      const encrypted = await PasswordEncryption.simpleEncrypt(password, testData);
      expect(typeof encrypted).toBe('string');
      
      // Should be valid JSON
      expect(() => JSON.parse(encrypted)).not.toThrow();
      
      const decrypted = await PasswordEncryption.simpleDecrypt(password, encrypted);
      expect(decrypted).toEqual(testData);
    });

    it('should handle string data', async () => {
      const password = 'test-password';
      const stringData = 'Just a simple string';
      
      const encrypted = await PasswordEncryption.simpleEncrypt(password, stringData);
      const decrypted = await PasswordEncryption.simpleDecrypt<string>(password, encrypted);
      
      expect(decrypted).toBe(stringData);
    });

    it('should handle array data', async () => {
      const password = 'test-password';
      const arrayData = [1, 2, 3, 'four', { five: 5 }];
      
      const encrypted = await PasswordEncryption.simpleEncrypt(password, arrayData);
      const decrypted = await PasswordEncryption.simpleDecrypt<typeof arrayData>(password, encrypted);
      
      expect(decrypted).toEqual(arrayData);
    });

    it('should fail simple decryption with wrong password', async () => {
      const correctPassword = 'correct-password';
      const wrongPassword = 'wrong-password';
      
      const encrypted = await PasswordEncryption.simpleEncrypt(correctPassword, testData);
      
      await expect(
        PasswordEncryption.simpleDecrypt(wrongPassword, encrypted)
      ).rejects.toThrow();
    });

    it('should fail with invalid encrypted string', async () => {
      const password = 'test-password';
      
      await expect(
        PasswordEncryption.simpleDecrypt(password, 'not-valid-json')
      ).rejects.toThrow();
      
      await expect(
        PasswordEncryption.simpleDecrypt(password, '{}')
      ).rejects.toThrow();
    });
  });

  describe('key derivation', () => {
    it('should use consistent key derivation', async () => {
      const password = 'test-password';
      const salt = 'fixed-salt-for-testing';
      const iterations = 100000;
      
      // Create two vaults with same parameters
      const vault1 = await PasswordEncryption.encrypt(password, { test: 1 });
      const vault2 = await PasswordEncryption.encrypt(password, { test: 2 });
      
      // Different data and salts
      expect(vault1.data).not.toBe(vault2.data);
      expect(vault1.salt).not.toBe(vault2.salt);
      
      // Same parameters
      expect(vault1.iterations).toBe(vault2.iterations);
    });

    it('should handle different iteration counts', async () => {
      const password = 'test-password';
      const data = { message: 'test' };
      
      const vault1000 = await PasswordEncryption.encrypt(password, data, {
        iterations: 1000
      });
      
      const vault100000 = await PasswordEncryption.encrypt(password, data, {
        iterations: 100000
      });
      
      expect(vault1000.iterations).toBe(1000);
      expect(vault100000.iterations).toBe(100000);
      
      // Both should decrypt successfully
      const decrypted1000 = await PasswordEncryption.decrypt(password, vault1000);
      const decrypted100000 = await PasswordEncryption.decrypt(password, vault100000);
      
      expect(decrypted1000).toEqual(data);
      expect(decrypted100000).toEqual(data);
    });
  });

  describe('edge cases', () => {
    it('should handle very long passwords', async () => {
      const longPassword = 'x'.repeat(10000);
      const data = { test: 'data' };
      
      const vault = await PasswordEncryption.encrypt(longPassword, data);
      const decrypted = await PasswordEncryption.decrypt(longPassword, vault);
      
      expect(decrypted).toEqual(data);
    });

    it('should handle special characters in password', async () => {
      const specialPassword = '!@#$%^&*()_+-=[]{}|;\':",./<>?`~\n\t\r\0';
      const data = { test: 'special' };
      
      const vault = await PasswordEncryption.encrypt(specialPassword, data);
      const decrypted = await PasswordEncryption.decrypt(specialPassword, vault);
      
      expect(decrypted).toEqual(data);
    });

    it('should handle large data objects', async () => {
      const password = 'test-password';
      const largeData: any = {
        array: new Array(1000).fill(0).map((_, i) => ({
          id: i,
          value: `Item ${i}`,
          nested: { deep: { value: i * 2 } }
        }))
      };
      
      const vault = await PasswordEncryption.encrypt(password, largeData);
      const decrypted = await PasswordEncryption.decrypt(password, vault);
      
      expect(decrypted).toEqual(largeData);
    });

    it('should handle unicode data', async () => {
      const password = 'test-password';
      const unicodeData = {
        english: 'Hello',
        japanese: 'こんにちは',
        arabic: 'مرحبا',
        emoji: '👋🌍🔐',
        mixed: 'Hello 世界 🌏'
      };
      
      const vault = await PasswordEncryption.encrypt(password, unicodeData);
      const decrypted = await PasswordEncryption.decrypt(password, vault);
      
      expect(decrypted).toEqual(unicodeData);
    });

    it('should handle concurrent operations', async () => {
      const password = 'concurrent-test';
      const datasets = Array.from({ length: 10 }, (_, i) => ({
        id: i,
        data: `Dataset ${i}`
      }));
      
      // Encrypt all datasets concurrently
      const encryptPromises = datasets.map(data => 
        PasswordEncryption.encrypt(password, data)
      );
      const vaults = await Promise.all(encryptPromises);
      
      // Decrypt all vaults concurrently
      const decryptPromises = vaults.map(vault => 
        PasswordEncryption.decrypt(password, vault)
      );
      const decryptedDatasets = await Promise.all(decryptPromises);
      
      // Verify all data
      decryptedDatasets.forEach((decrypted, index) => {
        expect(decrypted).toEqual(datasets[index]);
      });
    });
  });
});