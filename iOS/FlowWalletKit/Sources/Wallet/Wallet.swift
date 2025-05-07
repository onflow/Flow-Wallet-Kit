/// FlowWalletKit - A Swift SDK for Flow blockchain wallet management
///
/// This module provides comprehensive wallet functionality for the Flow blockchain, including:
/// - Key-based wallets: Wallets backed by cryptographic keys (Secure Enclave, Seed Phrase, Private Key)
/// - Watch-only wallets: Read-only wallets for monitoring addresses without signing capability
/// - Multi-network account management: Support for mainnet, testnet, and other Flow networks
/// - Account caching: Efficient local storage of account data
/// - Parallel network operations: Concurrent fetching of account data across networks
///
/// Example usage:
/// ```swift
/// // Create a key-based wallet
/// let key = try SeedPhraseKey.create()
/// let wallet = Wallet(type: .key(key))
///
/// // Fetch accounts across all networks
/// try await wallet.fetchAccount()
///
/// // Access accounts on mainnet
/// if let mainnetAccounts = wallet.accounts?[.mainnet] {
///     // Work with mainnet accounts
/// }
/// ```

import Flow
import Foundation

// MARK: - Wallet Implementation

/// Main wallet class that manages Flow accounts across different networks
/// This class provides functionality for:
/// - Managing accounts across multiple Flow networks
/// - Fetching account data from networks
/// - Caching account information
/// - Supporting both key-based and watch-only wallets
public class Wallet: ObservableObject {
    
    static let fullWeightThreshold = 1000

    // MARK: - Properties
    
    /// The type of wallet (key-based or watch-only)
    /// This determines the wallet's capabilities (signing vs. watch-only)
    public let type: WalletType
    
    /// Set of Flow networks this wallet is active on
    /// By default includes mainnet and testnet
    public var networks: Set<Flow.ChainID>

    /// Published property containing accounts for each network
    /// Key: Flow.ChainID (network identifier)
    /// Value: Array of Account objects for that network
    @Published
    public var accounts: [Flow.ChainID: [Account]]? = nil

    /// Raw Flow accounts data, used for caching
    /// This property stores the underlying Flow.Account objects
    public var flowAccounts: [Flow.ChainID: [Flow.Account]]?
    
    /// Storage mechanism used for caching wallet data
    private(set) var cacheStorage: StorageProtocol = FileSystemStorage()
    
    /// Delegate for handling security checks before signing operations
    public var securityDelegate: SecurityCheckDelegate?
    
    /// Indicates whether the wallet is currently loading data
    @Published
    public var isLoading: Bool = false

    // MARK: - Initialization

    /// Initialize a new wallet
    /// - Parameters:
    ///   - type: The type of wallet (key or watch)
    ///   - networks: Set of networks to manage (defaults to mainnet and testnet)
    ///   - cacheStorage: Optional custom storage mechanism for caching
    /// Example:
    /// ```swift
    /// // Create a key-based wallet for mainnet only
    /// let wallet = Wallet(type: .key(myKey), networks: [.mainnet])
    /// ```
    public init(type: WalletType,
                networks: Set<Flow.ChainID> = [.mainnet, .testnet],
                cacheStorage: StorageProtocol? = nil) {
        self.type = type
        self.networks = networks
        if let cacheStorage {
            self.cacheStorage = cacheStorage
        }
    }
    
    /// Create a new account on the Flow blockchain
    /// - Throws: FWKError.noImplementError as this feature is not yet implemented
    public func createAccount() async throws {
        // TODO: Implement me
        throw FWKError.noImplementError
    }

    // MARK: - Public Methods

    /// Fetch all accounts associated with this wallet
    /// This method performs the following steps:
    /// 1. Loads cached accounts if available
    /// 2. Fetches fresh account data from networks
    /// 3. Updates the cache with new data
    public func fetchAccount() async throws {
        do {
            if let model = try loadCache() {
                flowAccounts = model
                accounts = [Flow.ChainID: [Account]]()
                for network in model.keys {
                    if let acc = model[network] {
                        accounts?[network] = acc.compactMap { Account(account: $0, chainID: network, key: type.key, securityDelegate: securityDelegate) }
                    }
                }
            }
        } catch FWKError.cacheDecodeFailed {
            //TODO: Handle no cache log
            try? deleteCache()
        }
        try await _ = fetchAllNetworkAccounts()
        try cache()
    }
    
