/// FlowWalletKit - Private Key Implementation
///
/// This module implements key management using raw private keys.
/// It provides functionality for:
/// - Creating and managing private keys
/// - Secure storage and encryption of private keys
/// - Transaction signing with ECDSA keys
/// - Support for both NIST P-256 and secp256k1 curves
/// - KeyStore JSON import/export

import CryptoKit
import Flow
import Foundation
import KeychainAccess
import WalletCore

/// Implementation of KeyProtocol using raw private keys
public class PrivateKey: KeyProtocol {
    /// Type used for advanced key creation options (raw private key string)
    public typealias Advance = String

    // MARK: - Properties

    /// Storage implementation for persisting key data
    public var storage: any StorageProtocol
    /// Type identifier for this key implementation
    public var keyType: KeyType = .privateKey
    /// Underlying private key implementation from WalletCore
    public let pk: WalletCore.PrivateKey

    // MARK: - Initialization

    /// Initialize with custom storage
    /// - Parameter storage: Storage implementation
    init(storage: any StorageProtocol) {
        pk = WalletCore.PrivateKey()
        self.storage = storage
    }

    /// Initialize with existing private key and storage
    /// - Parameters:
    ///   - pk: WalletCore private key
    ///   - storage: Storage implementation
    init(pk: WalletCore.PrivateKey, storage: any StorageProtocol) {
        self.pk = pk
        self.storage = storage
    }
    
    /// Initialize with existing private key and storage
    /// - Parameters:
    ///   - pk: Private Key raw data
    ///   - storage: Storage implementation
    init?(pk: Data, storage: any StorageProtocol) {
        guard let pk = WalletCore.PrivateKey(data: pk) else {
            return nil
        }
        self.pk = pk
        self.storage = storage
    }

    // MARK: - Key Creation

    /// Create a new private key with storage
    /// - Parameter storage: Storage implementation
    /// - Returns: New private key instance
    public static func create(storage: any StorageProtocol) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        return PrivateKey(pk: pk, storage: storage)
    }

    /// Create and store a new private key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    /// - Returns: New private key instance
    /// - Throws: FWKError if encryption or storage fails
    public func create(id: String, password: String) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk, storage: storage)
    }

    /// Create and store a new private key (static version)
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    ///   - storage: Storage implementation
    /// - Returns: New private key instance
    /// - Throws: FWKError if encryption or storage fails
    public static func createAndStore(id: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk, storage: storage)
    }

    // MARK: - Key Recovery

    /// Retrieve a stored private key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for decrypting the key
    ///   - storage: Storage implementation
    /// - Returns: Retrieved private key instance
    /// - Throws: FWKError if retrieval or decryption fails
    public static func get(id: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        guard let data = try storage.get(id) else {
            throw FWKError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let pkData = try cipher.decrypt(combinedData: data)

        guard let pk = WalletCore.PrivateKey(data: pkData) else {
            throw FWKError.initPrivateKeyFailed
        }

        return PrivateKey(pk: pk, storage: storage)
    }

    /// Restore a private key from raw data
    /// - Parameters:
    ///   - secret: Raw private key data
    ///   - storage: Storage implementation
    /// - Returns: Restored private key instance
    /// - Throws: FWKError if restoration fails
    public static func restore(secret: Data, storage: any StorageProtocol) throws -> PrivateKey {
        guard let pk = WalletCore.PrivateKey(data: secret) else {
            throw FWKError.restoreWalletFailed
        }
        return PrivateKey(pk: pk, storage: storage)
    }

    /// Restore a private key from KeyStore JSON
    /// - Parameters:
    ///   - json: KeyStore JSON string
    ///   - password: Password to decrypt the KeyStore
    ///   - storage: Storage implementation
    /// - Returns: Restored private key instance
    /// - Throws: FWKError if JSON parsing or decryption fails
    public static func restore(json: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        guard let jsonData = json.data(using: .utf8), let passwordData = password.data(using: .utf8) else {
            throw FWKError.restoreWalletFailed
        }

        guard let storedKey = StoredKey.importJSON(json: jsonData) else {
            throw FWKError.invaildKeyStoreJSON
        }
        guard let pkData = storedKey.decryptPrivateKey(password: passwordData) else {
            throw FWKError.invaildKeyStorePassword
        }

        guard let pk = WalletCore.PrivateKey(data: pkData) else {
            throw FWKError.invaildPrivateKey
        }

        return PrivateKey(pk: pk, storage: storage)
    }

    // MARK: - Key Operations

    /// Store the private key securely
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    /// - Throws: FWKError if encryption or storage fails
    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
    }

    /// Verify a signature
    /// - Parameters:
    ///   - signature: Signature to verify
    ///   - message: Original message that was signed
    ///   - signAlgo: Signature algorithm used
    /// - Returns: Whether the signature is valid
    public func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return false
        }
        return pubK.verify(signature: signature, message: message)
    }

    /// Get the public key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Public key data (uncompressed, without prefix)
    public func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return nil
        }
        return pubK.uncompressed.data.dropFirst()
    }

    /// Get the raw private key data
    /// - Parameter signAlgo: Signature algorithm (default: ECDSA_P256)
    /// - Returns: Raw private key data
    public func privateKey(signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Data? {
        return pk.data
    }

    /// Sign data using specified algorithms
    /// - Parameters:
    ///   - data: Data to sign
    ///   - signAlgo: Signature algorithm
    ///   - hashAlgo: Hash algorithm
    /// - Returns: Signature data
    /// - Throws: FWKError if signing fails
    public func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data {
        let hashed = try hashAlgo.hash(data: data)
        guard let curve = signAlgo.WCCurve else {
            throw FWKError.unsupportSignatureAlgorithm
        }
        guard let signature = pk.sign(digest: hashed, curve: curve) else {
            throw FWKError.signError
        }

        return signature.dropLast()
    }

    // MARK: - Private Methods

    /// Get the public key implementation for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Public key implementation
    /// - Throws: FWKError if algorithm is not supported
    private func getPublicKey(signAlgo: Flow.SignatureAlgorithm) throws -> PublicKey {
        switch signAlgo {
        case .ECDSA_P256:
            return pk.getPublicKeyNist256p1()
        case .ECDSA_SECP256k1:
            return pk.getPublicKeySecp256k1(compressed: false)
        case .unknown:
            throw FWKError.unsupportSignatureAlgorithm
        }
    }
}