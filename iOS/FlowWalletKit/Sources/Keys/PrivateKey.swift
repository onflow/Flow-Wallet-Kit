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

public class PrivateKey: KeyProtocol {
    public typealias Advance = String

    public var storage: any StorageProtocol
    public var keyType: KeyType = .privateKey
    public let pk: WalletCore.PrivateKey

    init() {
        pk = WalletCore.PrivateKey()
        storage = FWKManager.shared.storage
    }

    init(storage: any StorageProtocol) {
        pk = WalletCore.PrivateKey()
        self.storage = storage
    }

    init(pk: WalletCore.PrivateKey, storage: any StorageProtocol) {
        self.pk = pk
        self.storage = storage
    }

    public static func create(storage: any StorageProtocol) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        return PrivateKey(pk: pk, storage: storage)
    }

    public func create(id: String, password: String) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk, storage: storage)
    }

    public static func createAndStore(id: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk, storage: storage)
    }

    public static func get(id: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        guard let data = try storage.get(id) else {
            throw WalletError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let pkData = try cipher.decrypt(combinedData: data)

        guard let pk = WalletCore.PrivateKey(data: pkData) else {
            throw WalletError.initPrivateKeyFailed
        }

        return PrivateKey(pk: pk, storage: storage)
    }

    public static func restore(secret: Data, storage: any StorageProtocol) throws -> PrivateKey {
        guard let pk = WalletCore.PrivateKey(data: secret) else {
            throw WalletError.restoreWalletFailed
        }
        return PrivateKey(pk: pk, storage: storage)
    }

    public static func restore(json: String, password: String, storage: any StorageProtocol) throws -> PrivateKey {
        guard let jsonData = json.data(using: .utf8), let passwordData = password.data(using: .utf8) else {
            throw WalletError.restoreWalletFailed
        }

        guard let storedKey = StoredKey.importJSON(json: jsonData) else {
            throw WalletError.invaildKeyStoreJSON
        }
        guard let pkData = storedKey.decryptPrivateKey(password: passwordData) else {
            throw WalletError.invaildKeyStorePassword
        }

        guard let pk = WalletCore.PrivateKey(data: pkData) else {
            throw WalletError.invaildPrivateKey
        }

        return PrivateKey(pk: pk, storage: storage)
    }

    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
    }

    public func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return false
        }
        return pubK.verify(signature: signature, message: message)
    }

    public func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return nil
        }
        return pubK.uncompressed.data.dropFirst()
    }

    public func privateKey(signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Data? {
        return pk.data
    }

    public func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data {
        let hashed = try hashAlgo.hash(data: data)
        guard let curve = signAlgo.WCCurve else {
            throw WalletError.unsupportSignatureAlgorithm
        }
        guard let signature = pk.sign(digest: hashed, curve: curve) else {
            throw WalletError.signError
        }

        return signature.dropLast()
    }

    private func getPublicKey(signAlgo: Flow.SignatureAlgorithm) throws -> PublicKey {
        switch signAlgo {
        case .ECDSA_P256:
            return pk.getPublicKeyNist256p1()
        case .ECDSA_SECP256k1:
            return pk.getPublicKeySecp256k1(compressed: false)
        case .unknown:
            throw WalletError.unsupportSignatureAlgorithm
        }
    }


}
