/// FlowWalletKit - Keychain Storage Implementation
///
/// This module implements the StorageProtocol using Apple's Keychain Services.
/// It provides secure storage for sensitive data like cryptographic keys and credentials,
/// with support for:
/// - iCloud keychain synchronization
/// - Access group sharing between apps
/// - Device-specific restrictions
/// - Biometric and passcode protection

import Foundation
import KeychainAccess

/// Implementation of StorageProtocol using Apple's Keychain
/// 
/// This class provides secure storage using the iOS Keychain, with features like:
/// - Configurable synchronization with iCloud Keychain
/// - Sharing between apps via access groups
/// - Device-specific restrictions
/// - Automatic encryption of stored data
public class KeychainStorage: StorageProtocol {
    /// Service identifier for the keychain items
    let service: String
    /// Label for keychain items (shown in Settings)
    let label: String
    /// Whether items should sync with iCloud Keychain
    let synchronizable: Bool
    /// Optional access group for sharing between apps
    let accessGroup: String?
    /// Underlying keychain implementation
    var keychain: Keychain

    /// Initialize keychain storage
    /// - Parameters:
    ///   - service: Service identifier for keychain items
    ///   - label: User-visible label for keychain items
    ///   - synchronizable: Whether items should sync with iCloud
    ///   - accessGroup: Optional group for sharing between apps
    ///   - deviceOnly: Whether items should be restricted to this device
    public init(
        service: String,
        label: String,
        synchronizable: Bool,
        accessGroup: String? = nil,
        deviceOnly: Bool = false
    ) {
        self.service = service
        self.label = label
        self.synchronizable = synchronizable
        self.accessGroup = accessGroup
        if let accessGroup {
            keychain = Keychain(service: service, accessGroup: accessGroup)
                .label(label)
                .synchronizable(synchronizable)
                .accessibility( deviceOnly ? .whenUnlockedThisDeviceOnly : .whenUnlocked)
                
        } else {
            keychain = Keychain(service: service)
                .label(label)
                .synchronizable(synchronizable)
                .accessibility( deviceOnly ? .whenUnlockedThisDeviceOnly : .whenUnlocked)
        }
    }

    /// All keys currently stored in the keychain
    public var allKeys: [String] {
        keychain.allKeys()
    }
    
    /// Find keys matching a keyword
    /// - Parameter keyword: Search term to match against keys
    /// - Returns: Array of matching keys
    /// - Throws: Keychain access errors
    public func findKey(_ keyword: String) throws -> [String] {
        return allKeys.filter{ key in
            key.contains(keyword)
        }
    }

    /// Retrieve data for a key from the keychain
    /// - Parameter key: Key to lookup
    /// - Returns: Stored data, or nil if not found
    /// - Throws: Keychain access errors
    public func get(_ key: String) throws -> Data? {
        try keychain.getData(key)
    }

    /// Remove data for a key from the keychain
    /// - Parameter key: Key to remove
    /// - Throws: Keychain access errors
    public func remove(_ key: String) throws {
        try keychain.remove(key)
    }

    /// Remove all stored data from the keychain
    /// - Throws: Keychain access errors
    public func removeAll() throws {
        try keychain.removeAll()
    }

    /// Store data for a key in the keychain
    /// - Parameters:
    ///   - key: Key to store under
    ///   - value: Data to store
    /// - Throws: Keychain access or capacity errors
    public func set(_ key: String, value: Data) throws {
        try keychain.set(value, key: key, ignoringAttributeSynchronizable: !synchronizable)
    }
}
