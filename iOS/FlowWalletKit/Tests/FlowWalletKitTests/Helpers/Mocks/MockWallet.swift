import Foundation
import Flow
@testable import FlowWalletKit

class MockWallet {
    var type: WalletType
    var networks: Set<Flow.ChainID>
    var mockAccounts: [Flow.ChainID: [Account]]
    var mockFlowAccounts: [Flow.ChainID: [Flow.Account]]
    var shouldThrowOnFetch: Bool = false
    var errorToThrow: Error = WalletError.loadCacheFailed
    
    init(type: WalletType, 
         networks: Set<Flow.ChainID> = [.mainnet, .testnet],
         mockAccounts: [Flow.ChainID: [Account]] = [:],
         mockFlowAccounts: [Flow.ChainID: [Flow.Account]] = [:]) {
        self.type = type
        self.networks = networks
        self.mockAccounts = mockAccounts
        self.mockFlowAccounts = mockFlowAccounts
    }
    
    func fetchAccount() async {
        // Mock implementation
    }
    
    func fetchAllNetworkAccounts() async throws -> [Flow.ChainID: [Account]] {
        if shouldThrowOnFetch {
            throw errorToThrow
        }
        return mockAccounts
    }
    
    func account(chainID: Flow.ChainID) async throws -> [Flow.Account] {
        if shouldThrowOnFetch {
            throw errorToThrow
        }
        return mockFlowAccounts[chainID] ?? []
    }
    
    func fullAccount(chainID: Flow.ChainID) async throws -> [Flow.Account] {
        if shouldThrowOnFetch {
            throw errorToThrow
        }
        return mockFlowAccounts[chainID] ?? []
    }
    
    func cache() throws {
        if shouldThrowOnFetch {
            throw errorToThrow
        }
        // Mock implementation
    }
    
    func loadCahe() throws {
        if shouldThrowOnFetch {
            throw errorToThrow
        }
        // Mock implementation
    }
} 