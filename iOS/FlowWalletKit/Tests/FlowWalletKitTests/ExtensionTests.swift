import Foundation
import Testing
import Flow
@testable import FlowWalletKit

struct ExtensionTests {
    
    func testHashAlgorithmExtension() throws {
        // Given
        let testData = "test data".data(using: .utf8)!
        
        // When
        let sha2Result = try Flow.HashAlgorithm.SHA2_256.hash(data: testData)
        let sha3Result = try Flow.HashAlgorithm.SHA3_256.hash(data: testData)
        
        // Then
        #expect(sha2Result.count == 32) // SHA256 produces 32 bytes
        #expect(sha3Result.count == 32) // SHA3_256 produces 32 bytes
        #expect(sha2Result != sha3Result) // Different algorithms should produce different results
    }
    
    func testUnsupportedHashAlgorithm() {
        // Given
        let testData = "test data".data(using: .utf8)!
        let unsupportedAlgo = Flow.HashAlgorithm.unknown
        
        // Then
        #expect(throws:WalletError.unsupportHashAlgorithm) {
            try unsupportedAlgo.hash(data: testData)
        }
    }
    
//    func testSignatureAlgorithmToCurve() {
//        // Given/When
//        let p256Curve = Flow.SignatureAlgorithm.ECDSA_P256.WCCurve
//        let secp256k1Curve = Flow.SignatureAlgorithm.ECDSA_SECP256k1.WCCurve
//        let unknownCurve = Flow.SignatureAlgorithm.unknown.WCCurve
//        
//        // Then
//        #expect(p256Curve == Curve.nist256p1)
//        #expect(secp256k1Curve == Curve.secp256k1)
//        #expect(unknownCurve == nil)
//    }
    
    func testStringDropPrefix() {
        // Given
        let stringWithPrefix = "0x1234567890abcdef"
        let stringWithoutPrefix = "1234567890abcdef"
        
        // When
        let result1 = stringWithPrefix.dropPrefix("0x")
        let result2 = stringWithoutPrefix.dropPrefix("0x")
        
        // Then
        #expect(result1 == "1234567890abcdef")
        #expect(result2 == "1234567890abcdef")
    }
    
    func testChainIDKeyIndexerURL() {
        // Given
        let publicKey = "1234567890abcdef"
        
        // When
        let mainnetURL = Flow.ChainID.mainnet.keyIndexer(with: publicKey)
        let testnetURL = Flow.ChainID.testnet.keyIndexer(with: publicKey)
        let emulatorURL = Flow.ChainID.emulator.keyIndexer(with: publicKey)
        
        // Then
        #expect(mainnetURL?.absoluteString == "https://production.key-indexer.flow.com/key/1234567890abcdef")
        #expect(testnetURL?.absoluteString == "https://staging.key-indexer.flow.com/key/1234567890abcdef")
        #expect(emulatorURL == nil) // Emulator doesn't have a key indexer URL
    }
} 
