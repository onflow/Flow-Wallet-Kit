/**
 * BIP39 seed phrase provider implementation
 * Supports HD wallet derivation with Flow's default path
 */

import { initTrustWallet } from '../utils/trustwallet.js';
import type { WalletCore } from '@trustwallet/wallet-core';
import { BaseKeyProtocol } from './KeyProtocol.js';
import {
  KeyType,
  SigningAlgorithm,
  HashingAlgorithm,
  SeedPhraseKeyOptions,
  KeyProtocol as IKeyProtocol
} from '../types/key.js';
import type { StorageProtocol } from '../types/storage.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { stringToBytes, bytesToString, bytesToHex } from '../utils/crypto.js';
import {
  derivePublicKey,
  sign as curveSign,
  verify as curveVerify,
  isValidPrivateKey
} from './curves.js';

/**
 * Default Flow derivation path
 */
export const FLOW_DEFAULT_PATH = "m/44'/539'/0'/0/0";

/**
 * Seed phrase data structure
 */
interface SeedPhraseData {
  mnemonic: string;
  hdWallet: any; // HDWallet instance from TrustWallet
  derivationPath: string;
}

// TrustWallet core components (initialized lazily)
let walletCore: WalletCore;
let Mnemonic: any;
let HDWallet: any;
let Curve: any;

// Ensure WASM is initialized and cache references
async function ensureWasmInitialized() {
  if (!walletCore) {
    walletCore = await initTrustWallet();
    Mnemonic = walletCore.Mnemonic;
    HDWallet = walletCore.HDWallet;
    Curve = walletCore.Curve;
  }
}

/**
 * BIP39 seed phrase provider for HD wallet functionality
 */
export class SeedPhraseProvider extends BaseKeyProtocol<SeedPhraseData, string, SeedPhraseKeyOptions> {
  readonly keyType = KeyType.SEED_PHRASE;
  readonly isHardwareBacked = false;
  
  private _key: SeedPhraseData;
  private _secret: string;
  private _advance: SeedPhraseKeyOptions;
  private _derivedKeys: Map<string, { privateKey: Uint8Array; publicKey: Uint8Array }> = new Map();
  
  constructor(
    key: SeedPhraseData,
    secret: string,
    advance: SeedPhraseKeyOptions,
    storage: StorageProtocol
  ) {
    super(storage);
    this._key = key;
    this._secret = secret;
    this._advance = advance;
  }
  
  get key(): SeedPhraseData {
    return this._key;
  }
  
  get secret(): string {
    return this._secret;
  }
  
  get advance(): SeedPhraseKeyOptions {
    return this._advance;
  }
  
  /**
   * Create a new seed phrase
   */
  async create(advance: SeedPhraseKeyOptions, storage: StorageProtocol): Promise<IKeyProtocol<SeedPhraseData, string, SeedPhraseKeyOptions>>;
  async create(storage: StorageProtocol): Promise<IKeyProtocol<SeedPhraseData, string, SeedPhraseKeyOptions>>;
  async create(
    advanceOrStorage: SeedPhraseKeyOptions | StorageProtocol,
    storage?: StorageProtocol
  ): Promise<IKeyProtocol<SeedPhraseData, string, SeedPhraseKeyOptions>> {
    let advance: SeedPhraseKeyOptions;
    let storageToUse: StorageProtocol;
    
    if (storage) {
      advance = advanceOrStorage as SeedPhraseKeyOptions;
      storageToUse = storage;
    } else {
      advance = {};
      storageToUse = advanceOrStorage as StorageProtocol;
    }
    
    // Ensure WASM is initialized
    await ensureWasmInitialized();
    
    // Generate mnemonic
    const strength = this.getStrengthFromWordCount(advance.wordCount || 12);
    let mnemonic: string;
    
    if (advance.entropy) {
      // Create wallet with entropy
      const hdWalletTemp = HDWallet.createWithEntropy(advance.entropy, advance.passphrase || '');
      mnemonic = hdWalletTemp.mnemonic();
      hdWalletTemp.delete();
    } else {
      // Create wallet with strength
      const hdWalletTemp = HDWallet.create(strength, advance.passphrase || '');
      mnemonic = hdWalletTemp.mnemonic();
      hdWalletTemp.delete();
    }
    
    // Create HD wallet
    const hdWallet = HDWallet.createWithMnemonic(mnemonic, advance.passphrase || '');
    
    // Verify wallet creation
    const derivationPath = advance.derivationPath || FLOW_DEFAULT_PATH;
    const testPrivateKey = this.derivePrivateKeyFromWallet(hdWallet, derivationPath, SigningAlgorithm.ECDSA_P256);
    
    if (!testPrivateKey || testPrivateKey.length === 0) {
      throw new WalletError(
        WalletErrorCode.InitHDWalletFailed,
        'Failed to derive private key from seed'
      );
    }
    
    const key: SeedPhraseData = {
      mnemonic,
      hdWallet,
      derivationPath
    };
    
    return new SeedPhraseProvider(key, mnemonic, advance, storageToUse);
  }
  
