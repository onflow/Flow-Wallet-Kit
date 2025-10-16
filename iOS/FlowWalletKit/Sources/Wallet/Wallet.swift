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
import WalletCore

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
    
    @Published
    public var eoaAddress: Set<String>?

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
        try? loadCachedAccount()
        refreshEOAAddresses()
    }
    
    /// Create a new account on the Flow blockchain
    /// - Throws: FWKError.noImplementError as this feature is not yet implemented
    public func createAccount() async throws {
        // TODO: Implement me
        throw FWKError.noImplementError
    }

    // MARK: - Public Methods
    
    /// Load cached all accounts associated with this wallet if available
    public func loadCachedAccount() throws {
        do {
            if let model = try loadCache() {
                flowAccounts = model
                accounts = [Flow.ChainID: [Account]]()
                for network in model.keys {
                    if let acc = model[network] {
                        accounts?[network] = acc.compactMap {
                            Account(account: $0, chainID: network, key: type.key, securityDelegate: securityDelegate)
                        }
                    }
                }
            }
        } catch FWKError.cacheDecodeFailed {
            //TODO: Handle no cache log
            try? deleteCache()
            throw FWKError.cacheDecodeFailed
        }
    }

    /// Fetch all accounts associated with this wallet
    /// This method performs the following steps:
    /// 1. Loads cached accounts if available
    /// 2. Fetches fresh account data from networks
    /// 3. Updates the cache with new data
    public func fetchAccount() async throws {
        try await _ = fetchAllNetworkAccounts()
        try cache()
    }
    
    /// Fetch accounts created by a specific transaction
    /// - Parameters:
    ///   - txId: Transaction ID that created the account
    ///   - network: Network where the transaction was executed
    /// - Throws: FWKError.emptyCreatedAddress if no account was created in the transaction
    public func fetchAccountsByCreationTxId(txId: Flow.ID, network: Flow.ChainID) async throws -> Account {
    
        if !networks.contains(network) {
            addNetwork(network)
        }
        flow.configure(chainID: network)
        let result = try await flow.accessAPI.getTransactionResultById(id: txId)
        guard let address = result.getCreatedAddress() else {
            throw FWKError.emptyCreatedAddress
        }

        if let existAccount = getAccount(by: address, network: network) {
            return existAccount
        }

        let account = try await flow.accessAPI.getAccountAtLatestBlock(address: .init(address))
        
        let newAccount = Account(account: account, chainID: network, key: type.key)
        addAccount(account, network: network)

        return newAccount
    }

    /// Find the corresponding Account by address and network
    /// - Parameters:
    ///   - address: The account address (hex string) to search for
    ///   - network: The Flow network to search in
    /// - Returns: The matching Account object, or nil if not found
    public func getAccount(by address: String, network: Flow.ChainID) -> Account? {
        // Check if there are accounts for the given network
        guard let accountList = accounts?[network] else {
            return nil
        }
        // Case-insensitive search for the address
        return accountList.first { $0.hexAddr.lowercased() == address.lowercased() }
    }

    /// add account to network
    func addAccount(_ account: Flow.Account, network: Flow.ChainID) {
        guard getAccount(by: account.address.hex, network: network) == nil else {
            return
        }

        var accountTmp: [Account] = accounts?[network] ?? []
        accountTmp.append(Account(account: account, chainID: network, key: type.key))
        accounts?[network] = accountTmp

        var flowAccountsTmp = flowAccounts?[network] ?? []
        flowAccountsTmp.append(account)
        flowAccounts?[network] = flowAccountsTmp
    }

    /// remove account by address
    func removeAccount(_ address: String, network: Flow.ChainID) {
        var accountTmp: [Account] = accounts?[network] ?? []
        accountTmp.removeAll { $0.hexAddr.lowercased() == address.lowercased() }
        accounts?[network] = accountTmp

        var flowAccountsTmp = flowAccounts?[network] ?? []
        flowAccountsTmp.removeAll { $0.address.hex.lowercased() == address.lowercased() }
        flowAccounts?[network] = flowAccountsTmp
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
    
    public func getEOAAccount(indexes: [UInt32]? = nil) throws -> [AnyAddress] {
        let key = try resolveEthereumKey()
        let normalizedIndexes = indexes?.isEmpty == false ? indexes! : [0]
        let addresses = try deriveEOAAddresses(from: key, indexes: normalizedIndexes)
        updateEOAAddressCache(with: addresses)
        return addresses
    }
    
    /// Returns the Ethereum address for the given derivation index (default index 0).
    public func ethAddress(index: UInt32 = 0) throws -> String {
        try resolveEthereumKey().ethAddress(index: index)
    }
    
    /// Signs a pre-hashed 32-byte digest with the wallet's Ethereum key.
    public func ethSignDigest(_ digest: Data, index: UInt32 = 0) throws -> Data {
        try resolveEthereumKey().ethSign(digest: digest, index: index)
    }
    
    /// EIP-191 personal sign (`personal_sign`). Prefixes the payload and hashes with keccak256 before signing.
    public func ethSignPersonalMessage(_ message: Data, index: UInt32 = 0) throws -> Data {
        let key = try resolveEthereumKey()
        let prefixString = "\u{19}Ethereum Signed Message:\n\(message.count)"
        guard let prefix = prefixString.data(using: .utf8) else {
            throw FWKError.invalidEthereumMessage
        }
        var payload = Data()
        payload.append(prefix)
        payload.append(message)
        let digest = Hash.keccak256(data: payload)
        return try key.ethSign(digest: digest, index: index)
    }
    
    /// Convenience alias matching RPC naming (`eth_sign` / `personal_sign`).
    public func ethSignPersonalData(_ data: Data, index: UInt32 = 0) throws -> Data {
        try ethSignPersonalMessage(data, index: index)
    }
    
    /// Signs structured data (EIP-712). Expects a JSON payload matching wallet-core's schema.
    public func ethSignTypedData(json: String, index: UInt32 = 0) throws -> Data {
        let key = try resolveEthereumKey()
        let digest = EthereumAbi.encodeTyped(messageJson: json)
        guard digest.count == 32 else {
            throw FWKError.invalidEthereumTypedData
        }
        return try key.ethSign(digest: digest, index: index)
    }
    
    /// Signs an Ethereum transaction using WalletCore's AnySigner pipeline.
    public func ethSignTransaction(_ input: EthereumSigningInput, index: UInt32 = 0) throws -> EthereumSigningOutput {
        let key = try resolveEthereumKey()
        var signingInput = input
        signingInput.privateKey = try key.ethPrivateKey(index: index)
        defer { signingInput.privateKey = Data() }
        return AnySigner.sign(input: signingInput, coin: .ethereum)
    }
    
    private func refreshEOAAddresses() {
        guard let key = try? resolveEthereumKey() else {
            eoaAddress = nil
            return
        }

        do {
            let addresses = try deriveEOAAddresses(from: key, indexes: [0])
            updateEOAAddressCache(with: addresses)
        } catch {
            eoaAddress = nil
        }
    }
    
    private func deriveEOAAddresses(from key: EthereumKeyProtocol,
                                    indexes: [UInt32]) throws -> [AnyAddress] {
        var results: [AnyAddress] = []
        for index in indexes {
            let addressString = try key.ethAddress(index: index)
            guard let address = AnyAddress(string: addressString, coin: .ethereum) else {
                throw FWKError.invaildEVMAddress
            }
            results.append(address)
        }
        return results
    }
    
    private func updateEOAAddressCache(with addresses: [AnyAddress]) {
        if addresses.isEmpty {
            eoaAddress = nil
            return
        }
        let set = Set(addresses.map { $0.description })
        eoaAddress = set.isEmpty ? nil : set
    }

    private func resolveEthereumKey() throws -> EthereumKeyProtocol {
        guard case let .key(rawKey) = type,
              let ethereumKey = rawKey as? EthereumKeyProtocol else {
            throw FWKError.unsupportedEthereumKey
        }
        return ethereumKey
    }
}
