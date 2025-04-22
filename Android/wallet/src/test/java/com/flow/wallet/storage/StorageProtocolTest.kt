package com.flow.wallet.storage

import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.test.assertFailsWith

/**
 * Base test class for storage protocol implementations
 * Provides common test cases that should be implemented by all storage backends
 */
abstract class StorageProtocolTest {
    abstract fun createStorage(): StorageProtocol

    fun testBasicOperations() {
        val storage = createStorage()
        val testKey = "test-key"
        val testValue = "test-value".toByteArray()

        // Test set and get
        storage.set(testKey, testValue)
        val retrieved = storage.get(testKey)
        assertNotNull(retrieved)
        assertTrue(testValue.contentEquals(retrieved))

        // Test remove
        storage.remove(testKey)
        assertNull(storage.get(testKey))
    }

    fun testMultipleKeys() {
        val storage = createStorage()
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()

        // Store multiple keys
        keys.forEach { storage.set(it, value) }

        // Test allKeys
        val storedKeys = storage.allKeys
        assertTrue(storedKeys.containsAll(keys))

        // Test findKey
        val foundKeys = storage.findKey("key")
        assertTrue(foundKeys.containsAll(keys))

        // Test removeAll
        storage.removeAll()
        assertTrue(storage.allKeys.isEmpty())
    }

    fun testErrorHandling() {
        val storage = createStorage()
        val nonExistentKey = "non-existent"

        // Test get non-existent key
        assertFailsWith<WalletError> {
            storage.get(nonExistentKey)
        }

        // Test remove non-existent key
        assertFailsWith<WalletError> {
            storage.remove(nonExistentKey)
        }

        // Test removeAll error handling
        val errorStorage = object : StorageProtocol {
            override val allKeys: List<String> = emptyList()
            override fun findKey(keyword: String): List<String> = emptyList()
            override fun get(key: String): ByteArray? = null
            override fun set(key: String, data: ByteArray) {}
            override fun remove(key: String) {}
            override fun removeAll() = throw WalletError(0, "Test error")
            override val securityLevel: SecurityLevel
                get() = SecurityLevel.STANDARD
        }
        assertFailsWith<WalletError> {
            errorStorage.removeAll()
        }
    }

    fun testSecurityLevel() {
        val storage = createStorage()
        assertNotNull(storage.securityLevel)
    }

    fun testCaseSensitivity() {
        val storage = createStorage()
        val upperKey = "TEST-KEY"
        val lowerKey = "test-key"
        val value = "test".toByteArray()

        // Test case sensitivity in set/get
        storage.set(upperKey, value)
        assertNotNull(storage.get(upperKey))
        assertNull(storage.get(lowerKey))

        // Test case sensitivity in findKey
        storage.set(lowerKey, value)
        val foundKeys = storage.findKey("test")
        assertTrue(foundKeys.contains(lowerKey))
        assertFalse(foundKeys.contains(upperKey))
    }

    fun testEmptyStorage() {
        val storage = createStorage()
        
        // Test allKeys with empty storage
        assertTrue(storage.allKeys.isEmpty())
        
        // Test findKey with empty storage
        assertTrue(storage.findKey("any").isEmpty())
        
        // Test get with empty storage
        assertNull(storage.get("any"))
    }
} 