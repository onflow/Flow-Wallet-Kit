/// FlowWalletKit - Key Management Protocol
///
/// This module defines the core protocol for cryptographic key management in FlowWalletKit.
/// It provides a unified interface for different types of keys (Secure Enclave, Seed Phrase, Private Key, etc.)
/// and their associated operations.

import Flow
import Foundation
import KeychainAccess

/// Protocol defining the interface for cryptographic key management
public protocol KeyProtocol: Identifiable {
    /// The concrete key type
    associatedtype Key
    /// The type used for secret key material
    associatedtype Secret
    /// The type used for advanced key creation options
    associatedtype Advance

    /// The type of key implementation
    var keyType: KeyType { get }

    /// Storage implementation for persisting key data
    var storage: StorageProtocol { set get }

    // MARK: - Key Creation and Recovery

    /// Create a new key with advanced options
    /// - Parameters:
    ///   - advance: Advanced creation options
    ///   - storage: Storage implementation
    /// - Returns: New key instance
    static func create(_ advance: Advance, storage: StorageProtocol) throws -> Key

    /// Create a new key with default options
    /// - Parameter storage: Storage implementation
    /// - Returns: New key instance
    static func create(storage: StorageProtocol) throws -> Key

    /// Create and store a new key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    ///   - storage: Storage implementation
    /// - Returns: New key instance
    static func createAndStore(id: String, password: String, storage: StorageProtocol) throws -> Key

    /// Retrieve a stored key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for decrypting the key
    ///   - storage: Storage implementation
    /// - Returns: Retrieved key instance
    static func get(id: String, password: String, storage: StorageProtocol) throws -> Key

    /// Restore a key from secret material
    /// - Parameters:
    ///   - secret: Secret key material
    ///   - storage: Storage implementation
    /// - Returns: Restored key instance
    static func restore(secret: Secret, storage: StorageProtocol) throws -> Key

    // MARK: - Key Operations

    /// Store the key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    func store(id: String, password: String) throws

    /// Verify a signature
    /// - Parameters:
    ///   - signature: Signature to verify
    ///   - message: Original message that was signed
    ///   - signAlgo: Signature algorithm used
    /// - Returns: Whether the signature is valid
    func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool

    /// Get the public key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Public key data
    func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data?

    /// Get the private key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Private key data
    func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data?

    /// Sign data using specified algorithms
    /// - Parameters:
    ///   - data: Data to sign
    ///   - signAlgo: Signature algorithm
    ///   - hashAlgo: Hash algorithm
    /// - Returns: Signature data
    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data

    /// Remove a stored key
    /// - Parameter id: Unique identifier of the key to remove
    func remove(id: String) throws

    /// Get all stored key identifiers
    /// - Returns: Array of key identifiers
    func allKeys() -> [String]
}

// MARK: - Default Implementations

public extension KeyProtocol {
    /// Default ID implementation using public key or UUID
    var id: String {
        if let data = publicKey(signAlgo: .ECDSA_P256) {
            return data.hexString
        }

        if let data = publicKey(signAlgo: .ECDSA_SECP256k1) {
            return data.hexString
        }

        return UUID().uuidString
    }

    /// Default implementation for removing a key
    func remove(id: String) throws {
        try storage.remove(id)
    }

    /// Default implementation for getting all keys
    func allKeys() -> [String] {
        storage.allKeys
    }

    /// Default implementation for advanced key creation
    static func create(_: Advance, storage _: any StorageProtocol) throws -> Key {
        throw FWKError.noImplement
    }
}