  /**
   * Create from existing mnemonic
   */
  static async fromMnemonic(
    mnemonic: string,
    passphrase: string = '',
    derivationPath: string = FLOW_DEFAULT_PATH,
    storage: StorageProtocol
  ): Promise<SeedPhraseProvider> {
    // Ensure WASM is initialized
    await ensureWasmInitialized();
    
    // Validate mnemonic
    if (!Mnemonic.isValid(mnemonic)) {
      throw new WalletError(
        WalletErrorCode.InvalidMnemonic,
        'Invalid mnemonic phrase'
      );
    }
    
    // Create HD wallet
    const hdWallet = HDWallet.createWithMnemonic(mnemonic, passphrase);
    
    // Verify wallet creation by testing key derivation
    const provider = new SeedPhraseProvider(
      { mnemonic, hdWallet, derivationPath },
      mnemonic,
      { wordCount: mnemonic.split(' ').length as 12 | 15 | 18 | 21 | 24, passphrase, derivationPath },
      storage
    );
    
    const testPrivateKey = provider.privateKey(SigningAlgorithm.ECDSA_P256);
    if (!testPrivateKey) {
      throw new WalletError(
        WalletErrorCode.InitHDWalletFailed,
        'Failed to derive private key from seed'
      );
    }
    
    return provider;
  }
  
  /**
   * Restore from secret (mnemonic)
   */
  async restore(
    secret: string,
    storage: StorageProtocol
  ): Promise<IKeyProtocol<SeedPhraseData, string, SeedPhraseKeyOptions>> {
    return SeedPhraseProvider.fromMnemonic(secret, '', FLOW_DEFAULT_PATH, storage);
  }
  
  /**
   * Get public key for a signature algorithm
   */
  publicKey(signAlgo: SigningAlgorithm): Uint8Array | null {
    const privateKey = this.privateKey(signAlgo);
    if (!privateKey) {
      return null;
    }
    
    const publicKey = derivePublicKey(privateKey, signAlgo);
    
    // TrustWallet returns uncompressed public keys with '04' prefix
    // Remove it if present to match Flow's expected format
    if (publicKey.length === 65 && publicKey[0] === 0x04) {
      return publicKey.slice(1);
    }
    
    return publicKey;
  }
  
  /**
   * Get private key for a signature algorithm
   */
  privateKey(signAlgo: SigningAlgorithm): Uint8Array | null {
    try {
      return this.derivePrivateKeyFromWallet(this._key.hdWallet, this._key.derivationPath, signAlgo);
    } catch {
      return null;
    }
  }
  
