/**
 * Private key provider implementation
 * Supports both P256 and secp256k1 curves
 */

import { BaseKeyProtocol } from './KeyProtocol.js';
import {
  KeyType,
  SigningAlgorithm,
  HashingAlgorithm,
  PrivateKeyOptions,
  KeyProtocol as IKeyProtocol
} from '../types/key.js';
import type { StorageProtocol } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { bytesToHex, base64ToArrayBuffer, arrayBufferToBase64 } from '../utils/crypto.js';
import {
  generatePrivateKey,
  derivePublicKey,
  isValidPrivateKey,
  sign as curveSign,
  verify as curveVerify,
  privateKeyFromHex
} from './curves.js';

/**
 * Private key data structure
 */
interface PrivateKeyData {
  privateKey: Uint8Array;
  signAlgo: SigningAlgorithm;
}

/**
 * Private key provider for software-based key management
 */
export class PrivateKeyProvider extends BaseKeyProtocol<PrivateKeyData, Uint8Array, PrivateKeyOptions> {
  readonly keyType = KeyType.PRIVATE_KEY;
  readonly isHardwareBacked = false;
  
  private _key: PrivateKeyData;
  private _secret: Uint8Array;
  private _advance: PrivateKeyOptions;
  
  constructor(
    key: PrivateKeyData,
    secret: Uint8Array,
    advance: PrivateKeyOptions,
    storage: StorageProtocol
  ) {
    super(storage);
    this._key = key;
    this._secret = secret;
    this._advance = advance;
  }
  
  get key(): PrivateKeyData {
    return this._key;
  }
  
  get secret(): Uint8Array {
    return this._secret;
  }
  
  get advance(): PrivateKeyOptions {
    return this._advance;
  }
  
  /**
   * Create a new private key
   */
  async create(advance: PrivateKeyOptions, storage: StorageProtocol): Promise<IKeyProtocol<PrivateKeyData, Uint8Array, PrivateKeyOptions>>;
  async create(storage: StorageProtocol): Promise<IKeyProtocol<PrivateKeyData, Uint8Array, PrivateKeyOptions>>;
  async create(
    advanceOrStorage: PrivateKeyOptions | StorageProtocol,
    storage?: StorageProtocol
  ): Promise<IKeyProtocol<PrivateKeyData, Uint8Array, PrivateKeyOptions>> {
    let advance: PrivateKeyOptions;
    let storageToUse: StorageProtocol;
    
    if (storage) {
      advance = advanceOrStorage as PrivateKeyOptions;
      storageToUse = storage;
    } else {
      advance = { signAlgo: SigningAlgorithm.ECDSA_P256 };
      storageToUse = advanceOrStorage as StorageProtocol;
    }
    
    const signAlgo = advance.signAlgo || SigningAlgorithm.ECDSA_P256;
    
    // Generate new private key
    const privateKey = advance.entropy ? 
      this.generateFromEntropy(advance.entropy, signAlgo) :
      generatePrivateKey(signAlgo);
    
    const key: PrivateKeyData = {
      privateKey,
      signAlgo
    };
    
    return new PrivateKeyProvider(key, privateKey, advance, storageToUse);
  }
  
  /**
   * Create a private key provider from various input formats
   */
  static async fromPrivateKey(
    privateKey: string | Uint8Array,
    signAlgo: SigningAlgorithm,
    storage: StorageProtocol
  ): Promise<PrivateKeyProvider> {
    let keyBytes: Uint8Array;
    
    if (typeof privateKey === 'string') {
      // Try to parse as hex first
      if (privateKey.match(/^[0-9a-fA-F]+$/)) {
        keyBytes = privateKeyFromHex(privateKey);
      } else {
        // Try base64
        try {
          const buffer = base64ToArrayBuffer(privateKey);
          keyBytes = new Uint8Array(buffer);
        } catch {
          throw new WalletError(
            WalletErrorCode.InvalidPrivateKey,
            'Invalid private key format. Expected hex or base64 string.'
          );
        }
      }
    } else {
      keyBytes = privateKey;
    }
    
    // Validate the private key
    if (!isValidPrivateKey(keyBytes, signAlgo)) {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        'Invalid private key for the specified curve'
      );
    }
    
    const key: PrivateKeyData = {
      privateKey: keyBytes,
      signAlgo
    };
    
    const advance: PrivateKeyOptions = { signAlgo };
    
