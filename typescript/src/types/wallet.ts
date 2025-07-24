/**
 * Flow Wallet Kit - Wallet Types
 * 
 * This module defines the core types and interfaces for Flow wallets,
 * including wallet types, account management, and child account structures.
 */

import type { StorageProtocol, Cacheable } from './storage.js';
import type { KeyProtocol, SigningAlgorithm, HashingAlgorithm } from './key.js';
import type { ChainId, FlowAccount, AccountPublicKey } from './network.js';

/**
 * Security check delegate for authentication
 */
export interface SecurityCheckDelegate {
  /**
   * Perform security verification (e.g., biometric, PIN)
   * @returns Whether the security check passed
   */
  verify(): Promise<boolean>;
}

/**
 * Token balance information
 */
export interface TokenBalance {
  /**
   * Token contract address
   */
  contractAddress: string;
  
  /**
   * Token symbol
   */
  symbol: string;
  
  /**
   * Token name
   */
  name: string;
  
  /**
   * Token decimals
   */
  decimals: number;
  
  /**
   * Balance amount (as string to preserve precision)
   */
  balance: string;
  
  /**
   * Token icon URL (optional)
   */
  icon?: string;
}

/**
 * Child account information
 */
export interface ChildAccount {
  /**
   * Child account address
   */
  address: string;
  
  /**
   * Network the account exists on
   */
  network: ChainId;
  
  /**
   * Account name
   */
  name: string;
  
  /**
   * Account description
   */
  description?: string;
  
  /**
   * Account icon URL
   */
  icon?: string;
}

/**
 * Cadence Owned Account (COA) for EVM compatibility
 */
export interface COA {
  /**
   * EVM address (20 bytes, hex format)
   */
  address: string;
  
  /**
   * Network the COA exists on
   */
  network: ChainId;
  
  /**
   * EVM balance in wei (as string to preserve precision)
   */
  balance?: string;
  
  /**
   * Nonce for EVM transactions
   */
  nonce?: number;
}

/**
 * Account cache data
 */
export interface AccountCache {
  /**
   * Cached child accounts
   */
  childs: ChildAccount[] | null;
  
  /**
   * Cached COA information
   */
  coa: COA | null;
}

/**
 * Flow account with wallet capabilities
 */
export interface Account extends Cacheable<AccountCache> {
  /**
   * The underlying Flow account
   */
  readonly account: FlowAccount;
  
  /**
   * Chain ID where the account exists
   */
  readonly chainID: ChainId;
  
  /**
   * Associated key for signing (null for watch-only accounts)
   */
  readonly key: KeyProtocol | null;
  
  /**
   * Loading state
   */
  readonly isLoading: boolean;
  
  /**
   * Token balances
   */
  readonly tokenBalances: readonly TokenBalance[];
  
  /**
   * Child accounts
   */
  readonly childs: ChildAccount[] | null;
  
  /**
   * Cadence Owned Account for EVM
   */
  readonly coa: COA | null;
  
  /**
   * Whether the account has child accounts
   */
  readonly hasChild: boolean;
  
  /**
   * Whether the account has a COA
   */
  readonly hasCOA: boolean;
  
  /**
   * Whether the account has any linked accounts
   */
  readonly hasLinkedAccounts: boolean;
  
  /**
   * Whether the account can sign transactions
   */
  readonly canSign: boolean;
  
  /**
   * Account address in hex format
   */
  readonly hexAddr: string;
  
  /**
   * Account address (alias for compatibility)
   */
  readonly address: string;
  
  /**
   * Key index for signing
   */
  readonly keyIndex: number;
  
  /**
   * Full weight key (if available)
   */
  readonly fullWeightKey: AccountPublicKey | undefined;
  
  /**
   * Whether the account has a full weight key
   */
  readonly hasFullWeightKey: boolean;
  
  /**
   * Find keys in the account that match the wallet's key
   * @returns Matching account keys
   */
  findKeyInAccount(): AccountPublicKey[];
  
  /**
   * Load linked accounts (child accounts and COA)
   * @throws {Error} if loading fails
   */
  loadLinkedAccounts(): Promise<void>;
  
  /**
   * Fetch child accounts
   * @returns Child accounts
   * @throws {Error} if fetching fails
   */
  fetchChild(): Promise<ChildAccount[]>;
  
  /**
   * Fetch COA information
   * @returns COA information
   * @throws {Error} if fetching fails
   */
  fetchVM(): Promise<COA>;
  
  /**
   * Sign a transaction
   * @param transaction - Transaction to sign
   * @param bytes - Transaction bytes to sign
   * @returns Signature
   * @throws {Error} if signing fails or security check fails
   */
  sign(transaction: unknown, bytes: Uint8Array): Promise<Uint8Array>;
}

/**
 * Enum representing different wallet types
 */
export enum WalletType {
  /**
   * Watch-only wallet (no signing capability)
   */
  WATCH = 'WATCH',
  
  /**
   * Key-based wallet (can sign transactions)
   */
  KEY = 'KEY',
  
  /**
   * Proxy wallet (e.g., hardware wallet)
   */
  PROXY = 'PROXY'
}

