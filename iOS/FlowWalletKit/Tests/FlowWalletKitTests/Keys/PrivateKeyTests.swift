import Foundation
import Testing
import Flow
@testable import FlowWalletKit

struct PrivateKeyTests {
    
    @Test
    func testPublicKey() throws {
        // Given
        let mockPrivateKey = MockPrivateKey()
        
        // When
        let publicKey = mockPrivateKey.publicKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(publicKey != nil)
        if let key = publicKey {
            #expect(key.count == 65)
        }
    }
    
    @Test
    func testPrivateKey() throws {
        // Given
        let mockPrivateKey = MockPrivateKey()
        
        // When
        let privateKey = mockPrivateKey.privateKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(privateKey != nil)
        if let key = privateKey {
            #expect(key.count == 32)
        }
    }
    
    @Test
    func testSigningData() throws {
        // Given
        let mockPrivateKey = MockPrivateKey()
        let dataToSign = "test data".data(using: .utf8)!
        
        // When
        let signature = try mockPrivateKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        
        // Then
        #expect(signature.count == 64)
    }
    
    @Test
    func testSigningWithError() throws {
        // Given
        let mockPrivateKey = MockPrivateKey()
        mockPrivateKey.shouldThrowOnSign = true
        let dataToSign = "test data".data(using: .utf8)!
        
        // Then
        #expect(throws: WalletError.signError) {
            try mockPrivateKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        }
    }
    
    @Test
    func testValidateSignature() {
        // Given
        let mockPrivateKey = MockPrivateKey()
        let message = "test message".data(using: .utf8)!
        let signature = Data(repeating: 3, count: 64)
        
        // When
        let isValid = mockPrivateKey.isValidSignature(signature: signature, message: message, signAlgo: .ECDSA_P256)
        
        // Then
        #expect(isValid)
    }
    
    @Test
    func testStoreOperation() throws {
        // Given
        let mockPrivateKey = MockPrivateKey()
        
        // When/Then - should not throw
        try mockPrivateKey.store(id: "test_id", password: "password")
    }
} 
