/// FlowWalletKit - BIP39 Seed Phrase Key Implementation
///
/// This module implements key management using BIP39 seed phrases.
/// It provides functionality for:
/// - Creating and managing HD wallets
/// - Deriving keys using BIP44 paths
/// - Secure storage of seed phrases
/// - Transaction signing with derived keys

import CryptoKit
import Flow
import Foundation
import KeychainAccess
import WalletCore

/// Implementation of KeyProtocol using BIP39 seed phrases
public class SeedPhraseKey: KeyProtocol {
    // MARK: - Types
    
    /// Advanced options for seed phrase key creation
    public struct AdvanceOption {
        /// BIP44 derivation path
        let derivationPath: String
        /// Length of the seed phrase (12, 15, 18, 21, 24 words)
        let seedPhraseLength: BIP39.SeedPhraseLength
        /// Optional passphrase for additional security
        let passphrase: String
    }

    /// Storage model for seed phrase data
    public struct KeyData: Codable {
        /// BIP39 mnemonic words
        let mnemonic: String
        /// BIP44 derivation path
        let derivationPath: String
        /// Optional passphrase
        let passphrase: String
    }

    // MARK: - Properties

    /// Storage implementation for persisting key data
    public var storage: any StorageProtocol
    /// Type identifier for this key implementation
    public var keyType: KeyType = .seedPhrase
    /// BIP44 derivation path (default: Flow path m/44'/539'/0'/0/0)
    static public var derivationPath = "m/44'/539'/0'/0/0"
    /// Optional passphrase for additional security
    static public var passphrase: String = ""
    
    /// BIP44 derivation path (default: Flow path m/44'/539'/0'/0/0)
    public var derivationPath: String
    /// Optional passphrase for additional security
    public var passphrase: String
    
    /// Default base derivation path for Ethereum accounts
    public static var ethBaseDerivationPath = DerivationPath(purpose: .bip44,
                                                             coin: CoinType.ethereum.slip44Id,
                                                             account: 0,
                                                             change: 0,
                                                             address: 0).description

    /// Default seed phrase length (12 words)
    public static let defaultSeedPhraseLength: BIP39.SeedPhraseLength = .twelve

    /// Underlying HD wallet implementation
    public let hdWallet: HDWallet

    // MARK: - Initialization

    /// Initialize a seed phrase key
    /// - Parameters:
    ///   - hdWallet: HD wallet implementation
    ///   - storage: Storage implementation
    ///   - derivationPath: BIP44 derivation path
    ///   - passphrase: Optional passphrase
    ///   - seedPhraseLength: Length of seed phrase
    public init(hdWallet: HDWallet,
                storage: any StorageProtocol,
                derivationPath: String = SeedPhraseKey.derivationPath,
                passphrase: String = SeedPhraseKey.passphrase
    ) {
        self.hdWallet = hdWallet
        self.storage = storage
        self.derivationPath = derivationPath
        self.passphrase = passphrase
    }

    // MARK: - Key Creation

    /// Create a new key with advanced options
    /// - Parameters:
    ///   - advance: Advanced creation options
    ///   - storage: Storage implementation
    /// - Returns: New seed phrase key
    /// - Throws: FWKError if HD wallet creation fails
    public static func create(_ advance: AdvanceOption, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: advance.seedPhraseLength.strength, passphrase: advance.passphrase) else {
            throw FWKError.initHDWalletFailed
        }

