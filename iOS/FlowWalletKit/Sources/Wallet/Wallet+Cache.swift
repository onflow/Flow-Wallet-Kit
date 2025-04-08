//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 8/4/2025.
//

import Foundation
import Flow

extension Wallet: Cacheable {
    
    /// Prefix used for caching wallet data in storage
    static let cachePrefix: String = "Wallets"
    
    /// The type of data being cached for the wallet
    public typealias CachedData = [Flow.ChainID: [Flow.Account]]
    
    /// Storage mechanism used for caching
    public var storage: StorageProtocol {
        cacheStorage
    }
    
    /// Data to be cached
    public var cachedData: CachedData? {
        flowAccounts
    }

    /// Unique identifier for caching wallet data
    /// Combines the cache prefix with the wallet's type-specific ID
    public var cacheId: String {
        [Wallet.cachePrefix, type.id].joined(separator: "-")
    }
}