  /**
   * Derive private key from HDWallet for a specific curve
   */
  private derivePrivateKeyFromWallet(
    hdWallet: any, // HDWallet instance
    path: string,
    signAlgo: SigningAlgorithm
  ): Uint8Array | null {
    const curve = signAlgo === SigningAlgorithm.ECDSA_P256 ? Curve.nist256p1 : Curve.secp256k1;
    const privateKeyData = hdWallet.getKeyByCurve(curve, path);
    
    if (!privateKeyData || privateKeyData.data().length === 0) {
      return null;
    }
    
    const privateKeyBytes = new Uint8Array(privateKeyData.data());
    
    // Validate that the private key works with the requested curve
    if (!isValidPrivateKey(privateKeyBytes, signAlgo)) {
      return null;
    }
    
    return privateKeyBytes;
  }
  
  /**
   * Sign data
   */
  async sign(
    data: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): Promise<Uint8Array> {
    const privateKey = this.privateKey(signAlgo);
    if (!privateKey) {
      throw new WalletError(
        WalletErrorCode.EmptySignKey,
        `No private key available for ${signAlgo}`
      );
    }
    
    try {
      return curveSign(data, privateKey, signAlgo, hashAlgo, false);
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
    const publicKey = this.publicKey(signAlgo);
    if (!publicKey) {
      return false;
    }
    
    return curveVerify(signature, message, publicKey, signAlgo, hashAlgo, false);
  }
  
  /**
   * Derive a key at a specific path
   */
  deriveKey(path: string): { privateKey: Uint8Array; publicKey: Uint8Array } {
    // Check cache first
    if (this._derivedKeys.has(path)) {
      return this._derivedKeys.get(path)!;
    }
    
    // Derive for both curves and use the one that works
    let privateKey: Uint8Array | null = null;
    let publicKey: Uint8Array | null = null;
    
    // Try P256 first (Flow's primary curve)
    privateKey = this.derivePrivateKeyFromWallet(this._key.hdWallet, path, SigningAlgorithm.ECDSA_P256);
    if (privateKey) {
      publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_P256);
    } else {
      // Fallback to secp256k1
      privateKey = this.derivePrivateKeyFromWallet(this._key.hdWallet, path, SigningAlgorithm.ECDSA_SECP256K1);
      if (privateKey) {
        publicKey = derivePublicKey(privateKey, SigningAlgorithm.ECDSA_SECP256K1);
      }
    }
    
    if (!privateKey || !publicKey) {
      throw new WalletError(
        WalletErrorCode.InitHDWalletFailed,
        `Failed to derive key at path: ${path}`
      );
    }
    
    const keyPair = { privateKey, publicKey };
    
    // Cache the derived key
    this._derivedKeys.set(path, keyPair);
    
    return keyPair;
  }
  
