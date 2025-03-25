import Foundation
import Testing
import Flow
@testable import FlowWalletKit

struct WalletTests {
    
    @Test
    func testWalletInitialization() {
        // Given
        let mockKey = MockPrivateKey()
        let walletType = WalletType.key(mockKey)
        
        // When
        let wallet = Wallet(type: walletType, networks: [.testnet])
        
        // Then
        #expect(wallet.type.id.starts(with: "Key/"))
        #expect(wallet.networks.count == 1)
        #expect(wallet.networks.contains(.testnet))
    }
    
    @Test
    func testAddNetwork() {
        // Given
        let mockKey = MockPrivateKey()
        let walletType = WalletType.key(mockKey)
        let wallet = Wallet(type: walletType, networks: [.testnet])
        
        // When
        wallet.addNetwork(.mainnet)
        
        // Then
        #expect(wallet.networks.count == 2)
        #expect(wallet.networks.contains(.testnet))
        #expect(wallet.networks.contains(.mainnet))
    }
    
    @Test
    func testWatchWalletInitialization() {
        // Given
        let address = Flow.Address(hex: "0x1234567890abcdef")
        let walletType = WalletType.watch(address)
        
        // When
        let wallet = Wallet(type: walletType)
        
        // Then
        #expect(wallet.type.id.starts(with: "Watch/"))
        #expect(wallet.type.id.contains(address.hex))
    }
} 
