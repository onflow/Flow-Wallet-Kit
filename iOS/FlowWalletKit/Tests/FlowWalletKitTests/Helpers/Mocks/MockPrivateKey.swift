import Foundation
import Flow
@testable import FlowWalletKit
import Factory

class MockPrivateKey: KeyProtocol {
    @Injected(\.keychainStorage) var storage
    var keyType: KeyType = .privateKey
    var mockPublicKey: Data
    var mockPrivateKey: Data
    var signatureToReturn: Data
    var shouldThrowOnSign: Bool = false
    var errorToThrow: Error = WalletError.signError
    
    init(mockPublicKey: Data = Data(repeating: 1, count: 65), 
         mockPrivateKey: Data = Data(repeating: 3, count: 32),
         signatureToReturn: Data = Data(repeating: 2, count: 64)) {
        self.mockPublicKey = mockPublicKey
        self.mockPrivateKey = mockPrivateKey
        self.signatureToReturn = signatureToReturn
    }
    
    static func create(_ advance: String) throws -> MockPrivateKey {
        MockPrivateKey()
    }

    static func create() throws -> MockPrivateKey {
        MockPrivateKey()
    }

    static func createAndStore(id: String, password: String) throws -> MockPrivateKey {
        MockPrivateKey()
    }
    
    static func get(id: String, password: String) throws -> MockPrivateKey {
        MockPrivateKey()
    }
    
    static func restore(secret: Data) throws -> MockPrivateKey {
        MockPrivateKey()
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
