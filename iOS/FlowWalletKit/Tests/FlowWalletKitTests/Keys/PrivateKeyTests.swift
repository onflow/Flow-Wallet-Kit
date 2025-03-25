import Foundation
import Testing
import Flow
@testable import FlowWalletKit
import Factory

struct PrivateKeyTests {
    
    init() {
        Container.shared.keychainStorage.register { MockStorage() }
    }
    
    @Test
    func testPublicKey() throws {
        // Given
        let privateKey = PrivateKey()
        
        // When
        let publicKey = privateKey.publicKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(publicKey != nil)
        if let key = publicKey {
            #expect(key.count == 64)
        }
    }
    
    @Test
    func testPrivateKey() throws {
        // Given
        let privateKey = PrivateKey()
        
        // When
        let privateKeyPrivateKey = privateKey.privateKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(privateKeyPrivateKey != nil)
        if let key = privateKeyPrivateKey {
            #expect(key.count == 32)
        }
    }
    
    @Test
    func testSigningData() throws {
        // Given
        let privateKey = PrivateKey()
        let dataToSign = "test data".data(using: .utf8)!
        
        // When
        let signature = try privateKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        
        // Then
        #expect(signature.count == 64)
    }
    
    @Test
    func testValidateSignature() {
        // Given
        let privateKey = PrivateKey()
        let message = "test message".data(using: .utf8)!
        let signature = Data(repeating: 3, count: 64)
        
        // When
        let isValid = privateKey.isValidSignature(signature: signature, message: message, signAlgo: .ECDSA_P256)
        
        // Then
        #expect(isValid == false)
    }
    
    @Test
    func testStoreOperation() throws {
        // Given
        let privateKey = PrivateKey()
        
        // When/Then - should not throw
        try privateKey.store(id: "test_id", password: "password")
    }
} 