    /// Fetch accounts created by a specific transaction
    /// - Parameters:
    ///   - txId: Transaction ID that created the account
    ///   - network: Network where the transaction was executed
    /// - Throws: FWKError.emptyCreatedAddress if no account was created in the transaction
    public func fetchAccountsByCreationTxId(txId: Flow.ID, network: Flow.ChainID) async throws {
        if !networks.contains(network) {
            addNetwork(network)
        }
        flow.configure(chainID: network)
        let result = try await flow.accessAPI.getTransactionResultById(id: txId)
        guard let address = result.getCreatedAddress() else {
            throw FWKError.emptyCreatedAddress
        }
        let account = try await flow.accessAPI.getAccountAtLatestBlock(address: .init(address))
        
        accounts = [network: [Account(account: account, chainID: network, key: type.key)]]
        self.flowAccounts = [network: [account]]
    }

    /// Add a new network to manage
    /// - Parameter network: The Flow network to add
    /// Example:
    /// ```swift
    /// wallet.addNetwork(.testnet) // Add testnet to managed networks
    /// ```
    public func addNetwork(_ network: Flow.ChainID) {
        networks.insert(network)
    }

    /// Fetch accounts from all configured networks in parallel
    /// This method efficiently retrieves account information by:
    /// - Running parallel network requests for each configured network
    /// - Combining results as they arrive
    /// - Updating both raw and processed account data
    /// - Returns: Dictionary mapping networks to their accounts
    /// - Throws: Error if fetching fails
    public func fetchAllNetworkAccounts() async throws -> [Flow.ChainID: [Account]] {
        var flowAccounts = [Flow.ChainID: [Flow.Account]]()
        var networkAccounts = [Flow.ChainID: [Account]]()
        
        isLoading = true
        defer {
            isLoading = false
        }
        
        // Fetch accounts from all networks in parallel using task groups
        try await withThrowingTaskGroup(of: (Flow.ChainID, [Flow.Account]).self) { group in
            // Start parallel tasks for each network
            for network in networks {
                group.addTask {
                    if let accounts = try? await self.account(chainID: network) {
                        return (network, accounts)
                    }
                    return (network, [])
                }
            }
            
            // Collect results as they complete
            for try await (network, accounts) in group {
                if !accounts.isEmpty {
                    flowAccounts[network] = accounts
                    networkAccounts[network] = accounts.compactMap { Account(account: $0, chainID: network, key: type.key) }
                }
            }
        }
        
        accounts = networkAccounts
        self.flowAccounts = flowAccounts
        return networkAccounts
    }

    /// Fetch accounts for a specific network
    /// - Parameters:
    ///   - chainID: The network to fetch accounts from
    /// - Returns: Array of Flow accounts
    /// - Throws: FWKError.invaildWalletType if wallet type is invalid
    ///
    /// For key-based wallets, this method:
    /// - Fetches accounts associated with both P256 and SECP256k1 public keys
    /// - Performs fetches in parallel for better performance
    /// For watch-only wallets:
    /// - Retrieves the account at the watched address
    public func account(chainID: Flow.ChainID) async throws -> [Flow.Account] {
        // Handle watch-only wallets
        guard case let .key(key) = type else {
            if case let .watch(address) = type {
                return [try await flow.getAccountAtLatestBlock(address: address)]
            }
            throw FWKError.invaildWalletType
        }

        // Parallel fetch for P256 accounts
        async let p256KeyAccounts: [Flow.Account] = {
            if let p256Key = key.publicKey(signAlgo: .ECDSA_P256)?.hexString {
                return try await Network.findFlowAccountByKey(publicKey: p256Key, chainID: chainID)
            }
            return []
        }()

        // Parallel fetch for SECP256k1 accounts
        async let secp256k1KeyAccounts: [Flow.Account] = {
            if let secp256k1Key = key.publicKey(signAlgo: .ECDSA_SECP256k1)?.hexString {
                return try await Network.findFlowAccountByKey(publicKey: secp256k1Key, chainID: chainID)
            }
            return []
        }()

        let accountList = try await p256KeyAccounts + secp256k1KeyAccounts
        // Combine results from both parallel operations
        return accountList.filter{ $0.keys.hasSignleFullWeightKey }
    }
}
