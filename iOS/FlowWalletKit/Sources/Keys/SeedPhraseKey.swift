//
//  File.swift
//
//
//  Created by Hao Fu on 16/1/2024.
//

import CryptoKit
import Flow
import Foundation
import KeychainAccess
import WalletCore

public class SeedPhraseKey: KeyProtocol {
    public struct AdvanceOption {
        let derivationPath: String
        let seedPhraseLength: BIP39.SeedPhraseLength
        let passphrase: String
    }

    public struct KeyData: Codable {
        let mnemonic: String
        let derivationPath: String
        let seedPhraseLength: BIP39.SeedPhraseLength
        let passphrase: String
    }

    public var storage: any StorageProtocol
    public var keyType: KeyType = .seedPhrase
    public var derivationPath = "m/44'/539'/0'/0/0"
    public var passphrase: String = ""
    public var seedPhraseLength: BIP39.SeedPhraseLength = SeedPhraseKey.defaultSeedPhraseLength

    public static let defaultSeedPhraseLength: BIP39.SeedPhraseLength = .twelve

    public let hdWallet: HDWallet

    public init(hdWallet: HDWallet,
         storage: any StorageProtocol,
         derivationPath: String = "m/44'/539'/0'/0/0",
         passphrase: String = "",
         seedPhraseLength: BIP39.SeedPhraseLength = SeedPhraseKey.defaultSeedPhraseLength
    )
    {
        self.hdWallet = hdWallet
        self.storage = storage
        self.derivationPath = derivationPath
        self.passphrase = passphrase
        self.seedPhraseLength = seedPhraseLength
    }

    public static func create(_ advance: AdvanceOption, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: advance.seedPhraseLength.strength, passphrase: advance.passphrase) else {
            throw WalletError.initHDWalletFailed
        }

        let key = SeedPhraseKey(hdWallet: hdWallet,
                                storage: storage,
                                derivationPath: advance.derivationPath,
                                passphrase: advance.passphrase,
                                seedPhraseLength: advance.seedPhraseLength
        )
        return key
    }

    public static func create(storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: SeedPhraseKey.defaultSeedPhraseLength.strength, passphrase: "") else {
            throw WalletError.initHDWalletFailed
        }
        return SeedPhraseKey(hdWallet: hdWallet, storage: storage)
    }

    public static func createAndStore(id: String, password: String, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let hdWallet = HDWallet(strength: SeedPhraseKey.defaultSeedPhraseLength.strength, passphrase: "") else {
            throw WalletError.initHDWalletFailed
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: hdWallet.entropy)
        let key = SeedPhraseKey(hdWallet: hdWallet, storage: storage)
        try key.store(id: id, password: password)
        return key
    }

    public static func get(id: String, password: String, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let data = try storage.get(id) else {
            throw WalletError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let entropyData = try cipher.decrypt(combinedData: data)
        let model = try JSONDecoder().decode(KeyData.self, from: entropyData)

        guard let hdWallet = HDWallet(mnemonic: model.mnemonic, passphrase: model.passphrase) else {
            throw WalletError.initHDWalletFailed
        }
        
        return SeedPhraseKey(hdWallet: hdWallet, storage: storage, derivationPath: model.derivationPath, passphrase: model.passphrase, seedPhraseLength: model.seedPhraseLength)
    }

    public func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return false
        }
        return pubK.verify(signature: signature, message: message)
    }

    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }
        let model = KeyData(mnemonic: hdWallet.mnemonic, derivationPath: derivationPath, seedPhraseLength: seedPhraseLength, passphrase: passphrase)
        let data = try JSONEncoder().encode(model)
        let encrypted = try cipher.encrypt(data: data)
        try storage.set(id, value: encrypted)
    }

    public static func restore(secret: KeyData, storage: any StorageProtocol) throws -> SeedPhraseKey {
        guard let wallet = HDWallet(mnemonic: secret.mnemonic, passphrase: secret.passphrase) else {
            throw WalletError.restoreWalletFailed
        }

        let key = SeedPhraseKey(hdWallet: wallet, storage: storage,
                                derivationPath: secret.derivationPath, passphrase: secret.passphrase)
        return key
    }

    public func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return nil
        }
        return pubK.uncompressed.data.dropFirst()
    }
    
    public func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let curve = signAlgo.WCCurve else {
            return nil
        }
        var pk = hdWallet.getKeyByCurve(curve: curve, derivationPath: derivationPath)
        defer { pk = WalletCore.PrivateKey() }
        return pk.data
    }

    public func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data {
        let hashed = try hashAlgo.hash(data: data)

        guard let curve = signAlgo.WCCurve else {
            throw WalletError.unsupportSignatureAlgorithm
        }

        var pk = hdWallet.getKeyByCurve(curve: curve, derivationPath: derivationPath)
        defer { pk = WalletCore.PrivateKey() }
        guard let signature = pk.sign(digest: hashed, curve: curve) else {
            throw WalletError.signError
        }
        return signature.dropLast()
    }

    private func getPublicKey(signAlgo: Flow.SignatureAlgorithm) throws -> PublicKey {
        switch signAlgo {
        case .ECDSA_P256:
            var pk = hdWallet.getKeyByCurve(curve: .nist256p1, derivationPath: derivationPath)
            defer { pk = WalletCore.PrivateKey() }
            return pk.getPublicKeyNist256p1()
        case .ECDSA_SECP256k1:
            var pk = hdWallet.getKeyByCurve(curve: .secp256k1, derivationPath: derivationPath)
            defer { pk = WalletCore.PrivateKey() }
            return pk.getPublicKeySecp256k1(compressed: false)
        case .unknown:
            throw WalletError.unsupportSignatureAlgorithm
        }
    }
}
