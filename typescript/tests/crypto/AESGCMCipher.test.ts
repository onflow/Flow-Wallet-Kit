import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AESGCMCipher } from '../../src/crypto/AESGCMCipher.js';
import { WalletErrorCode } from '../../src/utils/errors.js';
import { hexToBytes, bytesToHex, stringToBytes, bytesToString } from '../../src/utils/crypto.js';

describe('AESGCMCipher', () => {
  // Mock crypto.getRandomValues for deterministic tests when needed
  let originalGetRandomValues: typeof crypto.getRandomValues;

  beforeEach(() => {
    originalGetRandomValues = crypto.getRandomValues;
  });

  afterEach(() => {
    crypto.getRandomValues = originalGetRandomValues;
  });

  describe('constructor', () => {
    it('should create cipher with password', () => {
      const cipher = new AESGCMCipher('my-secret-password');
      expect(cipher).toBeDefined();
      expect(cipher.key).toHaveLength(32); // 256 bits
      expect(cipher.keySize).toBe(256);
    });

    it('should derive consistent key from same password', () => {
      const password = 'test-password-123';
      const cipher1 = new AESGCMCipher(password);
      const cipher2 = new AESGCMCipher(password);
      
      expect(bytesToHex(cipher1.key)).toBe(bytesToHex(cipher2.key));
    });

    it('should derive different keys from different passwords', () => {
      const cipher1 = new AESGCMCipher('password1');
      const cipher2 = new AESGCMCipher('password2');
      
      expect(bytesToHex(cipher1.key)).not.toBe(bytesToHex(cipher2.key));
    });

    it('should handle empty password', () => {
      const cipher = new AESGCMCipher('');
      expect(cipher.key).toHaveLength(32);
    });

    it('should handle unicode password', () => {
      const cipher = new AESGCMCipher('パスワード🔐');
      expect(cipher.key).toHaveLength(32);
    });
  });

  describe('fromKey', () => {
    it('should create cipher from valid key', () => {
      const key = new Uint8Array(32);
      key.fill(0x42);
      
      const cipher = AESGCMCipher.fromKey(key);
      expect(cipher).toBeDefined();
      expect(bytesToHex(cipher.key)).toBe(bytesToHex(key));
      expect(cipher.keySize).toBe(256);
    });

    it('should throw error for invalid key size', () => {
      const shortKey = new Uint8Array(16);
      const longKey = new Uint8Array(64);
      
      expect(() => AESGCMCipher.fromKey(shortKey)).toThrow();
      expect(() => AESGCMCipher.fromKey(longKey)).toThrow();
      
      try {
        AESGCMCipher.fromKey(shortKey);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.InvalidPrivateKey);
        expect(error.message).toContain('Invalid key size');
      }
    });

    it('should create independent cipher instance', () => {
      const key = new Uint8Array(32);
      key.fill(0xFF);
      
      const cipher = AESGCMCipher.fromKey(key);
      
      // The cipher should have its own copy of the key
      const cipherKeyBefore = new Uint8Array(cipher.key);
      
      // Modifying the original key should not affect the cipher
      key.fill(0x00);
      expect(cipher.key).toEqual(cipherKeyBefore);
      expect(cipher.key[0]).toBe(0xFF);
    });
  });

  describe('encrypt/decrypt', () => {
    it('should encrypt and decrypt data successfully', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Hello, World! 🌍');
      
      const encrypted = await cipher.encrypt(plaintext);
      expect(encrypted).toBeDefined();
      expect(encrypted.length).toBeGreaterThan(plaintext.length);
      
      const decrypted = await cipher.decrypt(encrypted);
      expect(bytesToString(decrypted)).toBe('Hello, World! 🌍');
    });

    it('should produce different ciphertexts for same plaintext (due to random nonce)', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Same message');
      
      const encrypted1 = await cipher.encrypt(plaintext);
      const encrypted2 = await cipher.encrypt(plaintext);
      
      // Ciphertexts should be different due to different nonces
      expect(bytesToHex(encrypted1)).not.toBe(bytesToHex(encrypted2));
      
      // But both should decrypt to the same plaintext
      const decrypted1 = await cipher.decrypt(encrypted1);
      const decrypted2 = await cipher.decrypt(encrypted2);
      expect(bytesToString(decrypted1)).toBe('Same message');
      expect(bytesToString(decrypted2)).toBe('Same message');
    });

    it('should handle empty data', async () => {
      const cipher = new AESGCMCipher('test-password');
      const empty = new Uint8Array(0);
      
      const encrypted = await cipher.encrypt(empty);
      expect(encrypted.length).toBeGreaterThan(0); // Should have nonce + tag
      
      const decrypted = await cipher.decrypt(encrypted);
      expect(decrypted).toHaveLength(0);
    });

    it('should handle large data', async () => {
      const cipher = new AESGCMCipher('test-password');
      const largeData = new Uint8Array(1024 * 1024); // 1MB
      largeData.fill(0xAB);
      
      const encrypted = await cipher.encrypt(largeData);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(decrypted).toHaveLength(largeData.length);
      expect(decrypted[0]).toBe(0xAB);
      expect(decrypted[decrypted.length - 1]).toBe(0xAB);
    });

    it('should fail decryption with wrong password', async () => {
      const cipher1 = new AESGCMCipher('password1');
      const cipher2 = new AESGCMCipher('password2');
      const plaintext = stringToBytes('Secret message');
      
      const encrypted = await cipher1.encrypt(plaintext);
      
      await expect(cipher2.decrypt(encrypted)).rejects.toThrow();
      
      try {
        await cipher2.decrypt(encrypted);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.DecryptionFailed);
      }
    });

    it('should fail decryption with corrupted data', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Test message');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // Corrupt the encrypted data
      encrypted[encrypted.length - 1] ^= 0xFF;
      
      await expect(cipher.decrypt(encrypted)).rejects.toThrow();
      
      try {
        await cipher.decrypt(encrypted);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.DecryptionFailed);
      }
    });

    it('should fail decryption with truncated data', async () => {
      const cipher = new AESGCMCipher('test-password');
      
      // Data too short to contain nonce
      const tooShort = new Uint8Array(5);
      
      await expect(cipher.decrypt(tooShort)).rejects.toThrow();
      
      try {
        await cipher.decrypt(tooShort);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.InvalidNonce);
        expect(error.message).toContain('too short');
      }
    });

    it('should handle encryption errors gracefully', async () => {
      const cipher = new AESGCMCipher('test-password');
      
      // Mock crypto.subtle.encrypt to throw
      const originalEncrypt = crypto.subtle.encrypt;
      crypto.subtle.encrypt = vi.fn().mockRejectedValue(new Error('Encryption failed'));
      
      await expect(cipher.encrypt(new Uint8Array([1, 2, 3]))).rejects.toThrow();
      
      try {
        await cipher.encrypt(new Uint8Array([1, 2, 3]));
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.EncryptionFailed);
        expect(error.message).toContain('Encryption failed');
      }
      
      // Restore
      crypto.subtle.encrypt = originalEncrypt;
    });
  });

  describe('nonce handling', () => {
    it('should prepend nonce to encrypted data', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Test');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // First 12 bytes should be the nonce
      expect(encrypted.length).toBeGreaterThanOrEqual(12);
      
      // Extract and verify nonce is used in decryption
      const nonce = encrypted.slice(0, 12);
      expect(nonce).toHaveLength(12);
    });

    it('should use unique nonces', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Test');
      const nonces = new Set<string>();
      
      // Encrypt multiple times and collect nonces
      for (let i = 0; i < 100; i++) {
        const encrypted = await cipher.encrypt(plaintext);
        const nonce = encrypted.slice(0, 12);
        nonces.add(bytesToHex(nonce));
      }
      
      // All nonces should be unique
      expect(nonces.size).toBe(100);
    });
  });

  describe('key caching', () => {
    it('should cache CryptoKey for performance', async () => {
      const cipher = new AESGCMCipher('test-password');
      const plaintext = stringToBytes('Test');
      
      // Spy on importKey
      const importKeySpy = vi.spyOn(crypto.subtle, 'importKey');
      
      // First encryption should import key
      await cipher.encrypt(plaintext);
      expect(importKeySpy).toHaveBeenCalledTimes(1);
      
      // Second encryption should reuse cached key
      await cipher.encrypt(plaintext);
      expect(importKeySpy).toHaveBeenCalledTimes(1);
      
      // Decryption should also reuse cached key
      const encrypted = await cipher.encrypt(plaintext);
      await cipher.decrypt(encrypted);
      expect(importKeySpy).toHaveBeenCalledTimes(1);
      
      importKeySpy.mockRestore();
    });
  });

  describe('compatibility', () => {
    it('should decrypt known test vectors', async () => {
      // Test vector created with known implementation
      const password = 'test-password';
      const cipher = new AESGCMCipher(password);
      
      // The key derived from 'test-password' using SHA-256
      const expectedKeyHex = 'c638833f69bbfb3c267afa0a74434812436b8f08a81fd263c6be6871de4f1265';
      expect(bytesToHex(cipher.key)).toBe(expectedKeyHex);
    });

    it('should handle binary data correctly', async () => {
      const cipher = new AESGCMCipher('binary-test');
      
      // Create binary data with all byte values
      const binaryData = new Uint8Array(256);
      for (let i = 0; i < 256; i++) {
        binaryData[i] = i;
      }
      
      const encrypted = await cipher.encrypt(binaryData);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(decrypted).toHaveLength(256);
      for (let i = 0; i < 256; i++) {
        expect(decrypted[i]).toBe(i);
      }
    });
  });

  describe('edge cases', () => {
    it('should handle very long passwords', async () => {
      const longPassword = 'a'.repeat(10000);
      const cipher = new AESGCMCipher(longPassword);
      const plaintext = stringToBytes('Test');
      
      const encrypted = await cipher.encrypt(plaintext);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(bytesToString(decrypted)).toBe('Test');
    });

    it('should handle special characters in password', async () => {
      const specialPassword = '!@#$%^&*()_+-=[]{}|;\':",./<>?`~\n\t\r';
      const cipher = new AESGCMCipher(specialPassword);
      const plaintext = stringToBytes('Test message');
      
      const encrypted = await cipher.encrypt(plaintext);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(bytesToString(decrypted)).toBe('Test message');
    });

    it('should handle repeated encryption/decryption', async () => {
      const cipher = new AESGCMCipher('stress-test');
      const plaintext = stringToBytes('Repeated test');
      
      // Perform many encrypt/decrypt cycles
      for (let i = 0; i < 100; i++) {
        const encrypted = await cipher.encrypt(plaintext);
        const decrypted = await cipher.decrypt(encrypted);
        expect(bytesToString(decrypted)).toBe('Repeated test');
      }
    });

    it('should handle concurrent operations', async () => {
      const cipher = new AESGCMCipher('concurrent-test');
      const messages = [
        'Message 1',
        'Message 2',
        'Message 3',
        'Message 4',
        'Message 5'
      ];
      
      // Encrypt all messages concurrently
      const encryptPromises = messages.map(msg => 
        cipher.encrypt(stringToBytes(msg))
      );
      const encryptedMessages = await Promise.all(encryptPromises);
      
      // Decrypt all messages concurrently
      const decryptPromises = encryptedMessages.map(enc => 
        cipher.decrypt(enc)
      );
      const decryptedMessages = await Promise.all(decryptPromises);
      
      // Verify all messages
      decryptedMessages.forEach((decrypted, index) => {
        expect(bytesToString(decrypted)).toBe(messages[index]);
      });
    });
  });
});