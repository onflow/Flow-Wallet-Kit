//
//  File.swift
//
//
//  Created by Hao Fu on 22/7/2024.
//

import Foundation
import KeychainAccess

//TODO: should this be an actor?
//TODO: don't make implementations public, have public factories that make them and return protocol.  StorageProtocol prob doesn't need to be public though.
public class KeychainStorage: StorageProtocol {
    let service: String
    let label: String
    let synchronizable: Bool
    let accessGroup: String?
    var keychain: Keychain

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

    public var allKeys: [String] {
        keychain.allKeys()
    }
    
    public func findKey(_ keyword: String) throws -> [String] {
        return allKeys.filter{ key in
            key.contains(keyword)
        }
    }

    public func get(_ key: String) throws -> Data? {
        try keychain.getData(key)
    }

    public func remove(_ key: String) throws {
        try keychain.remove(key)
    }

    public func removeAll() throws {
        try keychain.removeAll()
    }

    public func set(_ key: String, value: Data) throws {
        try keychain.set(value, key: key, ignoringAttributeSynchronizable: !synchronizable)
    }
}
