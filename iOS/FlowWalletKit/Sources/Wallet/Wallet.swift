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
public class Wallet: ObservableObject, Cacheable {
    // MARK: - Constants
    
    /// Prefix used for caching wallet data in storage
    static let cachePrefix: String = "Wallets"

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
    
    // MARK: - Cacheable Protocol Implementation
    
    /// The type of data being cached for the wallet
    public typealias CachedData = [Flow.ChainID: [Flow.Account]]
    
    /// Storage mechanism used for caching
    public var storage: StorageProtocol {
        cacheStorage
    }
    
    /// Storage mechanism used for caching
    private(set) var cacheStorage: StorageProtocol = FileSystemStorage()
    
    /// Data to be cached
    public var cachedData: CachedData? {
        flowAccounts
    }

    /// Unique identifier for caching wallet data
    /// Combines the cache prefix with the wallet's type-specific ID
    public var cacheId: String {
        [Wallet.cachePrefix, type.id].joined(separator: "-")
    }
    
    public var securityDelegate: SecurityCheckDelegate?

    // MARK: - Initialization

    /// Initialize a new wallet
    /// - Parameters:
    ///   - type: The type of wallet (key or watch)
    ///   - networks: Set of networks to manage (defaults to mainnet and testnet)
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

    // MARK: - Public Methods

    /// Fetch all accounts associated with this wallet
    /// This method performs the following steps:
    /// 1. Loads cached accounts if available
    /// 2. Fetches fresh account data from networks
    /// 3. Updates the cache with new data
    public func fetchAccount() async throws {
        print("DDDDDDDDDDDD ====> \(type.id)")
        do {
            if let model = try loadCache() {
                print("FFFFFFFFFFFF ====> \(type.id)")
                flowAccounts = model
                accounts = [Flow.ChainID: [Account]]()
                for network in model.keys {
                    if let acc = model[network] {
                        accounts?[network] = acc.compactMap { Account(account: $0, chainID: network, key: type.key, securityDelegate: securityDelegate) }
                    }
                }
            }
        } catch WalletError.cacheDecodeFailed{
            //TODO: Handle no cache log
//            print("BBBBBB ====> \(type.id) - \(#error.localizedDescription)")
        }
        
        print("DDDDDDDDDDDD 1111 ====> \(type.id)")
        
        try await _ = fetchAllNetworkAccounts()
        
        print("DDDDDDDDDDDD 2222 ====> \(type.id)")
        
        do {
            try cache()
            print("CCCCCCCCCCC ====> \(type.id) - Cache saved")
        } catch {
            //TODO: Handle no cache log
            print("BBBBBBAAAAAA ====> \(type.id) - \(error.localizedDescription)")
        }
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
    public func fetchAllNetworkAccounts() async throws -> [Flow.ChainID: [Account]] {
        var flowAccounts = [Flow.ChainID: [Flow.Account]]()
        var networkAccounts = [Flow.ChainID: [Account]]()
        
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
    /// - Throws: WalletError.invaildWalletType if wallet type is invalid
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
            throw WalletError.invaildWalletType
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

        // Combine results from both parallel operations
        return try await p256KeyAccounts + secp256k1KeyAccounts
    }

}
