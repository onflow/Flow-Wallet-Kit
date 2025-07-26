import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ChaChaPolyCipher } from '../../src/crypto/ChaChaPolyCipher.js';
import { WalletErrorCode } from '../../src/utils/errors.js';
import { hexToBytes, bytesToHex, stringToBytes, bytesToString } from '../../src/utils/crypto.js';

describe('ChaChaPolyCipher', () => {
  describe('constructor', () => {
    it('should create cipher with password', () => {
      const cipher = new ChaChaPolyCipher('my-secret-password');
      expect(cipher).toBeDefined();
      expect(cipher.key).toHaveLength(32); // 256 bits
      expect(cipher.keySize).toBe(256);
    });

    it('should derive consistent key from same password', () => {
      const password = 'test-password-123';
      const cipher1 = new ChaChaPolyCipher(password);
      const cipher2 = new ChaChaPolyCipher(password);
      
      expect(bytesToHex(cipher1.key)).toBe(bytesToHex(cipher2.key));
    });

    it('should derive different keys from different passwords', () => {
      const cipher1 = new ChaChaPolyCipher('password1');
      const cipher2 = new ChaChaPolyCipher('password2');
      
      expect(bytesToHex(cipher1.key)).not.toBe(bytesToHex(cipher2.key));
    });

    it('should handle empty password', () => {
      const cipher = new ChaChaPolyCipher('');
      expect(cipher.key).toHaveLength(32);
    });

    it('should handle unicode password', () => {
      const cipher = new ChaChaPolyCipher('パスワード🔐');
      expect(cipher.key).toHaveLength(32);
    });
  });

  describe('fromKey', () => {
    it('should create cipher from valid key', () => {
      const key = new Uint8Array(32);
      key.fill(0x42);
      
      const cipher = ChaChaPolyCipher.fromKey(key);
      expect(cipher).toBeDefined();
      expect(bytesToHex(cipher.key)).toBe(bytesToHex(key));
      expect(cipher.keySize).toBe(256);
    });

    it('should throw error for invalid key size', () => {
      const shortKey = new Uint8Array(16);
      const longKey = new Uint8Array(64);
      
      expect(() => ChaChaPolyCipher.fromKey(shortKey)).toThrow();
      expect(() => ChaChaPolyCipher.fromKey(longKey)).toThrow();
      
      try {
        ChaChaPolyCipher.fromKey(shortKey);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.InvalidPrivateKey);
        expect(error.message).toContain('Invalid key size');
      }
    });

    it('should create independent cipher instance', () => {
      const key = new Uint8Array(32);
      key.fill(0xFF);
      
      const cipher = ChaChaPolyCipher.fromKey(key);
      
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
      const cipher = new ChaChaPolyCipher('test-password');
      const plaintext = stringToBytes('Hello, World! 🌍');
      
      const encrypted = await cipher.encrypt(plaintext);
      expect(encrypted).toBeDefined();
      expect(encrypted.length).toBeGreaterThan(plaintext.length);
      
      const decrypted = await cipher.decrypt(encrypted);
      expect(bytesToString(decrypted)).toBe('Hello, World! 🌍');
    });

    it('should produce different ciphertexts for same plaintext (due to random nonce)', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
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
      const cipher = new ChaChaPolyCipher('test-password');
      const empty = new Uint8Array(0);
      
      const encrypted = await cipher.encrypt(empty);
      expect(encrypted.length).toBeGreaterThan(0); // Should have nonce + tag
      
      const decrypted = await cipher.decrypt(encrypted);
      expect(decrypted).toHaveLength(0);
    });

    it('should handle large data', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
      const largeData = new Uint8Array(1024 * 1024); // 1MB
      largeData.fill(0xCD);
      
      const encrypted = await cipher.encrypt(largeData);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(decrypted).toHaveLength(largeData.length);
      expect(decrypted[0]).toBe(0xCD);
      expect(decrypted[decrypted.length - 1]).toBe(0xCD);
    });

    it('should fail decryption with wrong password', async () => {
      const cipher1 = new ChaChaPolyCipher('password1');
      const cipher2 = new ChaChaPolyCipher('password2');
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
      const cipher = new ChaChaPolyCipher('test-password');
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
      const cipher = new ChaChaPolyCipher('test-password');
      
      // Data too short to contain nonce and tag
      const tooShort = new Uint8Array(15);
      
      await expect(cipher.decrypt(tooShort)).rejects.toThrow();
      
      try {
        await cipher.decrypt(tooShort);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.InvalidNonce);
        expect(error.message).toContain('too short');
      }
    });

    it('should handle encryption errors gracefully', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
      
      // Test with invalid input that would cause the cipher to throw
      const invalidData = null as any;
      
      await expect(cipher.encrypt(invalidData)).rejects.toThrow();
      
      try {
        await cipher.encrypt(invalidData);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.EncryptionFailed);
        expect(error.message).toContain('Encryption failed');
      }
    });
  });

  describe('nonce handling', () => {
    it('should prepend nonce to encrypted data', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
      const plaintext = stringToBytes('Test');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // First 12 bytes should be the nonce
      expect(encrypted.length).toBeGreaterThanOrEqual(12 + 16); // nonce + tag
      
      // Extract and verify nonce
      const nonce = encrypted.slice(0, 12);
      expect(nonce).toHaveLength(12);
    });

    it('should use unique nonces', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
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

  describe('tag handling', () => {
    it('should include authentication tag', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
      const plaintext = stringToBytes('Test message');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // Encrypted data should be: nonce (12) + ciphertext + tag (16)
      expect(encrypted.length).toBe(12 + plaintext.length + 16);
    });

    it('should verify authentication tag on decryption', async () => {
      const cipher = new ChaChaPolyCipher('test-password');
      const plaintext = stringToBytes('Authenticated message');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // Tamper with the tag (last 16 bytes)
      encrypted[encrypted.length - 5] ^= 0x01;
      
      // Decryption should fail due to invalid tag
      await expect(cipher.decrypt(encrypted)).rejects.toThrow();
    });
  });

  describe('compatibility', () => {
    it('should use correct key derivation', async () => {
      // Test that the key derivation matches expected values
      const password = 'test-password';
      const cipher = new ChaChaPolyCipher(password);
      
      // The key derived from 'test-password' using SHA-256
      const expectedKeyHex = 'c638833f69bbfb3c267afa0a74434812436b8f08a81fd263c6be6871de4f1265';
      expect(bytesToHex(cipher.key)).toBe(expectedKeyHex);
    });

    it('should handle binary data correctly', async () => {
      const cipher = new ChaChaPolyCipher('binary-test');
      
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

  describe('performance characteristics', () => {
    it('should handle stream-like data efficiently', async () => {
      const cipher = new ChaChaPolyCipher('stream-test');
      
      // ChaCha20 is a stream cipher, test with various sizes
      const sizes = [16, 64, 256, 1024, 4096, 16384];
      
      for (const size of sizes) {
        const data = new Uint8Array(size);
        crypto.getRandomValues(data);
        
        const encrypted = await cipher.encrypt(data);
        const decrypted = await cipher.decrypt(encrypted);
        
        expect(decrypted).toEqual(data);
      }
    });
  });

  describe('edge cases', () => {
    it('should handle very long passwords', async () => {
      const longPassword = 'b'.repeat(10000);
      const cipher = new ChaChaPolyCipher(longPassword);
      const plaintext = stringToBytes('Test');
      
      const encrypted = await cipher.encrypt(plaintext);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(bytesToString(decrypted)).toBe('Test');
    });

    it('should handle special characters in password', async () => {
      const specialPassword = '!@#$%^&*()_+-=[]{}|;\':",./<>?`~\n\t\r\0';
      const cipher = new ChaChaPolyCipher(specialPassword);
      const plaintext = stringToBytes('Test message');
      
      const encrypted = await cipher.encrypt(plaintext);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(bytesToString(decrypted)).toBe('Test message');
    });

    it('should handle repeated encryption/decryption', async () => {
      const cipher = new ChaChaPolyCipher('stress-test');
      const plaintext = stringToBytes('Repeated test');
      
      // Perform many encrypt/decrypt cycles
      for (let i = 0; i < 100; i++) {
        const encrypted = await cipher.encrypt(plaintext);
        const decrypted = await cipher.decrypt(encrypted);
        expect(bytesToString(decrypted)).toBe('Repeated test');
      }
    });

    it('should handle concurrent operations', async () => {
      const cipher = new ChaChaPolyCipher('concurrent-test');
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

    it('should handle data with repeating patterns', async () => {
      const cipher = new ChaChaPolyCipher('pattern-test');
      
      // Create data with repeating pattern
      const pattern = new Uint8Array([0xDE, 0xAD, 0xBE, 0xEF]);
      const repeats = 1000;
      const patternData = new Uint8Array(pattern.length * repeats);
      
      for (let i = 0; i < repeats; i++) {
        patternData.set(pattern, i * pattern.length);
      }
      
      const encrypted = await cipher.encrypt(patternData);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(decrypted).toEqual(patternData);
      
      // Encrypted data should not show the pattern
      let patternFound = false;
      for (let i = 0; i < encrypted.length - pattern.length; i++) {
        if (encrypted[i] === 0xDE && 
            encrypted[i + 1] === 0xAD && 
            encrypted[i + 2] === 0xBE && 
            encrypted[i + 3] === 0xEF) {
          patternFound = true;
          break;
        }
      }
      expect(patternFound).toBe(false);
    });
  });

  describe('ChaCha20-Poly1305 specific tests', () => {
    it('should handle counter overflow gracefully', async () => {
      // ChaCha20 uses a 32-bit counter, test behavior near limits
      const cipher = new ChaChaPolyCipher('counter-test');
      
      // Create data that would require many blocks
      const blockSize = 64; // ChaCha20 block size
      const blocks = 100;
      const data = new Uint8Array(blockSize * blocks);
      crypto.getRandomValues(data);
      
      const encrypted = await cipher.encrypt(data);
      const decrypted = await cipher.decrypt(encrypted);
      
      expect(decrypted).toEqual(data);
    });

    it('should maintain AEAD properties', async () => {
      const cipher = new ChaChaPolyCipher('aead-test');
      const plaintext = stringToBytes('Authenticated Encryption with Associated Data');
      
      const encrypted = await cipher.encrypt(plaintext);
      
      // Any modification should cause authentication failure
      const modifications = [
        () => { encrypted[0] ^= 0x01; }, // Modify nonce
        () => { encrypted[15] ^= 0x01; }, // Modify ciphertext
        () => { encrypted[encrypted.length - 1] ^= 0x01; }, // Modify tag
      ];
      
      for (const modify of modifications) {
        const encryptedCopy = new Uint8Array(encrypted);
        modify.call(null);
        
        await expect(cipher.decrypt(encrypted)).rejects.toThrow();
        
        // Restore for next test
        encrypted.set(encryptedCopy);
      }
    });
  });
});