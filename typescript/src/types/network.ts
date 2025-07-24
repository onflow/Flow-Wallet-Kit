/**
 * Flow Wallet Kit - Network Types
 * 
 * This module defines types for Flow blockchain network interactions,
 * including chain configurations, key indexer responses, and account structures.
 */

import type { HashingAlgorithm, SigningAlgorithm } from './key.js';

/**
 * Flow blockchain network identifiers
 */
export enum ChainId {
  MAINNET = 'flow-mainnet',
  TESTNET = 'flow-testnet',
  CANARYNET = 'flow-canarynet',
  EMULATOR = 'flow-emulator'
}

/**
 * Configuration for a Flow network
 */
export interface ChainConfig {
  /**
   * The chain identifier
   */
  id: ChainId;
  
  /**
   * Human-readable name for the network
   */
  name: string;
  
  /**
   * RPC endpoint URL
   */
  rpcUrl: string;
  
  /**
   * Key indexer service URL (optional)
   */
  keyIndexerUrl?: string;
  
  /**
   * Whether this is a production network
   */
  isProduction: boolean;
}

/**
 * Predefined chain configurations
 */
export const CHAIN_CONFIGS: Record<ChainId, ChainConfig> = {
  [ChainId.MAINNET]: {
    id: ChainId.MAINNET,
    name: 'Flow Mainnet',
    rpcUrl: 'https://rest-mainnet.onflow.org',
    keyIndexerUrl: 'https://production.key-indexer.flow.com',
    isProduction: true
  },
  [ChainId.TESTNET]: {
    id: ChainId.TESTNET,
    name: 'Flow Testnet',
    rpcUrl: 'https://rest-testnet.onflow.org',
    keyIndexerUrl: 'https://staging.key-indexer.flow.com',
    isProduction: false
  },
  [ChainId.CANARYNET]: {
    id: ChainId.CANARYNET,
    name: 'Flow Canarynet',
    rpcUrl: 'https://rest-canarynet.onflow.org',
    isProduction: false
  },
  [ChainId.EMULATOR]: {
    id: ChainId.EMULATOR,
    name: 'Flow Emulator',
    rpcUrl: 'http://localhost:8888',
    isProduction: false
  }
};

/**
 * Account key information from Flow blockchain
 */
export interface AccountPublicKey {
  /**
   * Key index in the account
   */
  index: string;
  
  /**
   * Public key in hex format
   */
  publicKey: string;
  
  /**
   * Signature algorithm used by this key
   */
  signingAlgorithm: SigningAlgorithm;
  
  /**
   * Hash algorithm used by this key
   */
  hashingAlgorithm: HashingAlgorithm;
  
  /**
   * Key weight (1000 = full signing authority)
   */
  weight: string;
  
  /**
   * Whether the key has been revoked
   */
  revoked: boolean;
  
  /**
   * Sequence number for replay protection
   */
  sequenceNumber: string;
}

/**
 * Expandable fields for Flow accounts
 */
export interface AccountExpandable {
  /**
   * Whether to expand keys in the response
   */
  keys?: boolean;
  
  /**
   * Whether to expand contracts in the response
   */
  contracts?: boolean;
}

/**
 * Link information for Flow accounts
 */
export interface AccountLink {
  /**
   * Self-reference URL
   */
  self?: string;
}

/**
 * Flow blockchain account
 */
export interface FlowAccount {
  /**
   * Account address in hex format (with or without 0x prefix)
   */
  address: string;
  
  /**
   * Account balance in FLOW (as string to preserve precision)
   */
  balance: string;
  
  /**
   * Account keys
   */
  keys?: Set<AccountPublicKey>;
  
  /**
   * Smart contracts deployed to this account
   */
  contracts?: Record<string, string>;
  
  /**
   * Expandable field configuration
   */
  expandable?: AccountExpandable;
  
  /**
   * API links
   */
  links?: AccountLink | null;
}

/**
 * Response from the Flow key indexer service
 */
export interface KeyIndexerResponse {
  /**
   * The public key being queried
   */
  publicKey: string;
  
  /**
   * List of accounts associated with this public key
   */
  accounts: KeyIndexerAccount[];
}

/**
 * Account entry in the key indexer response
 */
export interface KeyIndexerAccount {
  /**
   * The Flow address in hex format
   */
  address: string;
  
  /**
   * The key index in the account
   */
  keyId: number;
  
  /**
   * The key weight (determines signing authority)
   */
  weight: number;
  
  /**
   * Signature algorithm identifier
   */
  sigAlgo: number;
  
  /**
   * Hash algorithm identifier
   */
  hashAlgo: number;
  
  /**
   * The signature algorithm used
   */
  signing: SigningAlgorithm;
  
  /**
   * The hash algorithm used
   */
  hashing: HashingAlgorithm;
  
  /**
   * Whether the key has been revoked
   */
  isRevoked: boolean;
}

/**
 * Network service for Flow blockchain interactions
 */
export interface NetworkService {
  /**
   * Find account information using the key indexer service
   * @param publicKey - The public key to search for
   * @param chainId - The Flow network to search on
   * @returns Key indexer response containing account information
   * @throws {Error} if the request fails
   */
  findAccount(publicKey: string, chainId: ChainId): Promise<KeyIndexerResponse>;
  
  /**
   * Find accounts associated with a public key
   * @param publicKey - The public key to search for
   * @param chainId - The Flow network to search on
   * @returns Array of accounts associated with the key
   * @throws {Error} if the request fails
   */
  findAccountByKey(publicKey: string, chainId: ChainId): Promise<KeyIndexerAccount[]>;
  
  /**
   * Find Flow accounts associated with a public key
   * @param publicKey - The public key to search for
   * @param chainId - The Flow network to search on
   * @returns Array of Flow accounts associated with the key
   * @throws {Error} if the request fails
   */
  findFlowAccountByKey(publicKey: string, chainId: ChainId): Promise<FlowAccount[]>;
  
  /**
   * Get account details directly from the Flow network
   * @param address - The Flow address to fetch
   * @param chainId - The Flow network to query
   * @returns Account details
   * @throws {Error} if the account cannot be fetched
   */
  getAccount(address: string, chainId: ChainId): Promise<FlowAccount>;
}