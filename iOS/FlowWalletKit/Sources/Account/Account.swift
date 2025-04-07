/// FlowWalletKit - Account Management
///
/// This module provides account management functionality for Flow blockchain accounts.
/// It handles account operations, key management, and transaction signing.

import Flow
import Foundation

/// Represents a Flow blockchain account with signing capabilities
public class Account: ObservableObject, Cacheable {
    // MARK: - Properties
    
    /// Child accounts associated with this account
    @Published
    public var childs: [ChildAccount]?

    /// Whether this account has child accounts
    public var hasChild: Bool {
        !(childs?.isEmpty ?? true)
    }

    /// Virtual machine accounts associated with this account
//    public var vm: [any FlowVMProtocol]?
    @Published
    public var coa: COA?

    /// Whether this account has VM accounts
    public var hasCOA: Bool {
        !(coa == nil)
    }

    /// Whether this account can sign transactions
    public var canSign: Bool {
        !(key == nil)
    }

    /// The underlying Flow account
    public let account: Flow.Account
    
    // MARK: - Cacheable Protocol Implementation
    
    /// The type of data being cached for the account
    public typealias CachedData = AccountCache
    
    /// Storage mechanism used for caching
    public var storage: StorageProtocol {
        guard let key = key else {
            fatalError("Storage only available for accounts with keys")
        }
        return key.storage
    }
    
    /// Data to be cached
    public var cachedData: CachedData? {
        AccountCache(
            childs: childs,
            coa: coa
        )
    }
    
    /// Unique identifier for caching account data
    public var cacheId: String {
        ["Account", chainID.name, account.address.hex].joined(separator: "-")
    }
    
    // MARK: - Cache Data Structure
    
    /// Structure representing cacheable account data
    public struct AccountCache: Codable {
        let childs: [ChildAccount]?
        let coa: COA?
    }
    
    
    // MARK: - Full Weight Key
    
    /// First available full weight key
    public var fullWeightKey: Flow.AccountKey? {
        fullWeightKeys.first
    }
    
    /// Whether this account has any full weight keys
    public var hasFullWeightKey: Bool {
        fullWeightKeys.count > 0
    }

    /// List of non-revoked keys with full signing weight (1000+)
    public var fullWeightKeys: [Flow.AccountKey] {
        account.keys.filter { !$0.revoked && $0.weight >= 1000 }
    }

    /// Cryptographic key for signing
    public let key: (any KeyProtocol)?
    
    public let chainID: Flow.ChainID

    // MARK: - Initialization
    
    /// Initialize an account
    /// - Parameters:
    ///   - account: Flow account data
    ///   - key: Optional signing key
    init(account: Flow.Account, chainID: Flow.ChainID,  key: (any KeyProtocol)?) {
        self.account = account
        self.key = key
        self.chainID = chainID
        
        Task {
            try await fetchAccount()
        }
    }
    
    public func fetchAccount() async throws {
        do {
            if let cached = try loadCache() {
                self.childs = cached.childs
                self.coa = cached.coa
            }
        } catch {
            //TODO: Handle no cache log
            print("AAAAAA ====> \(address.hex) - \(error.localizedDescription)")
        }
        try await _ = loadLinkedAccounts()
        
    
        try cache()
    }

    // MARK: - Key Management
    
    /// Find all keys in the account that match the provided key
    /// - Returns: Array of matching account keys, or nil if no key is available
    public func findKeyInAccount() -> [Flow.AccountKey]? {
        guard let key else {
            return nil
        }

        var keys: [Flow.AccountKey] = []
        if let p256 = key.publicKey(signAlgo: .ECDSA_P256) {
            let p256Keys = account.keys.filter { !$0.revoked && $0.weight >= 1000 && $0.publicKey.data == p256 }
            keys += p256Keys
        }
        if let secpKey = key.publicKey(signAlgo: .ECDSA_SECP256k1) {
            let secpKeys = account.keys.filter { !$0.revoked && $0.weight >= 1000 && $0.publicKey.data == secpKey }
            keys += secpKeys
        }

        return keys
    }

    // MARK: - Account Relationships
    
    /// Load all linked accounts (VM and child accounts) in parallel
    @discardableResult
    public func loadLinkedAccounts() async throws -> (vms: COA?, childs: [ChildAccount]) {
        // Execute both fetch operations concurrently
        async let vmFetch: COA? = fetchVM()
        async let childFetch: [ChildAccount] = fetchChild()
        
        // Wait for both operations to complete
        return try await (vmFetch, childFetch)
    }
    
    /// Fetch child accounts
    /// - Note: Implementation pending
    @discardableResult
    public func fetchChild() async throws -> [ChildAccount] {
        let childs = try await flow.getChildMetadata(address: account.address)
        let childAccounts = childs.compactMap { (addr, metadata) in
            ChildAccount(address: .init(addr),
                         network: chainID,
                         name: metadata.name,
                         description: metadata.description,
                         icon: metadata.thumbnail?.url)
        }
        self.childs = childAccounts
        return childAccounts
    }

    /// Fetch virtual machine accounts
    /// - Note: Implementation pending
    @discardableResult
    public func fetchVM() async throws -> COA? {
        guard let address = try await flow.getEVMAddress(address: account.address) else {
            // No COA
            return nil
        }

        guard let coa = COA(address, network: chainID) else {
            throw WalletError.invaildEVMAddress
        }
        
        self.coa = coa
        return coa
    }
}

extension Account: Equatable {
    public static func == (lhs: Account, rhs: Account) -> Bool {
        lhs.address == rhs.address
    }
}

// MARK: - Flow Signer Implementation

extension Account: FlowSigner {
    /// Account address for signing
    public var address: Flow.Address {
        account.address
    }

    /// Key index for signing
    public var keyIndex: Int {
        findKeyInAccount()?.first?.index ?? 0
    }

    /// Sign a Flow transaction
    /// - Parameters:
    ///   - transaction: Transaction to sign (unused)
    ///   - signableData: Data to sign
    /// - Returns: Signed data
    /// - Throws: WalletError if signing key is not available
    public func sign(transaction _: Flow.Transaction, signableData: Data) async throws -> Data {
        guard let key, let signKey = findKeyInAccount()?.first else {
            throw WalletError.emptySignKey
        }

        return try key.sign(data: signableData, signAlgo: signKey.signAlgo, hashAlgo: signKey.hashAlgo)
    }
}
