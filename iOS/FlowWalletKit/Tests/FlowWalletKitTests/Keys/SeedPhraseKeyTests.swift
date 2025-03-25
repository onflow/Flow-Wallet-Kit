import Foundation
import Testing
import Flow
@testable import FlowWalletKit
import WalletCore
import Factory

struct SeedPhraseKeyTests {
    var mockHDWallet: HDWallet!
    
    init() {
        mockHDWallet = HDWallet(strength: defaultSeedPhraseLength.strength, passphrase: "")!
        Container.shared.keychainStorage.register { MockStorage() }
    }
    
    @Test
    func testPublicKey() throws {
        // Given
        let seedPhraseKey = SeedPhraseKey(hdWallet: mockHDWallet)
        
        // When
        let publicKey = seedPhraseKey.publicKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(publicKey != nil)
        if let key = publicKey {
            #expect(key.count == 64)
        }
    }
    
    @Test
    func testPrivateKey() throws {
        // Given
        let seedPhraseKey = SeedPhraseKey(hdWallet: mockHDWallet)
        
        // When
        let privateKey = seedPhraseKey.privateKey(signAlgo: .ECDSA_P256)
        
        // Then
        #expect(privateKey != nil)
        if let key = privateKey {
            #expect(key.count == 32)
        }
    }
    
    @Test
    func testSigningData() throws {
        // Given
        let seedPhraseKey = SeedPhraseKey(hdWallet: mockHDWallet)
        let dataToSign = "test data".data(using: .utf8)!
        
        // When
        let signature = try seedPhraseKey.sign(data: dataToSign, signAlgo: .ECDSA_P256, hashAlgo: .SHA2_256)
        
        // Then
        #expect(signature.count == 64)
    }
        
    @Test
    func testValidateSignature() {
        // Given
        let seedPhraseKey = SeedPhraseKey(hdWallet: mockHDWallet)
        let message = "test message".data(using: .utf8)!
        let signature = Data(repeating: 3, count: 64)
        
        // When
        let isValid = seedPhraseKey.isValidSignature(signature: signature, message: message, signAlgo: .ECDSA_P256)
        
        // Then
        #expect(isValid == false)
    }
    
    @Test
    func testStoreOperation() throws {
        // Given
        let seedPhraseKey = SeedPhraseKey(hdWallet: mockHDWallet)
        
        // When/Then - should not throw
        try seedPhraseKey.store(id: "test_id", password: "password")
    }
} 
