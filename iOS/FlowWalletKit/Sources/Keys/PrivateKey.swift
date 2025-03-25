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
import Factory

class PrivateKey: KeyProtocol {
    typealias Key = PrivateKey
    
    typealias Secret = Data
    
    typealias Advance = String
    
    @Injected(\.keychainStorage) var storage
    var keyType: KeyType = .privateKey
    let pk: WalletCore.PrivateKey

    init() {
        pk = WalletCore.PrivateKey()
    }

    init(pk: WalletCore.PrivateKey) {
        self.pk = pk
    }

    static func create() throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        return PrivateKey(pk: pk)
    }

    static func create(id: String, password: String) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        @Injected(\.keychainStorage) var storage
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk)
    }

    static func createAndStore(id: String, password: String) throws -> PrivateKey {
        let pk = WalletCore.PrivateKey()
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        @Injected(\.keychainStorage) var storage
        try storage.set(id, value: encrypted)
        return PrivateKey(pk: pk)
    }

    static func get(id: String, password: String) throws -> PrivateKey {
        @Injected(\.keychainStorage) var storage
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

        return PrivateKey(pk: pk)
    }

    static func restore(secret: Data) throws -> PrivateKey {
        guard let pk = WalletCore.PrivateKey(data: secret) else {
            throw WalletError.restoreWalletFailed
        }
        return PrivateKey(pk: pk)
    }

    func restore(json: String, password: String) throws -> PrivateKey {
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

        return PrivateKey(pk: pk)
    }

    func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let encrypted = try cipher.encrypt(data: pk.data)
        try storage.set(id, value: encrypted)
    }

    func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return false
        }
        return pubK.verify(signature: signature, message: message)
    }

    func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        guard let pubK = try? getPublicKey(signAlgo: signAlgo) else {
            return nil
        }
        return pubK.uncompressed.data.dropFirst()
    }

    func privateKey(signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Data? {
        return pk.data
    }

    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data {
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
