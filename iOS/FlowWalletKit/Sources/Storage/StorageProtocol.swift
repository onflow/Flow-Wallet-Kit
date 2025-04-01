/// FlowWalletKit - Storage Protocol
///
/// This module defines the core protocol for secure data storage in FlowWalletKit.
/// It provides a unified interface for storing and retrieving sensitive data like keys,
/// allowing different storage backends (e.g., Keychain, UserDefaults, etc.) to be used
/// interchangeably.

import Foundation

/// Protocol defining the interface for secure data storage
///
/// This protocol provides methods for:
/// - Storing and retrieving binary data
/// - Managing storage keys
/// - Searching stored items
/// - Clearing storage
public protocol StorageProtocol {
    // Note: These associated types are currently commented out but may be used
    // in future versions for type-safe storage
//    associatedtype Key
//    associatedtype Value

    /// All keys currently stored in the storage
    var allKeys: [String] { get }

    /// Find keys matching a keyword
    /// - Parameter keyword: Search term to match against keys
    /// - Returns: Array of matching keys
    /// - Throws: Storage access errors
    func findKey(_ keyword: String) throws -> [String]

    /// Retrieve data for a key
    /// - Parameter key: Key to lookup
    /// - Returns: Stored data, or nil if not found
    /// - Throws: Storage access errors
    func get(_ key: String) throws -> Data?

    /// Store data for a key
    /// - Parameters:
    ///   - key: Key to store under
    ///   - value: Data to store
    /// - Throws: Storage access or capacity errors
    func set(_ key: String, value: Data) throws

    /// Remove data for a key
    /// - Parameter key: Key to remove
    /// - Throws: Storage access errors
    func remove(_ key: String) throws

    /// Remove all stored data
    /// - Throws: Storage access errors
    func removeAll() throws
}
