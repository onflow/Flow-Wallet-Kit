import Foundation
import FlowWalletKit

class MockStorage: StorageProtocol {
    var storage: [String: Data] = [:]
    var errorToThrow: Error?
    
    var allKeys: [String] {
        return Array(storage.keys)
    }
    
    func findKey(_ keyword: String) throws -> [String] {
        if let error = errorToThrow {
            throw error
        }
        return storage.keys.filter { $0.contains(keyword) }
    }
    
    func get(_ key: String) throws -> Data? {
        if let error = errorToThrow {
            throw error
        }
        return storage[key]
    }
    
    func set(_ key: String, value: Data) throws {
        if let error = errorToThrow {
            throw error
        }
        storage[key] = value
    }
    
    func remove(_ key: String) throws {
        if let error = errorToThrow {
            throw error
        }
        storage.removeValue(forKey: key)
    }
    
    func removeAll() throws {
        if let error = errorToThrow {
            throw error
        }
        storage.removeAll()
    }
} 