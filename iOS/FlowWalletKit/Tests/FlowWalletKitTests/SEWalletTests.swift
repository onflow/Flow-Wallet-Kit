//import Foundation
//import Testing
//@testable import FlowWalletKit
//
//struct FlowWalletCoreTests {
//    let id = "userId"
//    let password = "password"
//
//    func testSecureEnclaveKeyCreate() async throws {
//        let wallet = try await SecureEnclaveKey.create(id: id, password: password, sync: false)
//        let reWallet = try await SecureEnclaveKey.get(id: id, password: password)
//        #expect(try wallet.publicKey() == try reWallet.publicKey())
//    }
//
//    func testSecureEnclaveKeyStore() async throws {
//        let wallet = try await SecureEnclaveKey.create()
//        try await wallet.store(id: id, password: password, sync: false)
//        let reWallet = try await SecureEnclaveKey.get(id: id, password: password)
//        #expect(try wallet.publicKey() == try reWallet.publicKey())
//    }
//}