/**
 * Base interface for all wallet types
 */
export interface Wallet {
  /**
   * The wallet type
   */
  readonly type: WalletType;
  
  /**
   * Accounts organized by chain
   */
  readonly accounts: ReadonlyMap<ChainId, readonly Account[]>;
  
  /**
   * Networks this wallet is connected to
   */
  readonly networks: ReadonlySet<ChainId>;
  
  /**
   * Storage implementation
   */
  readonly storage: StorageProtocol;
  
  /**
   * Loading state
   */
  readonly isLoading: boolean;
  
  /**
   * Security delegate for authentication
   */
  readonly securityDelegate?: SecurityCheckDelegate;
  
  /**
   * Add a network to the wallet
   * @param network - Network to add
   * @throws {Error} if operation fails
   */
  addNetwork(network: ChainId): Promise<void>;
  
  /**
   * Remove a network from the wallet
   * @param network - Network to remove
   * @throws {Error} if operation fails
   */
  removeNetwork(network: ChainId): Promise<void>;
  
  /**
   * Add an account to the wallet
   * @param account - Account to add
   * @throws {Error} if operation fails
   */
  addAccount(account: Account): Promise<void>;
  
  /**
   * Remove an account from the wallet
   * @param address - Address of account to remove
   * @throws {Error} if operation fails
   */
  removeAccount(address: string): Promise<void>;
  
  /**
   * Get an account by address
   * @param address - Account address
   * @returns Account if found, null otherwise
   */
  getAccount(address: string): Promise<Account | null>;
  
  /**
   * Refresh all accounts
   * @throws {Error} if operation fails
   */
  refreshAccounts(): Promise<void>;
  
  /**
   * Fetch accounts from all networks
   * @throws {Error} if operation fails
   */
  fetchAccounts(): Promise<void>;
  
  /**
   * Fetch accounts for a specific network
   * @param network - Network to fetch accounts for
   * @returns Flow accounts
   * @throws {Error} if operation fails
   */
  fetchAccountsForNetwork(network: ChainId): Promise<FlowAccount[]>;
  
  /**
   * Fetch account by address from the Flow network
   * @param address - Account address
   * @param network - Network where the account exists
   * @throws {Error} if operation fails
   */
  fetchAccountByAddress(address: string, network: ChainId): Promise<void>;
}

/**
 * Watch-only wallet that can observe addresses without signing capability
 */
export interface WatchWallet extends Wallet {
  readonly type: WalletType.WATCH;
  
  /**
   * Addresses being watched
   */
  readonly addresses: readonly string[];
  
  /**
   * Add an address to watch
   * @param address - Address to watch
   * @param network - Network where the address exists
   * @throws {Error} if operation fails
   */
  addAddress(address: string, network: ChainId): Promise<void>;
  
  /**
   * Remove a watched address
   * @param address - Address to stop watching
   * @throws {Error} if operation fails
   */
  removeAddress(address: string): Promise<void>;
}

/**
 * Key-based wallet with full signing capabilities
 */
export interface KeyWallet extends Wallet {
  readonly type: WalletType.KEY;
  
  /**
   * The cryptographic key backing this wallet
   */
  readonly key: KeyProtocol;
  
  /**
   * Create a new account on the specified network
   * @param network - Network to create account on
   * @returns Created account
   * @throws {Error} if account creation fails
   */
  createAccount(network: ChainId): Promise<Account>;
  
  /**
   * Sign data with the wallet's key
   * @param data - Data to sign
   * @param signAlgo - Signature algorithm
   * @param hashAlgo - Hash algorithm
   * @returns Signature
   * @throws {Error} if signing fails
   */
  sign(
    data: Uint8Array,
    signAlgo: SigningAlgorithm,
    hashAlgo: HashingAlgorithm
  ): Promise<Uint8Array>;
}

/**
 * Wallet factory for creating different wallet types
 */
export interface WalletFactory {
  /**
   * Create a key-based wallet
   * @param key - Cryptographic key
   * @param networks - Initial networks
   * @param storage - Storage implementation
   * @param securityDelegate - Optional security delegate
   * @returns Key wallet
   */
  createKeyWallet(
    key: KeyProtocol,
    networks: ChainId[],
    storage: StorageProtocol,
    securityDelegate?: SecurityCheckDelegate
  ): KeyWallet;
  
  /**
   * Create a watch-only wallet
   * @param addresses - Addresses to watch
   * @param networks - Initial networks
   * @param storage - Storage implementation
   * @returns Watch wallet
   */
  createWatchWallet(
    addresses: string[],
    networks: ChainId[],
    storage: StorageProtocol
  ): WatchWallet;
}

/**
 * Wallet configuration options
 */
export interface WalletConfig {
  /**
   * Initial networks to connect to
   */
  networks?: ChainId[];
  
  /**
   * Storage implementation
   */
  storage: StorageProtocol;
  
  /**
   * Security delegate for authentication
   */
  securityDelegate?: SecurityCheckDelegate;
  
  /**
   * Whether to enable caching
   */
  enableCache?: boolean;
  
  /**
   * Cache expiration time in seconds
   */
  cacheExpiration?: number;
}