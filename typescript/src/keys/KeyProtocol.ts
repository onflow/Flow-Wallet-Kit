/**
 * Base implementation of the KeyProtocol interface
 * Provides common functionality for all key types
 */

import type { 
  KeyProtocol as IKeyProtocol, 
  KeyType, 
  SigningAlgorithm, 
  HashingAlgorithm,
  KeyCreationOptions
} from '../types/key.js';
import type { StorageProtocol } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { PasswordEncryption, EncryptionAlgorithm } from '../crypto/PasswordEncryption.js';

/**
 * Abstract base class implementing common KeyProtocol functionality
 */
export abstract class BaseKeyProtocol<
  TKey = unknown,
  TSecret = Uint8Array,
  TAdvance extends KeyCreationOptions = KeyCreationOptions
> implements IKeyProtocol<TKey, TSecret, TAdvance> {
  abstract readonly key: TKey;
  abstract readonly secret: TSecret;
  abstract readonly advance: TAdvance;
  abstract readonly keyType: KeyType;
  abstract readonly isHardwareBacked: boolean;
  
  protected _storage: StorageProtocol;
  protected _id: string = '';
  
  constructor(storage: StorageProtocol) {
    this._storage = storage;
  }
  
  get storage(): StorageProtocol {
    return this._storage;
  }
  
  set storage(storage: StorageProtocol) {
    this._storage = storage;
  }
  
  get id(): string {
    return this._id;
  }
  
  // Abstract methods that must be implemented by subclasses
  abstract publicKey(signAlgo: SigningAlgorithm): Uint8Array | null;
  abstract privateKey(signAlgo: SigningAlgorithm): Uint8Array | null;
  abstract sign(
    data: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): Promise<Uint8Array>;
  abstract isValidSignature(
    signature: Uint8Array,
    message: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): boolean;
  
  // Methods with overloading for create
  abstract create(advance: TAdvance, storage: StorageProtocol): Promise<IKeyProtocol<TKey, TSecret, TAdvance>>;
  abstract create(storage: StorageProtocol): Promise<IKeyProtocol<TKey, TSecret, TAdvance>>;
  
  abstract restore(secret: TSecret, storage: StorageProtocol): Promise<IKeyProtocol<TKey, TSecret, TAdvance>>;
  
  /**
   * Create and store a new key
   */
  async createAndStore(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<IKeyProtocol<TKey, TSecret, TAdvance>> {
    // Create the key
    const keyInstance = await this.create(storage);
    
    // Store it
    await keyInstance.store(id, password);
    
    return keyInstance;
  }
  
  /**
   * Store the key with encryption
   */
  async store(id: string, password: string): Promise<void> {
    if (!this.storage) {
      throw new WalletError(
        WalletErrorCode.NoImplement,
        'Storage not configured'
      );
    }
    
    // Generate encryption metadata
    const metadata = {
      keyType: this.keyType,
      timestamp: new Date().toISOString(),
      advance: this.advance
    };
    
    // Encrypt the secret
    const secretData = this.serializeSecret();
    // Convert Uint8Array to array for JSON serialization
    const encryptedVault = await PasswordEncryption.encrypt(
      password,
      Array.from(secretData),
      {
        algorithm: EncryptionAlgorithm.AES_GCM,
        iterations: 100000
      }
    );
    
    // Store the encrypted data
    const keyData = {
      metadata,
      encryptedVault
    };
    
    const keyDataStr = JSON.stringify(keyData);
    const keyDataBytes = new TextEncoder().encode(keyDataStr);
    await this.storage.set(this.getStorageKey(id), keyDataBytes);
    this._id = id;
  }
  
  /**
   * Remove a stored key
   */
  async remove(id: string): Promise<void> {
    if (!this.storage) {
      throw new WalletError(
        WalletErrorCode.NoImplement,
        'Storage not configured'
      );
    }
    
    await this.storage.remove(this.getStorageKey(id));
    
    if (this._id === id) {
      this._id = '';
    }
  }
  
  /**
   * Get all stored key identifiers
   */
  allKeys(): string[] {
    if (!this.storage) {
      return [];
    }
    
    const keyPrefix = this.getKeyPrefix();
    const allKeys = Object.keys(this.storage);
    
    return allKeys
      .filter(key => key.startsWith(keyPrefix))
      .map(key => key.substring(keyPrefix.length));
  }
  
  /**
   * Get the storage key for a given identifier
   */
  protected getStorageKey(id: string): string {
    return `${this.getKeyPrefix()}${id}`;
  }
  
  /**
   * Get the key prefix for this key type
   */
  protected abstract getKeyPrefix(): string;
  
  /**
   * Serialize the secret for storage
   */
  protected abstract serializeSecret(): Uint8Array;
  
  /**
   * Deserialize the secret from storage
   */
  protected abstract deserializeSecret(data: Uint8Array): TSecret;
  
  /**
   * Load a key from storage
   */
  protected async loadFromStorage(
    id: string,
    password: string
  ): Promise<{ secret: TSecret; advance: TAdvance }> {
    if (!this.storage) {
      throw new WalletError(
        WalletErrorCode.NoImplement,
        'Storage not configured'
      );
    }
    
    const keyDataBytes = await this.storage.get(this.getStorageKey(id));
    if (!keyDataBytes) {
      throw new WalletError(
        WalletErrorCode.EmptyKey,
        `Key not found: ${id}`
      );
    }
    
    try {
      const keyDataStr = new TextDecoder().decode(keyDataBytes);
      const keyData = JSON.parse(keyDataStr);
      
      // Verify key type matches
      if (keyData.metadata.keyType !== this.keyType) {
        throw new WalletError(
          WalletErrorCode.InvalidWalletType,
          `Key type mismatch: expected ${this.keyType}, got ${keyData.metadata.keyType}`
        );
      }
      
      // Decrypt the secret
      const decryptedData = await PasswordEncryption.decrypt(
        password,
        keyData.encryptedVault
      );
      
      // The decrypted data is an array of numbers that we need to convert back to Uint8Array
      const secretArray = decryptedData as number[];
      const secret = this.deserializeSecret(new Uint8Array(secretArray));
      const advance = keyData.metadata.advance as TAdvance;
      
      return { secret, advance };
    } catch (error) {
      if (error instanceof WalletError) {
        throw error;
      }
      throw new WalletError(
        WalletErrorCode.DecryptionFailed,
        `Failed to load key: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }
}