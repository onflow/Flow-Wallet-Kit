import Foundation
import Testing
import Flow
@testable import FlowWalletKit

struct SecureEnclaveKeyTests {
    
    @Test
    func testPublicKey() throws {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        
        // When
        let publicKey = mockSecureEnclaveKey.publicKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(publicKey != nil)
        if let key = publicKey {
            #expect(key.count == 32)
        }
    }
    
    @Test
    func testSigningData() throws {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        let dataToSign = "test data".data(using: .utf8)!
        
        // When
        let signature = try mockSecureEnclaveKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        
        // Then
        #expect(signature.count == 64)
    }
    
    @Test
    func testSigningWithError() throws {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        mockSecureEnclaveKey.shouldThrowOnSign = true
        let dataToSign = "test data".data(using: .utf8)!
        
        // Then
        #expect(throws: WalletError.signError) {
            try mockSecureEnclaveKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        }
    }
    
    @Test
    func testValidateSignature() {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        let message = "test message".data(using: .utf8)!
        let signature = Data(repeating: 3, count: 64)
        
        // When
        let isValid = mockSecureEnclaveKey.isValidSignature(signature: signature, message: message, signAlgo: .ECDSA_P256)
        
        // Then
        #expect(isValid)
    }
    
    @Test
    func testStoreOperation() throws {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        
        // When/Then - should not throw
        try mockSecureEnclaveKey.store(id: "test_id", password: "password")
    }
    
    @Test
    func testNoPrivateKeyAccess() {
        // Given
        let mockSecureEnclaveKey = MockSecureEnclaveKey()
        
        // When
        let privateKey = mockSecureEnclaveKey.privateKey(signAlgo: .ECDSA_P256)
        
        // Then - should be nil since Secure Enclave doesn't expose private keys
        #expect(privateKey == nil)
    }
} 
