import { describe, it, expect } from 'vitest';
import { Hasher, HashingAlgorithm } from '../../src/crypto/Hasher.js';
import { WalletErrorCode } from '../../src/utils/errors.js';
import { hexToBytes, bytesToHex } from '../../src/utils/crypto.js';

describe('Hasher', () => {
  // Test vectors for different hash algorithms
  const testVectors = {
    sha256: [
      {
        input: '',
        expected: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
      },
      {
        input: 'abc',
        expected: 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
      },
      {
        input: 'The quick brown fox jumps over the lazy dog',
        expected: 'd7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592'
      }
    ],
    sha3_256: [
      {
        input: '',
        expected: 'a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a'
      },
      {
        input: 'abc',
        expected: '3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532'
      }
    ],
    keccak256: [
      {
        input: '',
        expected: 'c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470'
      },
      {
        input: 'abc',
        expected: '4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45'
      }
    ]
  };

  describe('hash', () => {
    it('should hash data with SHA-256', () => {
      const data = new TextEncoder().encode('abc');
      const result = Hasher.hash(data, HashingAlgorithm.SHA2_256);
      expect(bytesToHex(result)).toBe(testVectors.sha256[1].expected);
    });

    it('should hash data with SHA3-256', () => {
      const data = new TextEncoder().encode('abc');
      const result = Hasher.hash(data, HashingAlgorithm.SHA3_256);
      expect(bytesToHex(result)).toBe(testVectors.sha3_256[1].expected);
    });

    it('should hash data with Keccak-256', () => {
      const data = new TextEncoder().encode('abc');
      const result = Hasher.hash(data, HashingAlgorithm.KECCAK_256);
      expect(bytesToHex(result)).toBe(testVectors.keccak256[1].expected);
    });

    it('should hash empty data', () => {
      const data = new Uint8Array(0);
      const sha256Result = Hasher.hash(data, HashingAlgorithm.SHA2_256);
      expect(bytesToHex(sha256Result)).toBe(testVectors.sha256[0].expected);
      
      const sha3Result = Hasher.hash(data, HashingAlgorithm.SHA3_256);
      expect(bytesToHex(sha3Result)).toBe(testVectors.sha3_256[0].expected);
      
      const keccakResult = Hasher.hash(data, HashingAlgorithm.KECCAK_256);
      expect(bytesToHex(keccakResult)).toBe(testVectors.keccak256[0].expected);
    });

    it('should throw error for unsupported algorithm', () => {
      const data = new TextEncoder().encode('test');
      expect(() => {
        Hasher.hash(data, 'INVALID_ALGORITHM' as HashingAlgorithm);
      }).toThrow();
      
      try {
        Hasher.hash(data, 'INVALID_ALGORITHM' as HashingAlgorithm);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.UnsupportedHashAlgorithm);
        expect(error.message).toContain('Unsupported hash algorithm');
      }
    });

    it('should handle large data', () => {
      const largeData = new Uint8Array(1024 * 1024); // 1MB
      largeData.fill(0x42);
      
      // Should not throw
      expect(() => {
        Hasher.hash(largeData, HashingAlgorithm.SHA2_256);
      }).not.toThrow();
    });
  });

  describe('convenience methods', () => {
    describe('sha256', () => {
      it('should compute SHA-256 hash', () => {
        testVectors.sha256.forEach(({ input, expected }) => {
          const data = new TextEncoder().encode(input);
          const result = Hasher.sha256(data);
          expect(bytesToHex(result)).toBe(expected);
        });
      });
    });

    describe('sha3_256', () => {
      it('should compute SHA3-256 hash', () => {
        testVectors.sha3_256.forEach(({ input, expected }) => {
          const data = new TextEncoder().encode(input);
          const result = Hasher.sha3_256(data);
          expect(bytesToHex(result)).toBe(expected);
        });
      });
    });

    describe('keccak256', () => {
      it('should compute Keccak-256 hash', () => {
        testVectors.keccak256.forEach(({ input, expected }) => {
          const data = new TextEncoder().encode(input);
          const result = Hasher.keccak256(data);
          expect(bytesToHex(result)).toBe(expected);
        });
      });
    });
  });

  describe('hashString', () => {
    it('should hash string with SHA-256', () => {
      const result = Hasher.hashString('abc', HashingAlgorithm.SHA2_256);
      expect(bytesToHex(result)).toBe(testVectors.sha256[1].expected);
    });

    it('should hash string with SHA3-256', () => {
      const result = Hasher.hashString('abc', HashingAlgorithm.SHA3_256);
      expect(bytesToHex(result)).toBe(testVectors.sha3_256[1].expected);
    });

    it('should hash string with Keccak-256', () => {
      const result = Hasher.hashString('abc', HashingAlgorithm.KECCAK_256);
      expect(bytesToHex(result)).toBe(testVectors.keccak256[1].expected);
    });

    it('should handle empty string', () => {
      const sha256Result = Hasher.hashString('', HashingAlgorithm.SHA2_256);
      expect(bytesToHex(sha256Result)).toBe(testVectors.sha256[0].expected);
    });

    it('should handle unicode strings', () => {
      const unicodeStr = 'Hello 世界 🌍';
      // Should not throw
      expect(() => {
        Hasher.hashString(unicodeStr, HashingAlgorithm.SHA2_256);
      }).not.toThrow();
      
      // Should produce consistent results
      const result1 = Hasher.hashString(unicodeStr, HashingAlgorithm.SHA2_256);
      const result2 = Hasher.hashString(unicodeStr, HashingAlgorithm.SHA2_256);
      expect(bytesToHex(result1)).toBe(bytesToHex(result2));
    });
  });

  describe('doubleSha256', () => {
    it('should compute double SHA-256 hash', () => {
      const data = new TextEncoder().encode('hello');
      const result = Hasher.doubleSha256(data);
      
      // Verify it's the same as hashing twice
      const firstHash = Hasher.sha256(data);
      const expectedDouble = Hasher.sha256(firstHash);
      
      expect(bytesToHex(result)).toBe(bytesToHex(expectedDouble));
    });

    it('should handle empty data', () => {
      const data = new Uint8Array(0);
      const result = Hasher.doubleSha256(data);
      
      const firstHash = Hasher.sha256(data);
      const expectedDouble = Hasher.sha256(firstHash);
      
      expect(bytesToHex(result)).toBe(bytesToHex(expectedDouble));
    });

    it('should produce consistent results', () => {
      const data = new TextEncoder().encode('test data');
      const result1 = Hasher.doubleSha256(data);
      const result2 = Hasher.doubleSha256(data);
      
      expect(bytesToHex(result1)).toBe(bytesToHex(result2));
    });
  });

  describe('hmacSha256', () => {
    // Test vectors from RFC 4231
    const hmacTestVectors = [
      {
        key: '0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b',
        data: '4869205468657265', // "Hi There"
        expected: 'b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7'
      },
      {
        key: '4a656665', // "Jefe"
        data: '7768617420646f2079612077616e7420666f72206e6f7468696e673f', // "what do ya want for nothing?"
        expected: '5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843'
      }
    ];

    it('should compute HMAC-SHA256', () => {
      hmacTestVectors.forEach(({ key, data, expected }) => {
        const keyBytes = hexToBytes(key);
        const dataBytes = hexToBytes(data);
        const result = Hasher.hmacSha256(keyBytes, dataBytes);
        expect(bytesToHex(result)).toBe(expected);
      });
    });

    it('should handle empty data', () => {
      const key = new TextEncoder().encode('secret');
      const data = new Uint8Array(0);
      
      // Should not throw
      expect(() => {
        Hasher.hmacSha256(key, data);
      }).not.toThrow();
    });

    it('should handle empty key', () => {
      const key = new Uint8Array(0);
      const data = new TextEncoder().encode('data');
      
      // Should not throw
      expect(() => {
        Hasher.hmacSha256(key, data);
      }).not.toThrow();
    });

    it('should produce consistent results', () => {
      const key = new TextEncoder().encode('my secret key');
      const data = new TextEncoder().encode('my data');
      
      const result1 = Hasher.hmacSha256(key, data);
      const result2 = Hasher.hmacSha256(key, data);
      
      expect(bytesToHex(result1)).toBe(bytesToHex(result2));
    });

    it('should produce different results with different keys', () => {
      const key1 = new TextEncoder().encode('key1');
      const key2 = new TextEncoder().encode('key2');
      const data = new TextEncoder().encode('same data');
      
      const result1 = Hasher.hmacSha256(key1, data);
      const result2 = Hasher.hmacSha256(key2, data);
      
      expect(bytesToHex(result1)).not.toBe(bytesToHex(result2));
    });

    it('should handle errors gracefully', () => {
      // Test with invalid inputs that might cause noble/hashes to throw
      const key = new TextEncoder().encode('key');
      const invalidData = null as any;
      
      expect(() => {
        Hasher.hmacSha256(key, invalidData);
      }).toThrow();
      
      try {
        Hasher.hmacSha256(key, invalidData);
      } catch (error: any) {
        expect(error.code).toBe(WalletErrorCode.SignError);
        expect(error.message).toContain('HMAC-SHA256 failed');
      }
    });
  });

  describe('edge cases', () => {
    it('should handle very large inputs', () => {
      const largeData = new Uint8Array(10 * 1024 * 1024); // 10MB
      largeData.fill(0xFF);
      
      // Should complete without error
      const result = Hasher.sha256(largeData);
      expect(result).toHaveLength(32);
    });

    it('should handle repeated hashing', () => {
      const data = new TextEncoder().encode('test');
      const results: string[] = [];
      
      // Hash the same data multiple times
      for (let i = 0; i < 100; i++) {
        const result = Hasher.sha256(data);
        results.push(bytesToHex(result));
      }
      
      // All results should be the same
      const uniqueResults = new Set(results);
      expect(uniqueResults.size).toBe(1);
    });

    it('should handle special characters', () => {
      const specialChars = '\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0A\x0B\x0C\x0D\x0E\x0F';
      const data = new TextEncoder().encode(specialChars);
      
      // Should not throw
      expect(() => {
        Hasher.sha256(data);
        Hasher.sha3_256(data);
        Hasher.keccak256(data);
      }).not.toThrow();
    });
  });
});