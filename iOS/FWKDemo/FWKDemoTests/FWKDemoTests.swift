//
//  FWKDemoTests.swift
//  FWKDemoTests
//
//  Created by Hao Fu on 1/5/2024.
//

import CryptoKit
import FlowWalletKit
@testable import FWKDemo
import WalletCore
import XCTest

final class FWKDemoTests: XCTestCase {
    let id = "userId"
    let password = "password"
    let wrongPassword = "wrong_password"
    var storage = KeychainStorage(service: "io.outblock.FWKDemo", label: "FWKDemo Unit Test", synchronizable: false)

//    func testSecureEnclaveKeyCreate() throws {
//        let wallet = try SecureEnclaveKey.create(id: id, password: password, storage: storage)
//        let reWallet = try SecureEnclaveKey.get(id: id, password: password, storage: storage)
//        XCTAssertEqual(try wallet.publicKey(), try reWallet.publicKey())
//    }
//
//    func testSecureEnclaveKeyStore() throws {
//        let wallet = try SecureEnclaveKey.create(storage: storage)
//        try wallet.store(id: id, password: password)
//        let reWallet = try SecureEnclaveKey.get(id: id, password: password, storage: storage)
//        XCTAssertEqual(try wallet.publicKey(), try reWallet.publicKey())
//    }

    func testExample() throws {
        let hdWallet = HDWallet(strength: 128, passphrase: "123")!
        print(hdWallet.mnemonic)

        let pk = hdWallet.getKeyByCurve(curve: .secp256k1, derivationPath: "m/44'/60'/0'/0/0")
        let pubK2 = pk.getPublicKeySecp256k1(compressed: false)

        print("pk -> \(pk.data.hexValue)")
        print("pubK2 -> \(pubK2.data.hexValue)")

        let password = "password".data(using: .utf8)!
        let key = StoredKey.importPrivateKey(privateKey: pk.data, name: "flow test", password: password, coin: .ethereum)!
        let jsonData = key.exportJSON()!

        print("jsonData -> \(String(data: jsonData, encoding: .utf8)!)")

//        let vKey = StoredKey.importJSON(json: jsonData)!
//        let hdWallet2 = vKey.wallet(password: password)!
//
//        XCTAssertEqual(hdWallet.mnemonic, hdWallet2.mnemonic)
    }
}

public extension Data {
    /// Convert data to hex string
    var hexValue: String {
        return reduce("") { $0 + String(format: "%02x", $1) }
    }
}
