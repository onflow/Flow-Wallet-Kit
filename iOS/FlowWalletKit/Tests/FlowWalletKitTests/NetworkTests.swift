import Foundation
import Testing
import Flow
@testable import FlowWalletKit

struct NetworkTests {
    
    func testFindAccount() async throws {
        // This test would require network access, so we'll focus on verifying that
        // the correct URL is constructed for the key indexer
        
        // Given
        let testPublicKey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        let chainID = Flow.ChainID.testnet
        
        // When
        guard let url = chainID.keyIndexer(with: testPublicKey) else {
            #expect(false, "Failed to construct key indexer URL")
            return
        }
        
        // Then
        #expect(url.absoluteString.contains(testPublicKey))
        #expect(url.absoluteString.contains("testnet"))
    }
    
    func testKeyIndexerResponseAccountMapping() {
        // Given
        let publicKey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        let keyIndexerAccounts = [
            KeyIndexerResponse.Account(
                address: "0x1234",
                keyId: 0,
                weight: 1000,
                sigAlgo: 1,
                hashAlgo: 3,
                signing: .ECDSA_P256,
                hashing: .SHA3_256,
                isRevoked: false
            ),
            KeyIndexerResponse.Account(
                address: "0x5678",
                keyId: 0,
                weight: 1000,
                sigAlgo: 1,
                hashAlgo: 3,
                signing: .ECDSA_P256,
                hashing: .SHA3_256,
                isRevoked: false
            )
        ]
        
        let response = KeyIndexerResponse(publicKey: publicKey, accounts: keyIndexerAccounts)
        
        // When
        let flowAccounts = response.accountResponse
        
        // Then
        #expect(flowAccounts.count == 2)
        #expect(flowAccounts[0].address.hex == "0x1234")
        #expect(flowAccounts[1].address.hex == "0x5678")
        #expect(flowAccounts[0].keys.count == 1)
        #expect(flowAccounts[0].keys[0].publicKey.hex == publicKey)
    }
} 
