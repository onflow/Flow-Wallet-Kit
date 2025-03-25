import Foundation
import Flow
@testable import FlowWalletKit
import Factory

class MockSeedPhraseKey: KeyProtocol {
    var keyType: KeyType = .seedPhrase
    @Injected(\.keychainStorage) var storage
    var mockPublicKey: Data
    var mockPrivateKey: Data
    var signatureToReturn: Data
    var shouldThrowOnSign: Bool = false
    var errorToThrow: Error = WalletError.signError
    var mnemonic: String = "test test test test test test test test test test test test"
    var derivationPath: String = "m/44'/539'/0'/0/0"
    
    init(mockPublicKey: Data = Data(repeating: 1, count: 65),
         mockPrivateKey: Data = Data(repeating: 3, count: 32),
         signatureToReturn: Data = Data(repeating: 2, count: 64)) {
        self.mockPublicKey = mockPublicKey
        self.mockPrivateKey = mockPrivateKey
        self.signatureToReturn = signatureToReturn
    }
    
    static func create(_ advance: String) throws -> MockSeedPhraseKey {
        MockSeedPhraseKey()
    }
    
    static func create() throws -> MockSeedPhraseKey {
        MockSeedPhraseKey()
    }

    static func createAndStore(id: String, password: String) throws -> MockSeedPhraseKey {
        MockSeedPhraseKey()
    }
    
    static func get(id: String, password: String) throws -> MockSeedPhraseKey {
        MockSeedPhraseKey()
    }
    
    static func restore(secret: Data) throws -> MockSeedPhraseKey {
        MockSeedPhraseKey()
    }

    func store(id: String, password: String) throws {
        // Mock implementation
    }
    
    func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return mockPublicKey
    }
    
    func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return mockPrivateKey
    }
    
    func isValidSignature(signature: Data, message: Data, signAlgo: Flow.SignatureAlgorithm) -> Bool {
        return true // Always return true for testing
    }
    
    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data {
        if shouldThrowOnSign {
            throw errorToThrow
        }
        return signatureToReturn
    }
    
    func rawSign(data: Data, signAlgo: Flow.SignatureAlgorithm) throws -> Data {
        if shouldThrowOnSign {
            throw errorToThrow
        }
        return signatureToReturn
    }
}
