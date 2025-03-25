import Foundation
import Testing
import Flow
@testable import FlowWalletKit
import CryptoKit

struct SecureEnclaveKeyTests {
    let secureEnclaveKey: SecureEnclaveKey
    
    init() throws {
        secureEnclaveKey = SecureEnclaveKey(key: try SecureEnclave.P256.Signing.PrivateKey())
    }
    
    @Test
    func testPublicKey() throws {
        // When
        let publicKey = secureEnclaveKey.publicKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(publicKey != nil)
        if let key = publicKey {
            #expect(key.count == 64)
        }
    }
    
    @Test
    func testSigningData() throws {
        let dataToSign = "test data".data(using: .utf8)!
        
        // When
        let signature = try secureEnclaveKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        
        // Then
        #expect(signature.count == 64)
    }
        
    @Test
    func testValidateSignature() {
        let message = "test message".data(using: .utf8)!
        let signature = Data(repeating: 3, count: 64)
        
        // When
        let isValid = secureEnclaveKey.isValidSignature(signature: signature, message: message, signAlgo: .ECDSA_P256)
        
        // Then
        #expect(isValid == false)
    }
    
    @Test
    func testStoreOperation() throws {
        // When/Then - should not throw
        try secureEnclaveKey.store(id: "test_id", password: "password")
    }
    
    @Test
    func testNoPrivateKeyAccess() {
        // When
        let privateKey = secureEnclaveKey.privateKey(signAlgo: .ECDSA_P256)
        
        // Then - should be nil since Secure Enclave doesn't expose private keys
        #expect(privateKey == nil)
    }
} 
