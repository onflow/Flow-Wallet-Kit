/// FlowWalletKit - Caching Protocol
///
/// This protocol defines the caching behavior for Flow wallet components,
/// allowing consistent state persistence across the application.

import Foundation
import Flow

/// Protocol defining caching behavior for wallet components
public protocol Cacheable {
    /// The type of data being cached
    associatedtype CachedData: Codable
    
    /// Storage mechanism used for caching
    var storage: StorageProtocol { get }
    
    /// Unique identifier for the cached data
    var cacheId: String { get }
    
    /// The data to be cached
    var cachedData: CachedData? { get }
    
    /// Cache the current state
    /// - Throws: Error if caching fails
    func cache() throws
    
    /// Load state from cache
    /// - Throws: Error if loading fails
    func loadCache() throws -> CachedData
}

/// Default implementation for common caching operations
public extension Cacheable {
    /// Default implementation of cache operation
    func cache() throws {
        guard let data = cachedData else { return }
        let encoded = try JSONEncoder().encode(data)
        try storage.set(cacheId, value: encoded)
    }
    
    /// Default implementation of load operation
    func loadCache() throws -> CachedData {
        guard let data = try storage.get(cacheId) else {
            throw WalletError.loadCacheFailed
        }
        return try JSONDecoder().decode(CachedData.self, from: data)
    }
} 