  /**
   * Get key at index (for account derivation)
   */
  getKeyAtIndex(index: number, hardened: boolean = true): { privateKey: Uint8Array; publicKey: Uint8Array } {
    const basePath = this._key.derivationPath.replace(/\/\d+'?$/, '');
    const path = `${basePath}/${index}${hardened ? "'" : ''}`;
    return this.deriveKey(path);
  }
  
  /**
   * Get storage key prefix
   */
  protected getKeyPrefix(): string {
    return 'flow_wallet_kit:seed_phrase:';
  }
  
  /**
   * Serialize secret for storage
   */
  protected serializeSecret(): Uint8Array {
    // Store mnemonic + metadata as JSON
    const data = {
      mnemonic: this._key.mnemonic,
      passphrase: this._advance.passphrase || '',
      derivationPath: this._key.derivationPath
    };
    
    return stringToBytes(JSON.stringify(data));
  }
  
  /**
   * Deserialize secret from storage
   */
  protected deserializeSecret(data: Uint8Array): string {
    const jsonStr = bytesToString(data);
    const parsed = JSON.parse(jsonStr);
    
    // Update advance options with stored values
    this._advance.passphrase = parsed.passphrase;
    this._advance.derivationPath = parsed.derivationPath;
    
    return parsed.mnemonic;
  }
  
  /**
   * Get strength from word count
   */
  private getStrengthFromWordCount(wordCount: 12 | 15 | 18 | 21 | 24): number {
    const strengthMap: Record<number, number> = {
      12: 128,
      15: 160,
      18: 192,
      21: 224,
      24: 256
    };
    
    return strengthMap[wordCount] || 128;
  }
  
  
  /**
   * Validate a mnemonic phrase
   */
  static async validateMnemonic(mnemonic: string): Promise<boolean> {
    await ensureWasmInitialized();
    return Mnemonic.isValid(mnemonic);
  }
  
  /**
   * Get mnemonic word list
   */
  static async getWordlist(): Promise<string[]> {
    await ensureWasmInitialized();
    // TrustWallet doesn't expose wordlist directly, return standard BIP39 English wordlist
    // This is a limitation but maintains API compatibility
    return [];
  }
  
  /**
   * Export mnemonic phrase
   */
  exportMnemonic(): string {
    return this._key.mnemonic;
  }
  
  /**
   * Get extended public key
   */
  getExtendedPublicKey(): string {
    // TrustWallet doesn't expose extended public key directly
    // Return a placeholder or implement if needed
    return 'xpub-not-available-with-trustwallet';
  }
  
  /**
   * Get key details
   */
  getKeyDetails() {
    const privateKey = this.privateKey(SigningAlgorithm.ECDSA_P256);
    const publicKeyP256 = privateKey ? this.publicKey(SigningAlgorithm.ECDSA_P256) : null;
    const publicKeySecp = privateKey ? this.publicKey(SigningAlgorithm.ECDSA_SECP256K1) : null;
    
    return {
      keyType: this.keyType,
      derivationPath: this._key.derivationPath,
      wordCount: this._key.mnemonic.split(' ').length,
      publicKeyP256: publicKeyP256 ? bytesToHex(publicKeyP256) : null,
      publicKeySecp256k1: publicKeySecp ? bytesToHex(publicKeySecp) : null,
      extendedPublicKey: this.getExtendedPublicKey(),
      isHardwareBacked: this.isHardwareBacked
    };
  }
  
  /**
   * Static create method
   */
  static async create(advance: SeedPhraseKeyOptions, storage: StorageProtocol): Promise<SeedPhraseProvider>;
  static async create(storage: StorageProtocol): Promise<SeedPhraseProvider>;
  static async create(
    advanceOrStorage: SeedPhraseKeyOptions | StorageProtocol,
    storage?: StorageProtocol
  ): Promise<SeedPhraseProvider> {
    const instance = new SeedPhraseProvider(
      {
        mnemonic: '',
        hdWallet: null as any, // Will be created in create method
        derivationPath: FLOW_DEFAULT_PATH
      },
      '',
      {},
      storage || (advanceOrStorage as StorageProtocol)
    );
    return instance.create(advanceOrStorage as any, storage as any) as Promise<SeedPhraseProvider>;
  }
  
  /**
   * Static method to retrieve a stored seed phrase
   */
  static async get(
    id: string,
    password: string,
    storage: StorageProtocol
  ): Promise<SeedPhraseProvider> {
    // Create a temporary instance to access protected methods
    const temp = new SeedPhraseProvider(
      {
        mnemonic: '',
        hdWallet: null as any, // Will be created in create method
        derivationPath: FLOW_DEFAULT_PATH
      },
      '',
      {},
      storage
    );
    
    const { secret, advance } = await temp.loadFromStorage(id, password);
    
    // The secret is already the mnemonic string (deserialized in loadFromStorage)
    // We need to get the passphrase and derivationPath from advance
    const provider = await SeedPhraseProvider.fromMnemonic(
      secret,
      advance.passphrase || '',
      advance.derivationPath || FLOW_DEFAULT_PATH,
      storage
    );
    
    provider._id = id;
    provider._advance = advance;
    
    return provider;
  }
}