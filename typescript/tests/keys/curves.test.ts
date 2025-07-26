/**
 * Flow Wallet Kit - Elliptic Curves Tests
 * 
 * Comprehensive test suite for elliptic curve utilities.
 */

import { describe, it, expect } from 'vitest';
import {
  getCurve,
  getHasher,
  generatePrivateKey,
  derivePublicKey,
  getCompressedPublicKey,
  isValidPrivateKey,
  sign,
  verify,
  privateKeyToHex,
  privateKeyFromHex,
  publicKeyToHex,
  publicKeyFromHex,
  getPublicKeyPoint,
  normalizeSignature
} from '../../src/keys/curves.js';
import { SigningAlgorithm, HashingAlgorithm } from '../../src/types/key.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { hexToBytes, bytesToHex, stringToBytes } from '../../src/utils/crypto.js';
import { p256 } from '@noble/curves/p256';
import { secp256k1 } from '@noble/curves/secp256k1';

describe('Curve Utilities', () => {
  describe('getCurve', () => {
    it('should return P256 curve', () => {
      const curve = getCurve(SigningAlgorithm.ECDSA_P256);
      expect(curve).toBe(p256);
    });
    
    it('should return secp256k1 curve', () => {
      const curve = getCurve(SigningAlgorithm.ECDSA_SECP256K1);
      expect(curve).toBe(secp256k1);
    });
    
    it('should throw for unsupported algorithm', () => {
      expect(() => getCurve('INVALID' as SigningAlgorithm))
        .toThrow(WalletError);
    });
  });
  
  describe('getHasher', () => {
    it('should return SHA256 hasher', () => {
      const hasher = getHasher(HashingAlgorithm.SHA2_256);
      const testData = stringToBytes('test');
      const hash = hasher(testData);
      
      expect(hash).toBeInstanceOf(Uint8Array);
      expect(hash.length).toBe(32);
    });
    
    it('should return SHA3-256 hasher', () => {
      const hasher = getHasher(HashingAlgorithm.SHA3_256);
      const testData = stringToBytes('test');
      const hash = hasher(testData);
      
      expect(hash).toBeInstanceOf(Uint8Array);
      expect(hash.length).toBe(32);
    });
    
    it('should produce different hashes for different algorithms', () => {
      const testData = stringToBytes('test data');
      const sha256 = getHasher(HashingAlgorithm.SHA2_256);
      const sha3_256 = getHasher(HashingAlgorithm.SHA3_256);
      
      const hash1 = sha256(testData);
      const hash2 = sha3_256(testData);
      
      expect(hash1).not.toEqual(hash2);
    });
    
    it('should throw for unsupported algorithm', () => {
      expect(() => getHasher('INVALID' as HashingAlgorithm))
        .toThrow(WalletError);
    });
  });
  
  describe('Private Key Generation', () => {
    it('should generate valid P256 private key', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      
      expect(privateKey).toBeInstanceOf(Uint8Array);
      expect(privateKey.length).toBe(32);
      expect(isValidPrivateKey(privateKey, SigningAlgorithm.ECDSA_P256)).toBe(true);
    });
    
    it('should generate valid secp256k1 private key', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
      
      expect(privateKey).toBeInstanceOf(Uint8Array);
      expect(privateKey.length).toBe(32);
      expect(isValidPrivateKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1)).toBe(true);
    });
    
    it('should generate different keys each time', () => {
      const key1 = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const key2 = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      
      expect(key1).not.toEqual(key2);
    });
  });
  
  describe('Public Key Derivation', () => {
    it('should derive P256 public key', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      
      expect(publicKey).toBeInstanceOf(Uint8Array);
      expect(publicKey.length).toBe(65); // Uncompressed: 0x04 + 32 + 32
      expect(publicKey[0]).toBe(0x04); // Uncompressed prefix
    });
    
    it('should derive secp256k1 public key', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1);
      
      expect(publicKey).toBeInstanceOf(Uint8Array);
      expect(publicKey.length).toBe(65); // Uncompressed
      expect(publicKey[0]).toBe(0x04);
    });
    
    it('should derive compressed public key', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const compressedKey = getCompressedPublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      
      expect(compressedKey).toBeInstanceOf(Uint8Array);
      expect(compressedKey.length).toBe(33); // Compressed
      expect([0x02, 0x03]).toContain(compressedKey[0]); // Compressed prefix
    });
    
    it('should produce consistent public keys', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const publicKey1 = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      const publicKey2 = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      
      expect(publicKey1).toEqual(publicKey2);
    });
  });
  
  describe('Private Key Validation', () => {
    it('should validate correct P256 private key', () => {
      const validKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      expect(isValidPrivateKey(validKey, SigningAlgorithm.ECDSA_P256)).toBe(true);
    });
    
    it('should validate correct secp256k1 private key', () => {
      const validKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
      expect(isValidPrivateKey(validKey, SigningAlgorithm.ECDSA_SECP256K1)).toBe(true);
    });
    
    it('should reject zero private key', () => {
      const zeroKey = new Uint8Array(32);
      expect(isValidPrivateKey(zeroKey, SigningAlgorithm.ECDSA_P256)).toBe(false);
      expect(isValidPrivateKey(zeroKey, SigningAlgorithm.ECDSA_SECP256K1)).toBe(false);
    });
    
    it('should reject private key exceeding curve order', () => {
      // Create a key that's definitely too large (all 0xFF)
      const largeKey = new Uint8Array(32).fill(0xFF);
      expect(isValidPrivateKey(largeKey, SigningAlgorithm.ECDSA_P256)).toBe(false);
      expect(isValidPrivateKey(largeKey, SigningAlgorithm.ECDSA_SECP256K1)).toBe(false);
    });
    
    it('should reject wrong size keys', () => {
      const shortKey = new Uint8Array(16);
      const longKey = new Uint8Array(64);
      
      expect(isValidPrivateKey(shortKey, SigningAlgorithm.ECDSA_P256)).toBe(false);
      expect(isValidPrivateKey(longKey, SigningAlgorithm.ECDSA_P256)).toBe(false);
    });
    
    it('should handle validation errors gracefully', () => {
      const invalidKey = new Uint8Array([1, 2, 3]); // Wrong size
      expect(isValidPrivateKey(invalidKey, SigningAlgorithm.ECDSA_P256)).toBe(false);
    });
  });
  
  describe('Signing and Verification', () => {
    describe('P256 Signing', () => {
      it('should sign and verify with SHA256', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Test message for P256');
        
        const signature = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(signature).toBeInstanceOf(Uint8Array);
        expect(signature.length).toBeGreaterThan(0);
        
        const isValid = verify(
          signature,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(true);
      });
      
      it('should sign and verify with SHA3-256', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Test message for P256 with SHA3');
        
        const signature = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA3_256,
          false
        );
        
        const isValid = verify(
          signature,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA3_256,
          false
        );
        
        expect(isValid).toBe(true);
      });
      
      it('should handle prehashed messages', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Prehashed message test');
        
        // Hash the message first
        const hasher = getHasher(HashingAlgorithm.SHA2_256);
        const messageHash = hasher(message);
        
        const signature = sign(
          messageHash,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          true // prehashed
        );
        
        const isValid = verify(
          signature,
          messageHash,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          true // prehashed
        );
        
        expect(isValid).toBe(true);
      });
    });
    
    describe('secp256k1 Signing', () => {
      it('should sign and verify with SHA256', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1);
        const message = stringToBytes('Test message for secp256k1');
        
        const signature = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_SECP256K1,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        const isValid = verify(
          signature,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_SECP256K1,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(true);
      });
      
      it('should sign and verify with SHA3-256', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1);
        const message = stringToBytes('Test message for secp256k1 with SHA3');
        
        const signature = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_SECP256K1,
          HashingAlgorithm.SHA3_256,
          false
        );
        
        const isValid = verify(
          signature,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_SECP256K1,
          HashingAlgorithm.SHA3_256,
          false
        );
        
        expect(isValid).toBe(true);
      });
    });
    
    describe('Signature Verification Edge Cases', () => {
      it('should reject invalid signatures', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Original message');
        
        const validSignature = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        // Corrupt the signature
        const invalidSignature = new Uint8Array(validSignature);
        invalidSignature[10] ^= 0xFF;
        
        const isValid = verify(
          invalidSignature,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(false);
      });
      
      it('should reject wrong message', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const originalMessage = stringToBytes('Original message');
        const wrongMessage = stringToBytes('Wrong message');
        
        const signature = sign(
          originalMessage,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        const isValid = verify(
          signature,
          wrongMessage,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(false);
      });
      
      it('should reject wrong public key', () => {
        const privateKey1 = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const privateKey2 = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey2 = derivePublicKey(privateKey2, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Test message');
        
        const signature = sign(
          message,
          privateKey1,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        const isValid = verify(
          signature,
          message,
          publicKey2, // Wrong public key
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(false);
      });
      
      it('should handle malformed signatures gracefully', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const message = stringToBytes('Test');
        
        // Too short signature
        const shortSig = new Uint8Array(10);
        expect(verify(
          shortSig,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        )).toBe(false);
        
        // Random bytes
        const randomSig = new Uint8Array(70);
        crypto.getRandomValues(randomSig);
        expect(verify(
          randomSig,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        )).toBe(false);
      });
    });
    
    describe('Cross-curve Verification', () => {
      it('should not verify P256 signature with secp256k1 key', () => {
        const p256PrivateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const secp256k1PrivateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
        const secp256k1PublicKey = derivePublicKey(secp256k1PrivateKey, SigningAlgorithm.ECDSA_SECP256K1);
        
        const message = stringToBytes('Cross-curve test');
        
        const p256Signature = sign(
          message,
          p256PrivateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        // Try to verify P256 signature with secp256k1 public key
        const isValid = verify(
          p256Signature,
          message,
          secp256k1PublicKey,
          SigningAlgorithm.ECDSA_SECP256K1,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(false);
      });
    });
  });
  
  describe('Key Format Conversions', () => {
    describe('Private Key Conversions', () => {
      it('should convert private key to hex and back', () => {
        const originalKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const hex = privateKeyToHex(originalKey);
        const restored = privateKeyFromHex(hex);
        
        expect(hex).toMatch(/^[0-9a-f]{64}$/);
        expect(restored).toEqual(originalKey);
      });
      
      it('should reject invalid hex on conversion', () => {
        expect(() => privateKeyFromHex('invalid-hex'))
          .toThrow(WalletError);
      });
      
      it('should reject wrong length hex', () => {
        const shortHex = 'abcd';
        const longHex = 'a'.repeat(66);
        
        expect(() => privateKeyFromHex(shortHex))
          .toThrow(WalletError);
        expect(() => privateKeyFromHex(longHex))
          .toThrow(WalletError);
      });
    });
    
    describe('Public Key Conversions', () => {
      it('should convert public key to hex and back', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const originalPublicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        
        const hex = publicKeyToHex(originalPublicKey);
        const restored = publicKeyFromHex(hex);
        
        expect(hex).toMatch(/^[0-9a-f]+$/);
        expect(restored).toEqual(originalPublicKey);
      });
      
      it('should handle compressed public keys', () => {
        const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
        const compressedKey = getCompressedPublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        
        const hex = publicKeyToHex(compressedKey);
        const restored = publicKeyFromHex(hex);
        
        expect(hex).toMatch(/^[0-9a-f]{66}$/); // 33 bytes = 66 hex chars
        expect(restored).toEqual(compressedKey);
      });
    });
  });
  
  describe('Public Key Point', () => {
    it('should get public key point for P256', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      
      const point = getPublicKeyPoint(publicKey, SigningAlgorithm.ECDSA_P256);
      
      expect(point).toBeDefined();
      expect(point.x).toBeDefined();
      expect(point.y).toBeDefined();
    });
    
    it('should get public key point for secp256k1', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_SECP256K1);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1);
      
      const point = getPublicKeyPoint(publicKey, SigningAlgorithm.ECDSA_SECP256K1);
      
      expect(point).toBeDefined();
      expect(point.x).toBeDefined();
      expect(point.y).toBeDefined();
    });
  });
  
  describe('Signature Normalization', () => {
    it('should normalize high-S signatures', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const message = stringToBytes('Test for signature normalization');
      
      // Generate signatures until we get one with high S
      let highSSignature: Uint8Array | null = null;
      for (let i = 0; i < 100; i++) {
        const sig = sign(
          message,
          privateKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        const curve = getCurve(SigningAlgorithm.ECDSA_P256);
        const sigObj = curve.Signature.fromDER(sig);
        if (sigObj.hasHighS()) {
          highSSignature = sig;
          break;
        }
      }
      
      if (highSSignature) {
        const normalized = normalizeSignature(highSSignature, SigningAlgorithm.ECDSA_P256);
        
        // Verify the normalized signature still works
        const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
        const isValid = verify(
          normalized,
          message,
          publicKey,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256,
          false
        );
        
        expect(isValid).toBe(true);
        
        // Check that S is now low
        const curve = getCurve(SigningAlgorithm.ECDSA_P256);
        const normalizedSig = curve.Signature.fromDER(normalized);
        expect(normalizedSig.hasHighS()).toBe(false);
      }
    });
    
    it('should not change low-S signatures', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const message = stringToBytes('Test message');
      
      const signature = sign(
        message,
        privateKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      const normalized = normalizeSignature(signature, SigningAlgorithm.ECDSA_P256);
      
      // For most signatures, normalization should not change them
      // (they're already low-S)
      const curve = getCurve(SigningAlgorithm.ECDSA_P256);
      const origSig = curve.Signature.fromDER(signature);
      const normSig = curve.Signature.fromDER(normalized);
      
      if (!origSig.hasHighS()) {
        expect(normalized).toEqual(signature);
      }
    });
  });
  
  describe('DER Encoding', () => {
    it('should produce valid DER-encoded signatures', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const message = stringToBytes('DER encoding test');
      
      const signature = sign(
        message,
        privateKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      // DER signature should start with 0x30 (SEQUENCE tag)
      expect(signature[0]).toBe(0x30);
      
      // Length should be reasonable
      expect(signature.length).toBeGreaterThanOrEqual(68); // Minimum DER signature
      expect(signature.length).toBeLessThanOrEqual(72); // Maximum DER signature
    });
  });
  
  describe('Edge Cases and Error Handling', () => {
    it('should handle empty messages', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      const emptyMessage = new Uint8Array(0);
      
      const signature = sign(
        emptyMessage,
        privateKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      const isValid = verify(
        signature,
        emptyMessage,
        publicKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      expect(isValid).toBe(true);
    });
    
    it('should handle large messages', () => {
      const privateKey = generatePrivateKey(SigningAlgorithm.ECDSA_P256);
      const publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
      const largeMessage = new Uint8Array(10000);
      crypto.getRandomValues(largeMessage);
      
      const signature = sign(
        largeMessage,
        privateKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      const isValid = verify(
        signature,
        largeMessage,
        publicKey,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256,
        false
      );
      
      expect(isValid).toBe(true);
    });
  });
});