    return new PrivateKeyProvider(key, keyBytes, advance, storage);
  }
  
  /**
   * Restore from secret (private key bytes)
   */
  async restore(
    secret: Uint8Array,
    storage: StorageProtocol
  ): Promise<IKeyProtocol<PrivateKeyData, Uint8Array, PrivateKeyOptions>> {
    // Try both curves to determine which one the key belongs to
    let signAlgo: SigningAlgorithm;
    
    if (isValidPrivateKey(secret, SigningAlgorithm.ECDSA_P256)) {
      signAlgo = SigningAlgorithm.ECDSA_P256;
    } else if (isValidPrivateKey(secret, SigningAlgorithm.ECDSA_SECP256K1)) {
      signAlgo = SigningAlgorithm.ECDSA_SECP256K1;
    } else {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        'Invalid private key for both P256 and secp256k1 curves'
      );
    }
    
    const key: PrivateKeyData = {
      privateKey: secret,
      signAlgo
    };
    
    const advance: PrivateKeyOptions = { signAlgo };
    
    return new PrivateKeyProvider(key, secret, advance, storage);
  }
  
  /**
   * Get public key for a signature algorithm
   */
  publicKey(signAlgo: SigningAlgorithm): Uint8Array | null {
    if (signAlgo !== this._key.signAlgo) {
      return null;
    }
    
    return derivePublicKey(this._key.privateKey, signAlgo);
  }
  
  /**
   * Get private key for a signature algorithm
   */
  privateKey(signAlgo: SigningAlgorithm): Uint8Array | null {
    if (signAlgo !== this._key.signAlgo) {
      return null;
    }
    
    return this._key.privateKey;
  }
  
  /**
   * Sign data
   */
  async sign(
    data: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): Promise<Uint8Array> {
    if (signAlgo !== this._key.signAlgo) {
      throw new WalletError(
        WalletErrorCode.InvalidSignatureAlgorithm,
        `Key does not support ${signAlgo}. It uses ${this._key.signAlgo}`
      );
    }
    
    try {
      return curveSign(data, this._key.privateKey, signAlgo, hashAlgo, false);
    } catch (error) {
      throw new WalletError(
        WalletErrorCode.SignError,
        `Failed to sign: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
  
  /**
   * Verify a signature
   */
  isValidSignature(
    signature: Uint8Array,
    message: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): boolean {
    if (signAlgo !== this._key.signAlgo) {
      return false;
    }
    
    const publicKey = this.publicKey(signAlgo);
    if (!publicKey) {
      return false;
    }
    
    return curveVerify(signature, message, publicKey, signAlgo, hashAlgo, false);
  }
  
  /**
   * Get storage key prefix
   */
  protected getKeyPrefix(): string {
    return 'flow_wallet_kit:private_key:';
  }
  
  /**
   * Serialize secret for storage
   */
  protected serializeSecret(): Uint8Array {
    // Include the signature algorithm in the serialized data
    const signAlgoByte = this._key.signAlgo === SigningAlgorithm.ECDSA_P256 ? 0 : 1;
    const result = new Uint8Array(1 + this._key.privateKey.length);
    result[0] = signAlgoByte;
    result.set(this._key.privateKey, 1);
    return result;
  }
  
  /**
   * Deserialize secret from storage
   */
  protected deserializeSecret(data: Uint8Array): Uint8Array {
    if (data.length < 33) { // 1 byte for algo + 32 bytes for key
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        'Invalid serialized private key data'
      );
    }
    
    const signAlgoByte = data[0];
    const privateKey = data.slice(1);
    
    // Update the key data with the stored algorithm
    this._key.signAlgo = signAlgoByte === 0 ? 
      SigningAlgorithm.ECDSA_P256 : 
      SigningAlgorithm.ECDSA_SECP256K1;
    
    return privateKey;
  }
  
  /**
   * Generate private key from entropy
   */
  private generateFromEntropy(entropy: Uint8Array, signAlgo: SigningAlgorithm): Uint8Array {
    if (entropy.length < 32) {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        'Entropy must be at least 32 bytes'
      );
    }
    
    // Take first 32 bytes as private key
    const privateKey = entropy.slice(0, 32);
    
    if (!isValidPrivateKey(privateKey, signAlgo)) {
      throw new WalletError(
        WalletErrorCode.InvalidPrivateKey,
        'Generated private key is invalid for the specified curve'
      );
    }
    
    return privateKey;
  }
  
  /**
   * Export private key as hex string
   */
  exportAsHex(): string {
    return bytesToHex(this._key.privateKey);
  }
  
  /**
   * Export private key as base64 string
   */
  exportAsBase64(): string {
    return arrayBufferToBase64(this._key.privateKey.buffer);
  }
  
  /**
   * Get key details
   */
  getKeyDetails() {
    return {
      keyType: this.keyType,
      signAlgo: this._key.signAlgo,
      publicKey: bytesToHex(this.publicKey(this._key.signAlgo)!),
      isHardwareBacked: this.isHardwareBacked
    };
  }
  
  /**
   * Static create method
   */
  static async create(advance: PrivateKeyOptions, storage: StorageProtocol): Promise<PrivateKeyProvider>;
  static async create(storage: StorageProtocol): Promise<PrivateKeyProvider>;
  static async create(
    advanceOrStorage: PrivateKeyOptions | StorageProtocol,
    storage?: StorageProtocol
  ): Promise<PrivateKeyProvider> {
    const instance = new PrivateKeyProvider(
      { privateKey: new Uint8Array(0), signAlgo: SigningAlgorithm.ECDSA_P256 },
      new Uint8Array(0),
      {},
      storage || (advanceOrStorage as StorageProtocol)
    );
    return instance.create(advanceOrStorage as any, storage as any) as Promise<PrivateKeyProvider>;
  }
  
  /**
   * Static method to retrieve a stored private key
   */
  static async get(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<PrivateKeyProvider> {
    // Create a temporary instance to access protected methods
    const temp = new PrivateKeyProvider(
      { privateKey: new Uint8Array(0), signAlgo: SigningAlgorithm.ECDSA_P256 },
      new Uint8Array(0),
      {},
      storage
    );
    
    const { secret, advance } = await temp.loadFromStorage(id, password);
    
    // The secret includes the algorithm byte
    const signAlgoByte = secret[0];
    const privateKey = secret.slice(1);
    const signAlgo = signAlgoByte === 0 ? 
      SigningAlgorithm.ECDSA_P256 : 
      SigningAlgorithm.ECDSA_SECP256K1;
    
    const key: PrivateKeyData = {
      privateKey,
      signAlgo
    };
    
    const provider = new PrivateKeyProvider(key, privateKey, advance, storage);
    provider._id = id;
    
    return provider;
  }
}