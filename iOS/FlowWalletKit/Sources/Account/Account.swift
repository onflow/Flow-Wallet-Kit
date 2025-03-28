/// FlowWalletKit - Account Management
///
/// This module provides account management functionality for Flow blockchain accounts.
/// It handles account operations, key management, and transaction signing.

import Flow
import Foundation

/// Protocol for proxy wallet implementations
public protocol ProxyProtocol {
    /// The type of wallet this proxy represents
    associatedtype Wallet

    /// Retrieve a wallet instance by ID
    /// - Parameter id: Unique identifier for the wallet
    /// - Returns: Wallet instance
    /// - Throws: Error if wallet cannot be retrieved
    static func get(id: String) throws -> Wallet
    
    /// Sign data using specified algorithms
    /// - Parameters:
    ///   - data: Data to sign
    ///   - signAlgo: Signature algorithm to use
    ///   - hashAlgo: Hash algorithm to use
    /// - Returns: Signed data
    /// - Throws: Error if signing fails
    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data
}

/// Represents a Flow blockchain account with signing capabilities
public class Account {
    // MARK: - Properties
    
    /// Child accounts associated with this account
    public var childs: [Account]?

    /// Whether this account has child accounts
    public var hasChild: Bool {
        !(childs?.isEmpty ?? true)
    }
    
    /// Whether this account has any full weight keys
    public var hasFullWeightKey: Bool {
        fullWeightKeys.count > 0
    }

    /// List of non-revoked keys with full signing weight (1000+)
    public var fullWeightKeys: [Flow.AccountKey] {
        account.keys.filter { !$0.revoked && $0.weight >= 1000 }
    }

    /// Virtual machine accounts associated with this account
    public var vm: [Account]?

    /// Whether this account has VM accounts
    public var hasVM: Bool {
        !(vm?.isEmpty ?? true)
    }

    /// Whether this account can sign transactions
    public var canSign: Bool {
        !(key == nil)
    }

    /// The underlying Flow account
    public let account: Flow.Account
    
    /// Account address in hex format
    public var address: String {
        account.address.hex
    }
    
    /// First available full weight key
    public var fullWeightKey: Flow.AccountKey? {
        fullWeightKeys.first
    }

    /// Cryptographic key for signing
    public let key: (any KeyProtocol)?

    // MARK: - Initialization
    
    /// Initialize an account
    /// - Parameters:
    ///   - account: Flow account data
    ///   - key: Optional signing key
    init(account: Flow.Account, key: (any KeyProtocol)?) {
        self.account = account
        self.key = key
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
    
    /// Fetch child accounts
    /// - Note: Implementation pending
    public func fetchChild() {
        // TODO:
    }

    /// Fetch virtual machine accounts
    /// - Note: Implementation pending
    public func fetchVM() {
        // TODO:
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
