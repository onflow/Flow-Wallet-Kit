//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 7/4/2025.
//

import Foundation
import Flow

// MARK: - Wallet Types

/// Represents different types of Flow walletss
public enum WalletType {
    /// A wallet backed by a cryptographic key (e.g., Secure Enclave, Seed Phrase, Private Key)
    case key(any KeyProtocol)
    
    // TODO: Replace it with FWAddress, support both flow and evm address
    /// A watch-only wallet that can only observe an address without signing capability
    case watch(Flow.Address)
    
    // TODO: Add hardware support eg. Ledger
//    case proxy()

    /// Prefix used for wallet identification in storage
    var idPrefix: String {
        switch self {
        case .key:
            return "Key"
        case .watch:
            return "Watch"
        }
    }

    /// Unique identifier for the wallet type, combining prefix and specific identifier
    /// For key-based wallets: "Key/{key.id}"
    /// For watch wallets: "Watch/{address.hex}"
    var id: String {
        switch self {
        case let .key(key):
            return [idPrefix, key.id].joined(separator: "/")
        case let .watch(address):
            return [idPrefix, address.hex].joined(separator: "/")
        }
    }

    /// Returns the associated key if the wallet is key-based, nil for watch-only wallets
    var key: (any KeyProtocol)? {
        switch self {
        case let .key(key):
            return key
        default:
            return nil
        }
    }
}