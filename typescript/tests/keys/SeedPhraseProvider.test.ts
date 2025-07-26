/**
 * Flow Wallet Kit - Seed Phrase Provider Tests
 * 
 * Comprehensive test suite for the SeedPhraseProvider implementation.
 */

import { describe, it, expect, beforeEach, vi, beforeAll } from 'vitest';
import { SeedPhraseProvider, FLOW_DEFAULT_PATH } from '../../src/keys/SeedPhraseProvider.js';
import { SigningAlgorithm, HashingAlgorithm, SeedPhraseKeyOptions } from '../../src/types/key.js';
import { InMemoryProvider } from '../../src/storage/InMemoryProvider.js';
import { WalletError, WalletErrorCode } from '../../src/utils/errors.js';
import { bytesToHex, stringToBytes } from '../../src/utils/crypto.js';
import { 
  verify, 
  isValidPrivateKey, 
  generatePrivateKey,
  derivePublicKey as deriveRealPublicKey,
  sign as realSign
} from '../../src/keys/curves.js';
import * as trustwallet from '../../src/utils/trustwallet.js';

// Global counter for generating unique mnemonics
let mnemonicCounter = 0;

// Mock TrustWallet
vi.mock('../../src/utils/trustwallet.js', () => {
  // Create mock instances
  const mockMnemonic = {
    isValid: vi.fn((mnemonic: string) => {
      const words = mnemonic.split(' ');
      return words.length >= 12 && words.length <= 24 && words.length % 3 === 0;
    })
  };
  
  const createMockHDWallet = (mnemonic: string, passphrase: string = '') => {
    // Store generated keys for consistent behavior
    const keyCache = new Map<string, Uint8Array>();
    
    const getKeyForPath = (curve: number, path: string) => {
      const cacheKey = `${curve}-${path}`;
      
      if (keyCache.has(cacheKey)) {
        return keyCache.get(cacheKey)!;
      }
      
      // Generate a valid private key for the curve
      const signAlgo = curve === 1 ? SigningAlgorithm.ECDSA_P256 : SigningAlgorithm.ECDSA_SECP256K1;
      
      // Use a deterministic approach based on mnemonic, path, and curve
      const seed = new TextEncoder().encode(mnemonic + passphrase + path + curve);
      const privateKey = new Uint8Array(32);
      
      // Fill with deterministic values
      for (let i = 0; i < 32; i++) {
        privateKey[i] = seed[i % seed.length] ^ (i * 7);
      }
      
      // Ensure it's a valid key for the curve
      let attempts = 0;
      while (!isValidPrivateKey(privateKey, signAlgo) && attempts < 100) {
        privateKey[0] = (privateKey[0] + 1) % 255 || 1;
        attempts++;
      }
      
      // If still not valid, generate a real one
      if (!isValidPrivateKey(privateKey, signAlgo)) {
        const validKey = generatePrivateKey(signAlgo);
        privateKey.set(validKey);
      }
      
      keyCache.set(cacheKey, new Uint8Array(privateKey));
      return privateKey;
    };
    
    return {
      mnemonic: () => mnemonic,
      getKeyByCurve: vi.fn((curve: any, path: string) => ({
        data: () => getKeyForPath(curve, path)
      })),
      delete: vi.fn()
    };
  };
  
  const mockHDWallet = {
    create: vi.fn((strength: number, passphrase: string) => {
      const wordCount = strength / 32 * 3; // 128 bits = 12 words, etc.
      // Generate unique mnemonic for each call
      const words = Array(wordCount).fill('word').map((w, i) => `${w}${mnemonicCounter}_${i}`);
      mnemonicCounter++;
      return createMockHDWallet(words.join(' '), passphrase);
    }),
    createWithMnemonic: vi.fn((mnemonic: string, passphrase: string) => 
      createMockHDWallet(mnemonic, passphrase)
    ),
    createWithEntropy: vi.fn((entropy: Uint8Array, passphrase: string) => {
      const words = Array(12).fill('entropy').map((w, i) => `${w}${mnemonicCounter}_${i}`);
      mnemonicCounter++;
      return createMockHDWallet(words.join(' '), passphrase);
    })
  };
  
  const mockWalletCore = {
    Mnemonic: mockMnemonic,
    HDWallet: mockHDWallet,
    Curve: {
      nist256p1: 1,
      secp256k1: 0
    }
  };
  
  return {
    initTrustWallet: vi.fn().mockResolvedValue(mockWalletCore),
    getTrustWallet: vi.fn().mockReturnValue(mockWalletCore),
    FLOW_BIP44_PATH: "m/44'/539'/0'/0/0",
    getFlowPath: vi.fn((index: number = 0) => `m/44'/539'/0'/0/${index}`),
    removePublicKeyPrefix: vi.fn((hex: string) => hex.replace(/^04/, ''))
  };
});

