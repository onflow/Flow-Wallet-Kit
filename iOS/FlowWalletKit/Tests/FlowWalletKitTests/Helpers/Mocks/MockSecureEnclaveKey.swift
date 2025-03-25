import Foundation
import CryptoKit
import Flow
@testable import FlowWalletKit
import Factory

class MockSecureEnclaveKey: KeyProtocol {
    var keyType: KeyType = .secureEnclave
    @Injected(\.keychainStorage) var storage
    var mockPublicKey: Data
    var signatureToReturn: Data
    var shouldThrowOnSign: Bool = false
    var errorToThrow: Error = WalletError.signError
    
    init(mockPublicKey: Data = Data(repeating: 1, count: 32), signatureToReturn: Data = Data(repeating: 2, count: 64)) {
        self.mockPublicKey = mockPublicKey
        self.signatureToReturn = signatureToReturn
    }

    static func create(_ advance: String) throws -> MockSecureEnclaveKey {
        MockSecureEnclaveKey()
    }

    static func create() throws -> MockSecureEnclaveKey {
        MockSecureEnclaveKey()
    }

    static func createAndStore(id: String, password: String) throws -> MockSecureEnclaveKey {
        return MockSecureEnclaveKey(mockPublicKey: Data(repeating: 1, count: 32), signatureToReturn: Data(repeating: 2, count: 64))
    }
    
    static func get(id: String, password: String) throws -> MockSecureEnclaveKey {
        return MockSecureEnclaveKey(mockPublicKey: Data(repeating: 1, count: 32), signatureToReturn: Data(repeating: 2, count: 64))
    }
    
    static func restore(secret: Data) throws -> MockSecureEnclaveKey {
        return MockSecureEnclaveKey(mockPublicKey: Data(repeating: 1, count: 32), signatureToReturn: Data(repeating: 2, count: 64))
    }

    func store(id: String, password: String) throws {
        // Mock implementation
    }
    
    func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return mockPublicKey
    }
    
    func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data? {
        return nil // Simulating SecureEnclave behavior
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
    
    func create(storage: any FlowWalletKit.StorageProtocol) throws -> any FlowWalletKit.KeyProtocol {
        return self
    }
}
