import Foundation
import Testing
@testable import FlowWalletKit
import KeychainAccess

//TODO: these are failing because we don't have keychain access in SPM tests.  (technically this would be an "integration test", but stubbing the Keychain makes KeychainStorage do very little)

struct KeychainStorageTests {
    
    @Test
    func testSetAndGetData() throws {
        // Given
        let storage = KeychainStorage(service: "com.test.temp", label: "TempTest", synchronizable: false)
        try? storage.removeAll() // Clear any previous test data
        
        let key = "testKey"
        let testData = "testValue".data(using: .utf8)!
        
        // When
        try storage.set(key, value: testData)
        let retrievedData = try storage.get(key)
        
        // Then
        #expect(retrievedData != nil)
        if let data = retrievedData {
            #expect(data == testData)
        }
        
        // Clean up
        try storage.removeAll()
    }
    
    @Test
    func testRemoveKey() throws {
        // Given
        let storage = KeychainStorage(service: "com.test.temp", label: "TempTest", synchronizable: false)
        try? storage.removeAll() // Clear any previous test data
        
        let key = "testKeyToRemove"
        let testData = "testValue".data(using: .utf8)!
        try storage.set(key, value: testData)
        
        // When
        try storage.remove(key)
        let retrievedData = try storage.get(key)
        
        // Then
        #expect(retrievedData == nil)
    }
    
    @Test
    func testFindKey() throws {
        // Given
        let storage = KeychainStorage(service: "com.test.temp", label: "TempTest", synchronizable: false)
        try? storage.removeAll() // Clear any previous test data
        
        try storage.set("prefix_key1", value: Data())
        try storage.set("prefix_key2", value: Data())
        try storage.set("other_key", value: Data())
        
        // When
        let keys = try storage.findKey("prefix")
        
        // Then
        #expect(keys.count == 2)
        #expect(keys.contains("prefix_key1"))
        #expect(keys.contains("prefix_key2"))
        #expect(!keys.contains("other_key"))
        
        // Clean up
        try storage.removeAll()
    }
    
    @Test
    func testRemoveAll() throws {
        // Given
        let storage = KeychainStorage(service: "com.test.temp", label: "TempTest", synchronizable: false)
        
        try storage.set("key1", value: Data())
        try storage.set("key2", value: Data())
        
        // When
        try storage.removeAll()
        
        // Then
        #expect(storage.allKeys.isEmpty)
    }
} 
