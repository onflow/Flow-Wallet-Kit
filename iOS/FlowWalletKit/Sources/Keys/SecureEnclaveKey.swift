//
//  SecureEnclave.swift
//  FRW
//
//  Created by cat on 2023/11/6.
//

import CryptoKit
import Flow
import Foundation
import KeychainAccess
import WalletCore
import Factory

public class SecureEnclaveKey: KeyProtocol {
    public typealias Advance = String

    public var keyType: KeyType = .secureEnclave
    public let key: SecureEnclave.P256.Signing.PrivateKey
    @Injected(\.keychainStorage) public var storage

    public init(key: SecureEnclave.P256.Signing.PrivateKey) {
        self.key = key
    }

    public static func create() throws -> SecureEnclaveKey {
        let key = try SecureEnclave.P256.Signing.PrivateKey()
        return SecureEnclaveKey(key: key)
    }

    public static func createAndStore(id: String, password: String) throws -> SecureEnclaveKey {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }
        let key = try SecureEnclave.P256.Signing.PrivateKey()
        let encrypted = try cipher.encrypt(data: key.dataRepresentation)
        @Injected(\.keychainStorage) var storage
        try storage.set(id, value: encrypted)
        return SecureEnclaveKey(key: key)
    }

    public static func get(id: String, password: String) throws -> SecureEnclaveKey {
        @Injected(\.keychainStorage) var storage
        guard let data = try storage.get(id) else {
            throw WalletError.emptyKeychain
        }

        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }

        let pk = try cipher.decrypt(combinedData: data)
        let key = try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: pk)
        return SecureEnclaveKey(key: key)
    }

    public static func restore(secret: Data) throws -> SecureEnclaveKey {
        let key = try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: secret)
        return SecureEnclaveKey(key: key)
    }

    public func store(id: String, password: String) throws {
        guard let cipher = ChaChaPolyCipher(key: password) else {
            throw WalletError.initChaChapolyFailed
        }
        let encrypted = try cipher.encrypt(data: key.dataRepresentation)
        try storage.set(id, value: encrypted)
    }

    public func publicKey(signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Data? {
        if signAlgo != .ECDSA_P256 {
            return nil
        }
        return key.publicKey.rawRepresentation
    }

    public func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return nil
    }

    public func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm = .ECDSA_P256) -> Bool {
        if signAlgo != .ECDSA_P256 {
            return false
        }
        guard let result = try? key.publicKey.isValidSignature(.init(rawRepresentation: signature), for: message) else {
            return false
        }
        return result
    }

    public func sign(data: Data,
                     signAlgo _: Flow.SignatureAlgorithm = .ECDSA_P256,
                     hashAlgo: Flow.HashAlgorithm) throws -> Data
    {
        let hashed = SHA256.hash(data: data)
        return try key.signature(for: hashed).rawRepresentation
    }

    public func rawSign(data: Data, signAlgo _: Flow.SignatureAlgorithm = .ECDSA_P256) throws -> Data {
        return try key.signature(for: data).rawRepresentation
    }
}