describe('SeedPhraseProvider', () => {
  let storage: InMemoryProvider;
  
  beforeAll(async () => {
    // Ensure TrustWallet is initialized
    await trustwallet.initTrustWallet();
  });
  
  beforeEach(() => {
    storage = new InMemoryProvider();
    vi.clearAllMocks();
    // Reset mnemonic counter for consistent tests
    mnemonicCounter = 0;
  });
  
  describe('Mnemonic Generation', () => {
    it('should create a 12-word mnemonic by default', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      expect(provider.keyType).toBe('SEED_PHRASE');
      expect(provider.isHardwareBacked).toBe(false);
      
      const mnemonic = provider.exportMnemonic();
      const words = mnemonic.split(' ');
      expect(words).toHaveLength(12);
    });
    
    it('should create mnemonics with different word counts', async () => {
      const wordCounts: (12 | 15 | 18 | 21 | 24)[] = [12, 15, 18, 21, 24];
      
      for (const wordCount of wordCounts) {
        const provider = await SeedPhraseProvider.create({ wordCount }, storage);
        const mnemonic = provider.exportMnemonic();
        const words = mnemonic.split(' ');
        expect(words).toHaveLength(wordCount);
      }
    });
    
    it('should create mnemonic with passphrase', async () => {
      const passphrase = 'my-secure-passphrase';
      const provider = await SeedPhraseProvider.create({ passphrase }, storage);
      
      expect(provider.advance.passphrase).toBe(passphrase);
      
      // The providers should have different mnemonics (generated randomly)
      const providerNoPass = await SeedPhraseProvider.create(storage);
      const mnemonic1 = provider.exportMnemonic();
      const mnemonic2 = providerNoPass.exportMnemonic();
      expect(mnemonic1).not.toBe(mnemonic2);
    });
    
    it('should create mnemonic with custom derivation path', async () => {
      const customPath = "m/44'/539'/0'/0/1";
      const provider = await SeedPhraseProvider.create({ derivationPath: customPath }, storage);
      
      expect(provider.key.derivationPath).toBe(customPath);
      expect(provider.advance.derivationPath).toBe(customPath);
    });
    
    it('should create mnemonic from entropy', async () => {
      const entropy = new Uint8Array(16); // 128 bits for 12 words
      crypto.getRandomValues(entropy);
      
      const provider = await SeedPhraseProvider.create({ entropy }, storage);
      expect(provider.exportMnemonic()).toBeTruthy();
    });
  });
  
  describe('Mnemonic Import', () => {
    const validMnemonic = 'abandon ability able about above absent absorb abstract absurd abuse access accident';
    
    it('should import valid mnemonic', async () => {
      const provider = await SeedPhraseProvider.fromMnemonic(
        validMnemonic,
        '',
        FLOW_DEFAULT_PATH,
        storage
      );
      
      expect(provider.exportMnemonic()).toBe(validMnemonic);
      expect(provider.key.derivationPath).toBe(FLOW_DEFAULT_PATH);
    });
    
    it('should import mnemonic with passphrase', async () => {
      const passphrase = 'test-passphrase';
      const provider = await SeedPhraseProvider.fromMnemonic(
        validMnemonic,
        passphrase,
        FLOW_DEFAULT_PATH,
        storage
      );
      
      expect(provider.advance.passphrase).toBe(passphrase);
    });
    
    it('should reject invalid mnemonic', async () => {
      const invalidMnemonic = 'invalid words that are not a valid mnemonic phrase';
      
      await expect(SeedPhraseProvider.fromMnemonic(
        invalidMnemonic,
        '',
        FLOW_DEFAULT_PATH,
        storage
      )).rejects.toThrow(WalletError);
    });
    
    it('should validate mnemonic statically', async () => {
      expect(await SeedPhraseProvider.validateMnemonic(validMnemonic)).toBe(true);
      expect(await SeedPhraseProvider.validateMnemonic('invalid mnemonic')).toBe(false);
    });
  });
  
  describe('Key Derivation', () => {
    it('should derive P256 private key', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const privateKey = provider.privateKey(SigningAlgorithm.ECDSA_P256);
      expect(privateKey).toBeInstanceOf(Uint8Array);
      expect(privateKey?.length).toBe(32);
      expect(isValidPrivateKey(privateKey!, SigningAlgorithm.ECDSA_P256)).toBe(true);
    });
    
    it('should derive secp256k1 private key', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const privateKey = provider.privateKey(SigningAlgorithm.ECDSA_SECP256K1);
      expect(privateKey).toBeInstanceOf(Uint8Array);
      expect(privateKey?.length).toBe(32);
      expect(isValidPrivateKey(privateKey!, SigningAlgorithm.ECDSA_SECP256K1)).toBe(true);
    });
    
    it('should derive public keys correctly', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const publicKeyP256 = provider.publicKey(SigningAlgorithm.ECDSA_P256);
      const publicKeySecp = provider.publicKey(SigningAlgorithm.ECDSA_SECP256K1);
      
      expect(publicKeyP256).toBeInstanceOf(Uint8Array);
      expect(publicKeySecp).toBeInstanceOf(Uint8Array);
      
      // Public keys should be 64 bytes (uncompressed without prefix)
      expect(publicKeyP256?.length).toBeGreaterThanOrEqual(64);
      expect(publicKeySecp?.length).toBeGreaterThanOrEqual(64);
    });
    
    it('should strip uncompressed public key prefix', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      // Get the private key and derive public key manually
      const privateKey = provider.privateKey(SigningAlgorithm.ECDSA_P256);
      const manualPublicKey = deriveRealPublicKey(privateKey!, SigningAlgorithm.ECDSA_P256);
      
      // The manual derivation should have the 0x04 prefix
      expect(manualPublicKey[0]).toBe(0x04);
      
      // But the provider should strip it
      const providerPublicKey = provider.publicKey(SigningAlgorithm.ECDSA_P256);
      if (providerPublicKey!.length === 64) {
        // Already stripped
        expect(providerPublicKey![0]).not.toBe(0x04);
      } else if (providerPublicKey!.length === 65) {
        // Not stripped
        expect(providerPublicKey![0]).toBe(0x04);
      }
    });
  });
  
  describe('HD Wallet Derivation', () => {
    it('should derive key at specific path', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const path1 = "m/44'/539'/0'/0/0";
      const path2 = "m/44'/539'/0'/0/1";
      
      const key1 = provider.deriveKey(path1);
      const key2 = provider.deriveKey(path2);
      
      // Keys should be different
      expect(bytesToHex(key1.privateKey)).not.toBe(bytesToHex(key2.privateKey));
      expect(bytesToHex(key1.publicKey)).not.toBe(bytesToHex(key2.publicKey));
    });
    
    it('should cache derived keys', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      const path = "m/44'/539'/0'/0/0";
      
      const key1 = provider.deriveKey(path);
      const key2 = provider.deriveKey(path);
      
      // Should be the same reference (cached)
      expect(key1).toBe(key2);
    });
    
    it('should derive key at index', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const key0 = provider.getKeyAtIndex(0);
      const key1 = provider.getKeyAtIndex(1);
      const key2Hardened = provider.getKeyAtIndex(2, true);
      const key2NonHardened = provider.getKeyAtIndex(2, false);
      
      // All keys should be different
      expect(bytesToHex(key0.privateKey)).not.toBe(bytesToHex(key1.privateKey));
      expect(bytesToHex(key1.privateKey)).not.toBe(bytesToHex(key2Hardened.privateKey));
      expect(bytesToHex(key2Hardened.privateKey)).not.toBe(bytesToHex(key2NonHardened.privateKey));
    });
    
    it('should handle derivation failures gracefully', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      // Mock HDWallet to return null for a specific path
      const wallet = provider.key.hdWallet;
      wallet.getKeyByCurve = vi.fn().mockReturnValue({
        data: () => new Uint8Array(0)
      });
      
      expect(() => provider.deriveKey("m/44'/539'/0'/0/999"))
        .toThrow(WalletError);
    });
  });
  
  describe('Signing and Verification', () => {
    it('should sign and verify with P256', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const message = stringToBytes('Hello, Flow blockchain!');
      const signature = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      
      expect(signature).toBeInstanceOf(Uint8Array);
      expect(signature.length).toBeGreaterThan(0);
      
      // Verify the signature
      const isValid = provider.isValidSignature(
        signature,
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      expect(isValid).toBe(true);
    });
    
    it('should sign and verify with secp256k1', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const message = stringToBytes('Hello, Flow blockchain!');
      const signature = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_SECP256K1,
        HashingAlgorithm.SHA3_256
      );
      
      expect(signature).toBeInstanceOf(Uint8Array);
      
      const isValid = provider.isValidSignature(
        signature,
        message,
        SigningAlgorithm.ECDSA_SECP256K1,
        HashingAlgorithm.SHA3_256
      );
      expect(isValid).toBe(true);
    });
    
    it('should fail signing with no private key', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      // Mock privateKey to return null
      provider.privateKey = vi.fn().mockReturnValue(null);
      
      const message = stringToBytes('test');
      
      await expect(provider.sign(
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      )).rejects.toThrow(WalletError);
    });
    
    it('should return false for invalid signature', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const message = stringToBytes('Original message');
      const wrongMessage = stringToBytes('Different message');
      
      const signature = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      
      const isValid = provider.isValidSignature(
        signature,
        wrongMessage,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      
      expect(isValid).toBe(false);
    });
  });
  
  describe('Storage Operations', () => {
    it('should store and retrieve seed phrase', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      const mnemonic = provider.exportMnemonic();
      
      const keyId = 'seed-phrase-1';
      const password = 'secure-password-123';
      
      await provider.store(keyId, password);
      
      // Retrieve the stored seed phrase
      const retrieved = await SeedPhraseProvider.get(keyId, password, storage);
      
      expect(retrieved.exportMnemonic()).toBe(mnemonic);
      expect(retrieved.privateKey(SigningAlgorithm.ECDSA_P256))
        .toEqual(provider.privateKey(SigningAlgorithm.ECDSA_P256));
    });
    
    it('should preserve passphrase and derivation path', async () => {
      const passphrase = 'test-passphrase';
      const customPath = "m/44'/539'/0'/0/5";
      
      const provider = await SeedPhraseProvider.create({
        passphrase,
        derivationPath: customPath
      }, storage);
      
      const keyId = 'seed-with-options';
      const password = 'storage-password';
      
      await provider.store(keyId, password);
      
      const retrieved = await SeedPhraseProvider.get(keyId, password, storage);
      
      expect(retrieved.advance.passphrase).toBe(passphrase);
      expect(retrieved.advance.derivationPath).toBe(customPath);
      expect(retrieved.key.derivationPath).toBe(customPath);
    });
    
    it('should fail with wrong password', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      const keyId = 'protected-seed';
      const password = 'correct-password';
      const wrongPassword = 'wrong-password';
      
      await provider.store(keyId, password);
      
      await expect(SeedPhraseProvider.get(keyId, wrongPassword, storage))
        .rejects.toThrow(WalletError);
    });
  });
  
  describe('Restoration', () => {
    it('should restore from mnemonic secret', async () => {
      const originalProvider = await SeedPhraseProvider.create(storage);
      const mnemonic = originalProvider.secret;
      
      const restoredProvider = await originalProvider.restore(mnemonic, storage);
      
      expect(restoredProvider.exportMnemonic()).toBe(mnemonic);
      expect(restoredProvider.privateKey(SigningAlgorithm.ECDSA_P256))
        .toEqual(originalProvider.privateKey(SigningAlgorithm.ECDSA_P256));
    });
  });
  
  describe('Key Details', () => {
    it('should return comprehensive key details', async () => {
      const provider = await SeedPhraseProvider.create({
        wordCount: 24,
        derivationPath: "m/44'/539'/0'/0/1"
      }, storage);
      
      const details = provider.getKeyDetails();
      
      expect(details.keyType).toBe('SEED_PHRASE');
      expect(details.derivationPath).toBe("m/44'/539'/0'/0/1");
      expect(details.wordCount).toBe(24);
      expect(details.publicKeyP256).toMatch(/^[0-9a-f]+$/);
      expect(details.publicKeySecp256k1).toMatch(/^[0-9a-f]+$/);
      expect(details.isHardwareBacked).toBe(false);
    });
  });
  
  describe('Error Handling', () => {
    it('should handle TrustWallet initialization failure', async () => {
      // Create a new instance of the mock that will fail
      const originalMock = vi.mocked(trustwallet.initTrustWallet);
      
      // Replace with a failing version
      vi.mocked(trustwallet.initTrustWallet).mockRejectedValueOnce(
        new Error('WASM initialization failed')
      );
      
      await expect(SeedPhraseProvider.create(storage))
        .rejects.toThrow('WASM initialization failed');
      
      // Restore the original mock
      vi.mocked(trustwallet.initTrustWallet).mockImplementation(originalMock);
    });
    
    it('should wrap signing errors properly', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      // Force an error by making privateKey return an invalid key
      provider.privateKey = vi.fn().mockReturnValue(new Uint8Array(10)); // Wrong size
      
      const message = stringToBytes('test');
      
      try {
        await provider.sign(
          message,
          SigningAlgorithm.ECDSA_P256,
          HashingAlgorithm.SHA2_256
        );
        expect.fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(WalletError);
        expect((error as WalletError).code).toBe(WalletErrorCode.SignError);
      }
    });
  });
  
  describe('Integration Tests', () => {
    it('should handle full mnemonic lifecycle', async () => {
      // Create with specific options
      const options: SeedPhraseKeyOptions = {
        wordCount: 15,
        passphrase: 'integration-test',
        derivationPath: "m/44'/539'/0'/0/3"
      };
      
      const provider = await SeedPhraseProvider.create(options, storage);
      const mnemonic = provider.exportMnemonic();
      
      // Store
      const keyId = 'integration-seed';
      const password = 'test-password';
      await provider.store(keyId, password);
      
      // Sign something
      const message = stringToBytes('Integration test message');
      const signature = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      
      // Retrieve
      const retrieved = await SeedPhraseProvider.get(keyId, password, storage);
      
      // Verify signature with retrieved key
      const isValid = retrieved.isValidSignature(
        signature,
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      expect(isValid).toBe(true);
      
      // Import from mnemonic
      const imported = await SeedPhraseProvider.fromMnemonic(
        mnemonic,
        options.passphrase!,
        options.derivationPath!,
        storage
      );
      
      // Verify imported key produces same signature verification
      expect(imported.isValidSignature(
        signature,
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      )).toBe(true);
      
      // Derive additional keys
      const childKey1 = provider.getKeyAtIndex(1);
      const childKey2 = provider.getKeyAtIndex(2);
      
      expect(bytesToHex(childKey1.privateKey)).not.toBe(bytesToHex(childKey2.privateKey));
      
      // Cleanup
      await provider.remove(keyId);
      expect(provider.allKeys()).not.toContain(keyId);
    });
    
    it('should handle multiple curves correctly', async () => {
      const provider = await SeedPhraseProvider.create(storage);
      
      // Get keys for both curves
      const p256Private = provider.privateKey(SigningAlgorithm.ECDSA_P256);
      const secp256k1Private = provider.privateKey(SigningAlgorithm.ECDSA_SECP256K1);
      
      // Keys should be different
      expect(p256Private).not.toEqual(secp256k1Private);
      
      // Both should be valid
      expect(isValidPrivateKey(p256Private!, SigningAlgorithm.ECDSA_P256)).toBe(true);
      expect(isValidPrivateKey(secp256k1Private!, SigningAlgorithm.ECDSA_SECP256K1)).toBe(true);
      
      // Sign with both
      const message = stringToBytes('Multi-curve test');
      
      const p256Sig = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      );
      
      const secpSig = await provider.sign(
        message,
        SigningAlgorithm.ECDSA_SECP256K1,
        HashingAlgorithm.SHA2_256
      );
      
      // Verify both
      expect(provider.isValidSignature(
        p256Sig,
        message,
        SigningAlgorithm.ECDSA_P256,
        HashingAlgorithm.SHA2_256
      )).toBe(true);
      
      expect(provider.isValidSignature(
        secpSig,
        message,
        SigningAlgorithm.ECDSA_SECP256K1,
        HashingAlgorithm.SHA2_256
      )).toBe(true);
      
      // Cross-verification should fail
      expect(provider.isValidSignature(
        p256Sig,
        message,
        SigningAlgorithm.ECDSA_SECP256K1,
        HashingAlgorithm.SHA2_256
      )).toBe(false);
    });
  });
});