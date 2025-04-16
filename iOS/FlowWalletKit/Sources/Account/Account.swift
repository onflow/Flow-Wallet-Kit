/// FlowWalletKit - Account Management
///
/// This module provides account management functionality for Flow blockchain accounts.
/// It handles account operations, key management, and transaction signing.

import Flow
import Foundation

/// Represents a Flow blockchain account with signing capabilities
public class Account: ObservableObject {
    // MARK: - Properties
    
    /// Child accounts associated with this account
    @Published
    public var childs: [ChildAccount]?

    /// Whether this account has child accounts
    public var hasChild: Bool {
        !(childs?.isEmpty ?? true)
    }
    
    /// Whether this account has any linked accounts (child accounts or COA)
    public var hasLinkedAccounts: Bool {
        hasChild || hasCOA
    }

    /// Chain-Owned Account (COA) associated with this account
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
    
    /// Delegate for handling security checks before signing operations
    public var securityDelegate: SecurityCheckDelegate?
    
    /// Indicates whether the account is currently loading data
    @Published
    public var isLoading: Bool = false
    
    /// Storage mechanism used for caching account data
    private(set) var cacheStorage: StorageProtocol = FileSystemStorage()
    
    /// The account address in hexadecimal format
    public var hexAddr: String {
        address.hex
    }
    
    // MARK: - Cache Data Structure
    
    /// Structure representing cacheable account data
    public struct AccountCache: Codable {
        /// Child accounts associated with this account
        let childs: [ChildAccount]?
        /// Chain-Owned Account associated with this account
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
    
    /// The Flow chain ID this account belongs to
    public let chainID: Flow.ChainID

    // MARK: - Initialization
    
    /// Initialize an account
    /// - Parameters:
    ///   - account: Flow account data
    ///   - chainID: The Flow chain ID this account belongs to
    ///   - key: Optional signing key for transaction signing
    ///   - securityDelegate: Optional delegate for handling security checks before signing
    init(account: Flow.Account,
         chainID: Flow.ChainID,
         key: (any KeyProtocol)? = nil,
         securityDelegate: SecurityCheckDelegate? = nil) {
        self.account = account
        self.key = key
        self.chainID = chainID
        self.securityDelegate = securityDelegate
    }
    
    /// Fetch and update account data including linked accounts
    /// This method will:
    /// 1. Load cached data if available
    /// 2. Fetch fresh data for linked accounts
    /// 3. Update the cache with new data
    /// - Throws: Error if fetching or caching fails
    public func fetchAccount() async throws {
        do {
            if let cached = try loadCache() {
                self.childs = cached.childs
                self.coa = cached.coa
            }
        } catch {
            //TODO: Handle no cache log
            print("\(error.localizedDescription)")
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
    /// - Returns: Tuple containing the fetched COA and child accounts
    /// - Throws: Error if fetching fails
    @discardableResult
    public func loadLinkedAccounts() async throws -> (vms: COA?, childs: [ChildAccount]) {
        isLoading = true
        defer { isLoading = false }
        // Execute both fetch operations concurrently
        async let vmFetch: COA? = fetchVM()
        async let childFetch: [ChildAccount] = fetchChild()
        
        // Wait for both operations to complete
        return try await (vmFetch, childFetch)
    }
    
    /// Fetch child accounts associated with this account
    /// - Returns: Array of child accounts
    /// - Throws: Error if fetching fails
    @discardableResult
    public func fetchChild() async throws -> [ChildAccount] {
        let childs = try await flow.getChildMetadata(address: account.address)
        let childAccounts = childs.compactMap { (addr, metadata) in
            ChildAccount(address: .init(addr),
                         network: chainID,
                         parentAddress: address,
                         name: metadata.name,
                         description: metadata.description,
                         icon: metadata.thumbnail?.url)
        }
        self.childs = childAccounts
        return childAccounts
    }

    /// Fetch the Chain-Owned Account (COA) associated with this account
    /// - Returns: COA instance if available, nil if no COA exists
    /// - Throws: FWKError.invaildEVMAddress if the EVM address is invalid
    @discardableResult
    public func fetchVM() async throws -> COA? {
        guard let address = try await flow.getEVMAddress(address: account.address) else {
            // No COA
            return nil
        }

        guard let coa = COA(address, network: chainID) else {
            throw FWKError.invaildEVMAddress
        }
        
        self.coa = coa
        return coa
    }
}

// MARK: - Equatable Implementation

extension Account: Equatable {
    /// Compare two accounts for equality based on their addresses
    /// - Parameters:
    ///   - lhs: Left-hand side account
    ///   - rhs: Right-hand side account
    /// - Returns: True if the accounts have the same address
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

    /// Sign a Flow transaction or data
    /// - Parameters:
    ///   - signableData: Data to sign
    ///   - transaction: Optional transaction context
    /// - Returns: Signed data
    /// - Throws: FWKError if signing key is not available or security check fails
    public func sign(signableData: Data, transaction: Flow.Transaction? = nil) async throws -> Data {
        guard let key, let signKey = findKeyInAccount()?.first else {
            throw FWKError.emptySignKey
        }
        
        /// If there is securityDelegate, check if it's passed the security check
        if let delegate = securityDelegate {
            let result = try await delegate.verify()
            if !result {
                throw FWKError.failedPassSecurityCheck
            }
        }

        return try key.sign(data: signableData, signAlgo: signKey.signAlgo, hashAlgo: signKey.hashAlgo)
    }
}