        let key = SeedPhraseKey(hdWallet: hdWallet,
                                storage: storage,
                                derivationPath: advance.derivationPath,
                                passphrase: advance.passphrase)
        return key
    }
    
    /// Create a new key with advanced options
    /// - Parameters:
    ///   - seedphrase: Seed Phrase
    ///   - storage: Storage implementation
    /// - Returns: New seed phrase key
    /// - Throws: FWKError if HD wallet creation fails
    public static func create(_ seedphrase: String,
                              derivationPath: String = SeedPhraseKey.derivationPath,
                              passphrase: String = SeedPhraseKey.passphrase,
                              storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(mnemonic: seedphrase, passphrase: passphrase) else {
            throw FWKError.initHDWalletFailed
        }
        
        let key = SeedPhraseKey(hdWallet: hdWallet,
                                storage: storage,
                                derivationPath: derivationPath,
                                passphrase: passphrase)
        return key
    }

    /// Create a new key with default options
    /// - Parameter storage: Storage implementation
    /// - Returns: New seed phrase key
    /// - Throws: FWKError if HD wallet creation fails
    public static func create(storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: SeedPhraseKey.defaultSeedPhraseLength.strength, passphrase: "") else {
            throw FWKError.initHDWalletFailed
        }
        return SeedPhraseKey(hdWallet: hdWallet, storage: storage)
    }

    /// Create and store a new key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    ///   - storage: Storage implementation
    /// - Returns: New seed phrase key
    /// - Throws: FWKError if creation or storage fails
    public static func createAndStore(id: String, password: String, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: SeedPhraseKey.defaultSeedPhraseLength.strength, passphrase: "") else {
            throw FWKError.initHDWalletFailed
        }
        let key = SeedPhraseKey(hdWallet: hdWallet, storage: storage)
        try key.store(id: id, password: password)
        return key
    }

    // MARK: - Key Recovery

    /// Retrieve a stored key
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for decrypting the key
    ///   - storage: Storage implementation
    /// - Returns: Retrieved seed phrase key
    /// - Throws: FWKError if retrieval fails
    public static func get(id: String, password: String, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let data = try storage.get(id) else {
            throw FWKError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }

        let entropyData = try cipher.decrypt(combinedData: data)
        let model = try JSONDecoder().decode(KeyData.self, from: entropyData)

        guard let hdWallet = HDWallet(mnemonic: model.mnemonic, passphrase: model.passphrase) else {
            throw FWKError.initHDWalletFailed
        }
        
        return SeedPhraseKey(hdWallet: hdWallet, storage: storage, derivationPath: model.derivationPath, passphrase: model.passphrase)
    }

    // MARK: - Cryptographic Operations

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

    /// Store the key securely
    /// - Parameters:
    ///   - id: Unique identifier for the key
    ///   - password: Password for encrypting the key
    /// - Throws: FWKError if storage fails
    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw FWKError.initChaChapolyFailed
        }
        let model = KeyData(mnemonic: hdWallet.mnemonic,
                            derivationPath: SeedPhraseKey.derivationPath,
                            passphrase: passphrase)
        let data = try JSONEncoder().encode(model)
        let encrypted = try cipher.encrypt(data: data)
        try storage.set(id, value: encrypted)
    }

    /// Restore a key from secret material
    /// - Parameters:
    ///   - secret: Secret key data
    ///   - storage: Storage implementation
    /// - Returns: Restored seed phrase key
    /// - Throws: FWKError if restoration fails
    public static func restore(secret: KeyData, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let wallet = HDWallet(mnemonic: secret.mnemonic, passphrase: secret.passphrase) else {
            throw FWKError.restoreWalletFailed
        }

        let key = SeedPhraseKey(hdWallet: wallet, storage: storage,
                                derivationPath: secret.derivationPath, passphrase: secret.passphrase)
        return key
    }

    /// Get the public key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Public key data
    public func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return nil
        }
        return pubK.uncompressed.data.dropFirst() // 04
    }
    
    /// Get the private key for a signature algorithm
    /// - Parameter signAlgo: Signature algorithm
    /// - Returns: Private key data
    public func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let curve = signAlgo.WCCurve else {
            return nil
        }
        var pk = hdWallet.getKeyByCurve(curve: curve, derivationPath: SeedPhraseKey.derivationPath)
        defer { pk = WalletCore.PrivateKey() }
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

        var pk = hdWallet.getKeyByCurve(curve: curve, derivationPath: SeedPhraseKey.derivationPath)
        defer { pk = WalletCore.PrivateKey() }
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
        guard let pubKey = hdWallet.getPublicKey(signAlgo: signAlgo) else {
            throw FWKError.unsupportSignatureAlgorithm
        }
        return pubKey
    }
}

// MARK: - Ethereum Support

extension SeedPhraseKey: EthereumKeyProtocol {
    
    public func ethAddress(index: UInt32) throws -> String {
        try withEthereumPrivateKey(index: index) { key in
            CoinType.ethereum.deriveAddress(privateKey: key)
        }
    }
    
    public func ethPublicKey(index: UInt32) throws -> Data {
        try withEthereumPrivateKey(index: index) { key in
            key.getPublicKeySecp256k1(compressed: false).uncompressed.data
        }
    }
    
    public func ethPrivateKey(index: UInt32) throws -> Data {
        try withEthereumPrivateKey(index: index) { key in
            key.data
        }
    }
    
    public func ethSign(digest: Data, index: UInt32) throws -> Data {
        try withEthereumPrivateKey(index: index) { key in
            try validateEthereumDigest(digest)
            guard let signature = key.sign(digest: digest, curve: .secp256k1) else {
                throw FWKError.signError
            }
            // Signature is 65 bytes: [r(32) | s(32) | v(1)]
            return try normalizeEthereumSignature(signature)
        }
    }
    
    private func ethereumDerivationPath(index: UInt32) throws -> DerivationPath {
        guard let basePath = DerivationPath(SeedPhraseKey.ethBaseDerivationPath) else {
            throw FWKError.invalidEthereumDerivationPath
        }
        return DerivationPath(purpose: basePath.purpose,
                              coin: basePath.coin,
                              account: basePath.account,
                              change: basePath.change,
                              address: index)
    }
    
    private func withEthereumPrivateKey<T>(index: UInt32,
                                           _ body: (WalletCore.PrivateKey) throws -> T) throws -> T {
        let path = try ethereumDerivationPath(index: index)
        var key = hdWallet.getKey(coin: .ethereum, derivationPath: path.description)
        defer { key = WalletCore.PrivateKey() }
        return try body(key)
    }
}
