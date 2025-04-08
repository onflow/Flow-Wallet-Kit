//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 8/4/2025.
//

import Foundation

// MARK: - Cacheable Protocol Implementation

extension Account: Cacheable {
    
    /// The type of data being cached for the account
    public typealias CachedData = AccountCache
    
    /// Data to be cached
    public var cachedData: CachedData? {
        AccountCache(
            childs: childs,
            coa: coa
        )
    }
    
    /// Storage mechanism used for caching
    public var storage: StorageProtocol {
        cacheStorage
    }
    
    /// Unique identifier for caching account data
    public var cacheId: String {
        ["Account", chainID.name, account.address.hex].joined(separator: "-")
    }
    
}
