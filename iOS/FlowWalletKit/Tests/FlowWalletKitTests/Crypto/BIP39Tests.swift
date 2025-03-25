import Foundation
import Testing
@testable import FlowWalletKit

struct BIP39Tests {
    
    @Test
    func testSeedPhraseGeneration() {
        // Given
        let entropy = Data(repeating: 1, count: 16) // 128 bits of entropy
        
        // When
        let mnemonic = BIP39.generate(passphrase: String(data: entropy, encoding: .utf8)!)
        
        // Then
        #expect(mnemonic != nil)
        if let phrase = mnemonic {
            let words = phrase.split(separator: " ")
            #expect(words.count == 12) // 128 bits of entropy should produce 12 words
        }
    }
    
    @Test
    func testSeedFromMnemonic() {
        // Given
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        
        // When
        let seed = BIP39.generate(.twelve, passphrase: mnemonic)
        
        // Then
        #expect(seed != nil)
        if let seedData = seed {
            #expect(seedData.count == 64) // Seed should be 512 bits (64 bytes)
            
            // Compare with known test vector result (first 16 bytes for verification)
            let expectedHexStart = "c55257c360c07c72029aebc1b53c05ed"
            let actualHexStart = seedData.prefix(16).lowercased()
            #expect(actualHexStart == expectedHexStart)
        }
    }
    
    @Test
    func testInvalidMnemonic() {
        // Given
        let invalidMnemonic = "invalid words that are not in the bip39 wordlist"
        
        // When
        let isValid = BIP39.isValid(mnemonic: invalidMnemonic)
        
        // Then
        #expect(isValid == false)
    }
    
    @Test
    func testDifferentPassphraseProducesDifferentSeed() {
        // Given
        let passphrase1 = "passphrase1"
        let passphrase2 = "passphrase2"
        
        // When
        let seed1 = BIP39.generate(passphrase: passphrase1)
        let seed2 = BIP39.generate(passphrase: passphrase2)
        
        // Then
        #expect(seed1 != nil)
        #expect(seed2 != nil)
        #expect(seed1 != seed2)
    }
} 
