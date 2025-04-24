/// FlowWalletKit - Caching Protocol
///
/// This protocol defines the caching behavior for Flow wallet components,
/// allowing consistent state persistence across the application.

import Foundation
import Flow

/// Wrapper for cached data with timestamp and expiration
public struct CacheWrapper<T: Codable>: Codable {
    /// The actual cached data
    let data: T
    /// When the data was cached
    let timestamp: Date
    /// Time interval after which the cache expires (nil means never)
    let expiresIn: TimeInterval?
    
    /// Whether the cached data has expired
    var isExpired: Bool {
        guard let expiresIn = expiresIn else {
            return false // Never expires
        }
        return Date().timeIntervalSince(timestamp) > expiresIn
    }
}

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
    
    /// Time interval after which the cache expires (nil means never)
    var cacheExpiration: TimeInterval? { get }
    
    /// Cache the current state
    /// - Parameter expiresIn: Optional custom expiration time
    /// - Throws: Error if caching fails
    func cache(expiresIn: TimeInterval?) throws
    
    /// Load state from cache
    /// - Parameter ignoreExpiration: Whether to ignore expiration time
    /// - Throws: Error if loading fails
    /// - Returns: Cached data if available and not expired
    func loadCache(ignoreExpiration: Bool) throws -> CachedData?
    
    /// Delete cached state
    /// - Throws: Error if deletion fails
    func deleteCache() throws
}

/// Default implementation for common caching operations
public extension Cacheable {
    /// Default cache expiration (never expires)
    var cacheExpiration: TimeInterval? {
        nil
    }
    
    /// Default implementation of cache operation
    func cache(expiresIn: TimeInterval? = nil) throws {
        guard let data = cachedData else { return }
        let wrapper = CacheWrapper(
            data: data,
            timestamp: Date(),
            expiresIn: expiresIn ?? cacheExpiration
        )
        let encoded = try JSONEncoder().encode(wrapper)
        try storage.set(cacheId, value: encoded)
    }
    
    /// Default implementation of cache operation without expiration
    func cache() throws {
        try cache(expiresIn: nil)
    }
    
    /// Default implementation of load operation
    func loadCache(ignoreExpiration: Bool = false) throws -> CachedData? {
        guard let data = try storage.get(cacheId) else {
            return nil
        }

        do {
            let wrapper = try JSONDecoder().decode(CacheWrapper<CachedData>.self, from: data)
            // Check expiration unless ignored
            if !ignoreExpiration && wrapper.isExpired {
                try deleteCache()
                return nil
            }
            return wrapper.data
        } catch {
            throw FWKError.cacheDecodeFailed
        }
    }
    
    /// Default implementation of delete operation
    /// Removes the cached data for this instance
    /// - Throws: Error if deletion fails
    func deleteCache() throws {
        try storage.remove(cacheId)
    }
}