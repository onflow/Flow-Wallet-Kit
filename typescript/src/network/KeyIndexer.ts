/**
 * Key Indexer client for account discovery
 */

import { 
  ChainId, 
  KeyIndexerResponse, 
  FlowAccount,
  AccountPublicKey,
  SigningAlgorithm 
} from '../types/network.js';
import { WalletError, WalletErrorCode } from '../utils/errors.js';
import { NetworkManager } from './NetworkManager.js';
import { hexToBytes } from '../utils/crypto.js';

/**
 * Key Indexer client for discovering accounts by public key
 */
export class KeyIndexer {
  private networkManager: NetworkManager;
  private cache: Map<string, KeyIndexerResponse>;

  constructor() {
    this.networkManager = NetworkManager.getInstance();
    this.cache = new Map();
  }

  /**
   * Find accounts by public key
   */
  async findAccountsByPublicKey(
    publicKey: string,
    chainId: ChainId
  ): Promise<KeyIndexerResponse> {
    const cacheKey = `${chainId}:${publicKey}`;
    
    // Check cache first
    const cached = this.cache.get(cacheKey);
    if (cached) {
      return cached;
    }

    try {
      const keyIndexerUrl = this.networkManager.getKeyIndexerUrl(chainId);
      const url = `${keyIndexerUrl}/key/${publicKey}`;

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new WalletError(
          WalletErrorCode.KeyIndexerRequestFailed,
          `Key Indexer request failed: ${response.status} ${response.statusText}`
        );
      }

      const data = await response.json() as KeyIndexerResponse;
      
      // Cache the result
      this.cache.set(cacheKey, data);
      
      return data;
    } catch (error) {
      if (error instanceof WalletError) {
        throw error;
      }
      
      throw new WalletError(
        WalletErrorCode.KeyIndexerRequestFailed,
        `Failed to query Key Indexer: ${error instanceof Error ? error.message : 'Unknown error'}`,
        error
      );
    }
  }

  /**
   * Find Flow accounts by public key
   */
  async findFlowAccountsByPublicKey(
    publicKey: string,
    chainId: ChainId
  ): Promise<FlowAccount[]> {
    const response = await this.findAccountsByPublicKey(publicKey, chainId);
    
    // Convert Key Indexer response to Flow accounts
    return response.accounts.map(account => this.toFlowAccount(account, publicKey));
  }

  /**
   * Find accounts with full weight keys (1000+)
   */
  async findAccountsWithFullWeight(
    publicKey: string,
    chainId: ChainId
  ): Promise<FlowAccount[]> {
    const accounts = await this.findFlowAccountsByPublicKey(publicKey, chainId);
    
    // Filter for accounts where the key has full weight (1000)
    return accounts.filter(account => 
      account.keys.some(key => 
        key.publicKey === publicKey && key.weight >= 1000
      )
    );
  }

  /**
   * Find accounts by multiple public keys
   */
  async findAccountsByMultipleKeys(
    publicKeys: string[],
    chainId: ChainId
  ): Promise<Map<string, FlowAccount[]>> {
    const results = new Map<string, FlowAccount[]>();
    
    // Query in parallel
    const promises = publicKeys.map(async publicKey => {
      try {
        const accounts = await this.findFlowAccountsByPublicKey(publicKey, chainId);
        results.set(publicKey, accounts);
      } catch (error) {
        // Store empty array for failed queries
        results.set(publicKey, []);
      }
    });
    
    await Promise.all(promises);
    
    return results;
  }

  /**
   * Clear cache
   */
  clearCache(): void {
    this.cache.clear();
  }

  /**
   * Clear cache for a specific chain
   */
  clearChainCache(chainId: ChainId): void {
    const keysToDelete: string[] = [];
    
    for (const key of this.cache.keys()) {
      if (key.startsWith(`${chainId}:`)) {
        keysToDelete.push(key);
      }
    }
    
    keysToDelete.forEach(key => this.cache.delete(key));
  }

  /**
   * Convert Key Indexer account to Flow account
   */
  private toFlowAccount(
    indexerAccount: KeyIndexerResponse['accounts'][0],
    publicKey: string
  ): FlowAccount {
    // Map signing algorithm
    const signAlgo = indexerAccount.signAlgorithm === 'ECDSA_P256' 
      ? SigningAlgorithm.ECDSA_P256 
      : SigningAlgorithm.ECDSA_SECP256K1;

    // Create account key
    const accountKey: AccountPublicKey = {
      index: indexerAccount.keyId,
      publicKey: publicKey,
      signAlgo,
      hashAlgo: indexerAccount.hashAlgorithm,
      weight: indexerAccount.weight,
      sequenceNumber: 0, // Not provided by Key Indexer
      revoked: false // Assume not revoked if returned by indexer
    };

    // Create Flow account
    return {
      address: indexerAccount.address,
      balance: 0, // Balance not provided by Key Indexer
      code: '', // Code not provided by Key Indexer
      keys: [accountKey],
      contracts: {} // Contracts not provided by Key Indexer
    };
  }

  /**
   * Validate public key format
   */
  private validatePublicKey(publicKey: string): void {
    // Basic validation - should be hex string
    if (!/^[0-9a-fA-F]+$/.test(publicKey)) {
      throw new WalletError(
        WalletErrorCode.InvalidPublicKey,
        'Public key must be a hex string'
      );
    }
    
    // Check length (P256 and secp256k1 uncompressed keys are 64 bytes = 128 hex chars)
    if (publicKey.length !== 128) {
      throw new WalletError(
        WalletErrorCode.InvalidPublicKey,
        'Public key must be 64 bytes (128 hex characters)'
      );
    }
  }

  /**
   * Get cache size
   */
  getCacheSize(): number {
    return this.cache.size;
  }

  /**
   * Check if a result is cached
   */
  isCached(publicKey: string, chainId: ChainId): boolean {
    const cacheKey = `${chainId}:${publicKey}`;
    return this.cache.has(cacheKey);
  }
}