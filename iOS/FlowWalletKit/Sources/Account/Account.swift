//
//  File.swift
//
//
//  Created by Hao Fu on 18/7/2024.
//

import Flow
import Foundation

public protocol ProxyProtocol {
    associatedtype Wallet

    static func get(id: String) throws -> Wallet
    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data
}


public class Account {
    public var childs: [Account]?

    public var hasChild: Bool {
        !(childs?.isEmpty ?? true)
    }
    
    public var hasFullWeightKey: Bool {
        fullWeightKeys.count > 0
    }

    public var fullWeightKeys: [Flow.AccountKey] {
        account.keys.filter { !$0.revoked && $0.weight >= 1000 }
    }

    public var vm: [Account]?

    public var hasVM: Bool {
        !(vm?.isEmpty ?? true)
    }

    public var canSign: Bool {
        !(key == nil)
    }

    public let account: Flow.Account

    public let key: (any KeyProtocol)?

    init(account: Flow.Account, key: (any KeyProtocol)?) {
        self.account = account
        self.key = key
    }

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

    public func fetchChild() {
        // TODO:
    }

    public func fetchVM() {
        // TODO:
    }
}

extension Account: FlowSigner {
    public var address: Flow.Address {
        account.address
    }

    public var keyIndex: Int {
        findKeyInAccount()?.first?.index ?? 0
    }

    public func sign(transaction _: Flow.Transaction, signableData: Data) async throws -> Data {
        guard let key, let signKey = findKeyInAccount()?.first else {
            throw WalletError.emptySignKey
        }

        return try key.sign(data: signableData, signAlgo: signKey.signAlgo, hashAlgo: signKey.hashAlgo)
    }
}
