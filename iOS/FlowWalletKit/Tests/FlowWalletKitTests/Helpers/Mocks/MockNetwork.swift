import Foundation
import Flow
@testable import FlowWalletKit

struct MockNetwork {
    static var shouldThrow = false
    static var errorToThrow: Error = WalletError.keyIndexerRequestFailed
    static var mockKeyIndexerResponse: KeyIndexerResponse?
    static var mockFlowAccounts: [Flow.Account] = []
    static var mockKeyIndexerAccounts: [KeyIndexerResponse.Account] = []
    
    static func resetMocks() {
        shouldThrow = false
        errorToThrow = WalletError.keyIndexerRequestFailed
        mockKeyIndexerResponse = nil
        mockFlowAccounts = []
        mockKeyIndexerAccounts = []
    }
    
    static func findAccount(publicKey: String, chainID: Flow.ChainID) async throws -> KeyIndexerResponse {
        if shouldThrow {
            throw errorToThrow
        }
        
        if let response = mockKeyIndexerResponse {
            return response
        }
        
        // Create a default mock response
        return KeyIndexerResponse(
            publicKey: publicKey,
            accounts: mockKeyIndexerAccounts.isEmpty ? 
                [KeyIndexerResponse.Account(
                    address: "0x1234567890abcdef",
                    keyId: 0,
                    weight: 1000,
                    sigAlgo: 1,
                    hashAlgo: 3,
                    signing: .ECDSA_P256,
                    hashing: .SHA3_256,
                    isRevoked: false
                )] : mockKeyIndexerAccounts
        )
    }
    
    static func findAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [KeyIndexerResponse.Account] {
        if shouldThrow {
            throw errorToThrow
        }
        
        if !mockKeyIndexerAccounts.isEmpty {
            return mockKeyIndexerAccounts
        }
        
        return [
            KeyIndexerResponse.Account(
                address: "0x1234567890abcdef",
                keyId: 0,
                weight: 1000,
                sigAlgo: 1,
                hashAlgo: 3,
                signing: .ECDSA_P256,
                hashing: .SHA3_256,
                isRevoked: false
            )
        ]
    }
    
    static func findFlowAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [Flow.Account] {
        if shouldThrow {
            throw errorToThrow
        }
        
        if !mockFlowAccounts.isEmpty {
            return mockFlowAccounts
        }
        
        return [
            Flow.Account(
                address: Flow.Address(hex: "0x1234567890abcdef"),
                keys: [
                    Flow.AccountKey(
                        index: 0,
                        publicKey: Flow.PublicKey(hex: publicKey),
                        signAlgo: .ECDSA_P256,
                        hashAlgo: .SHA3_256,
                        weight: 1000,
                        revoked: false
                    )
                ]
            )
        ]
    }
} 