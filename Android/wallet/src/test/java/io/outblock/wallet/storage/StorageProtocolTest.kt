package io.outblock.wallet.storage

import io.outblock.wallet.errors.WalletError
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
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

        // Test exists
        assertTrue(storage.exists(testKey))

        // Test remove
        storage.remove(testKey)
        assertFalse(storage.exists(testKey))
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
    }

    fun testSecurityLevel() {
        val storage = createStorage()
        assertNotNull(storage.securityLevel)
    }
} 