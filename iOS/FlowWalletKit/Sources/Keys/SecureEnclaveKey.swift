//
//  SecureEnclave.swift
//  FRW
//
//  Created by cat on 2023/11/6.
//

/// FlowWalletKit - Secure Enclave Key Implementation
///
/// This module implements key management using Apple's Secure Enclave.
/// It provides functionality for:
/// - Creating and managing keys in the Secure Enclave
/// - Secure storage and encryption of key data
/// - Transaction signing with ECDSA P-256 keys
/// - Hardware-backed security for private keys
/// - Biometric authentication support
///
/// The Secure Enclave provides a hardware-isolated environment for key storage and operations,
/// offering enhanced security compared to software-based key management.

import CryptoKit
import Flow
import Foundation
import KeychainAccess
import WalletCore

/// Implementation of KeyProtocol using Apple's Secure Enclave
/// This class provides hardware-backed key management and signing operations
public class SecureEnclaveKey: KeyProtocol {
    /// Type used for advanced key creation options (not currently used)
    public typealias Advance = String

    // MARK: - Properties

    /// Type identifier for this key implementation
    public var keyType: KeyType = .secureEnclave
    /// Underlying Secure Enclave private key
    public let key: SecureEnclave.P256.Signing.PrivateKey
    /// Storage implementation for persisting key data
    public var storage: any StorageProtocol

    // MARK: - Initialization

    /// Initialize with an existing Secure Enclave key
    /// - Parameters:
    ///   - key: Secure Enclave P-256 private key
    ///   - storage: Storage implementation
    public init(key: SecureEnclave.P256.Signing.PrivateKey, storage: any StorageProtocol) {
        self.key = key
        self.storage = storage
    }

    // MARK: - Key Creation

    /// Create a new Secure Enclave key
    /// - Parameter storage: Storage implementation
    /// - Returns: New Secure Enclave key instance
    /// - Throws: Error if key creation fails
    public static func create(storage: any StorageProtocol) throws -> SecureEnclaveKey {
        let key = try SecureEnclave.P256.Signing.PrivateKey()
        return SecureEnclaveKey(key: key, storage: storage)
    }

    /// Create and store a new Secure Enclave key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key data
    ///   - storage: Storage implementation
    /// - Returns: New Secure Enclave key instance
    /// - Throws: FWKError if encryption or storage fails
    public static func createAndStore(id: String, password: String, storage: any StorageProtocol) throws -> SecureEnclaveKey {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }
        let key = try SecureEnclave.P256.Signing.PrivateKey()
        let encrypted = try cipher.encrypt(data: key.dataRepresentation)
        try storage.set(id, value: encrypted)
        return SecureEnclaveKey(key: key, storage: storage)
    }

    // MARK: - Key Recovery

    /// Retrieve a stored Secure Enclave key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for decrypting the key data
    ///   - storage: Storage implementation
    /// - Returns: Retrieved Secure Enclave key instance
    /// - Throws: FWKError if retrieval or decryption fails
    public static func get(id: String, password: String, storage: any StorageProtocol) throws -> SecureEnclaveKey {
        guard let data = try storage.get(id) else {
            throw FWKError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let pk = try cipher.decrypt(combinedData: data)
        let key = try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: pk)
        return SecureEnclaveKey(key: key, storage: storage)
    }

    /// Restore a Secure Enclave key from raw data
    /// - Parameters:
    ///   - secret: Raw key data
    ///   - storage: Storage implementation
    /// - Returns: Restored Secure Enclave key instance
    /// - Throws: Error if key restoration fails
    public static func restore(secret: Data, storage: any StorageProtocol) throws -> SecureEnclaveKey {
        let key = try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: secret)
        return SecureEnclaveKey(key: key, storage: storage)
    }

    // MARK: - Key Operations

    /// Store the Secure Enclave key data
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key data
    /// - Throws: FWKError if encryption or storage fails
    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }
        let encrypted = try cipher.encrypt(data: key.dataRepresentation)
        try storage.set(id, value: encrypted)
    }

    /// Get the public key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm (must be ECDSA_P256)
    /// - Returns: Public key data, or nil if algorithm is not supported
    public func publicKey(signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Data? {
        if signAlgo != .ECDSA_P256 {
            return nil
        }
        return key.publicKey.rawRepresentation
    }

    /// Get the private key data (always returns nil for Secure Enclave)
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Always nil as private key cannot be exported
    public func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return nil
    }

    /// Verify a signature
    /// - Parameters:
    ///   - signature: Signature to verify
    ///   - message: Original message that was signed
    ///   - signAlgo: Signature algorithm (must be ECDSA_P256)
    /// - Returns: Whether the signature is valid
    public func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Bool {
        if signAlgo != .ECDSA_P256 {
            return false
        }
        guard let result = try? key.publicKey.isValidSignature(.init(rawRepresentation: signature), for: message) else {
            return false
        }
        return result
    }

    /// Sign data using specified algorithms
    /// - Parameters:
    ///   - data: Data to sign
    ///   - signAlgo: Signature algorithm (must be ECDSA_P256)
    ///   - hashAlgo: Hash algorithm
    /// - Returns: Signature data
    /// - Throws: Error if signing fails
    public func sign(data: Data,
                     signAlgo _: Flow.SignatureAlgorithm = .ECDSA_P256,
                     hashAlgo: Flow.HashAlgorithm) throws -> Data
    {
        let hashed = SHA256.hash(data: data) 
        return try key.signature(for: hashed).rawRepresentation
    }

    /// Sign raw data without hashing
    /// - Parameters:
    ///   - data: Data to sign
    ///   - signAlgo: Signature algorithm (must be ECDSA_P256)
    /// - Returns: Signature data
    /// - Throws: Error if signing fails
    public func rawSign(data: Data, signAlgo _: Flow.SignatureAlgorithm = .ECDSA_P256) throws -> Data {
        return try key.signature(for: data).rawRepresentation
    }
    
}
