package com.flow.wallet.storage

import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InMemoryStorageTest : StorageProtocolTest() {
    override fun createStorage(): StorageProtocol {
        return InMemoryStorage()
    }

    @Test
    fun testBasicOperations() {
        super.testBasicOperations()
    }

    @Test
    fun testMultipleKeys() {
        super.testMultipleKeys()
    }

    @Test
    fun testErrorHandling() {
        super.testErrorHandling()
    }

    @Test
    fun testSecurityLevel() {
        val storage = createStorage()
        assertEquals(SecurityLevel.IN_MEMORY, storage.securityLevel)
    }

    @Test
    fun testConcurrentAccess() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Simulate concurrent access
        val threads = List(10) {
            Thread {
                storage.set(key, value)
                storage.get(key)
                assertNotNull(storage.get(key))
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertNotNull(storage.get(key))
        assertTrue(value.contentEquals(storage.get(key)))
    }

    @Test
    fun testFindKey() {
        val storage = createStorage()
        val keys = listOf("test1", "test2", "other")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        
        val found = storage.findKey("test")
        assertEquals(2, found.size)
        assertTrue(found.contains("test1"))
        assertTrue(found.contains("test2"))
        assertFalse(found.contains("other"))
    }

    @Test
    fun testRemoveAll() {
        val storage = createStorage()
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        assertEquals(3, storage.allKeys.size)
        
        storage.removeAll()
        assertTrue(storage.allKeys.isEmpty())
        
        keys.forEach { key ->
            assertFailsWith<WalletError> {
                storage.get(key)
            }
        }
    }

    @Test
    fun testCaseSensitivity() {
        val storage = createStorage()
        val upperKey = "TEST-KEY"
        val lowerKey = "test-key"
        val value = "test".toByteArray()
        
        // Test case sensitivity in set/get
        storage.set(upperKey, value)
        assertTrue(value.contentEquals(storage.get(upperKey)))
        assertNull(storage.get(lowerKey))
        
        // Test case sensitivity in findKey
        storage.set(lowerKey, value)
        val foundKeys = storage.findKey("test")
        assertTrue(foundKeys.contains(lowerKey))
        assertFalse(foundKeys.contains(upperKey))
    }

    @Test
    fun testEmptyStorage() {
        val storage = createStorage()
        
        // Test allKeys with empty storage
        assertTrue(storage.allKeys.isEmpty())
        
        // Test findKey with empty storage
        assertTrue(storage.findKey("any").isEmpty())
        
        // Test get with empty storage
        assertNull(storage.get("any"))
    }

    @Test
    fun testConcurrentWrites() {
        val storage = createStorage()
        val key = "test-key"
        val values = List(10) { "value$it".toByteArray() }
        
        // Simulate concurrent writes to same key
        val threads = values.map { value ->
            Thread {
                storage.set(key, value)
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final value is one of the written values
        val finalValue = storage.get(key)
        assertTrue(values.any { it.contentEquals(finalValue) })
    }

    @Test
    fun testConcurrentReadWrite() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Simulate concurrent reads and writes
        val threads = List(10) {
            Thread {
                if (it % 2 == 0) {
                    storage.set(key, value)
                } else {
                    storage.get(key)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertTrue(value.contentEquals(storage.get(key)))
    }

    @Test
    fun testConcurrentRemovals() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Simulate concurrent removals
        val threads = List(10) {
            Thread {
                storage.set(key, value)
                storage.remove(key)
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertNull(storage.get(key))
    }

    @Test
    fun testLargeData() {
        val storage = createStorage()
        val key = "test-key"
        val value = ByteArray(1024 * 1024) { 1 } // 1MB of data
        
        // Test storing and retrieving large data
        storage.set(key, value)
        val retrieved = storage.get(key)
        assertTrue(value.contentEquals(retrieved))
    }

    @Test
    fun testNullValues() {
        val storage = createStorage()
        val key = "test-key"
        
        // Test storing null value
        storage.set(key, ByteArray(0))
        assertTrue(storage.get(key)?.isEmpty() ?: false)
    }

    @Test
    fun testEmptyByteArrays() {
        val storage = createStorage()
        val key = "test-key"
        val emptyValue = ByteArray(0)
        
        // Test storing empty byte array
        storage.set(key, emptyValue)
        val retrieved = storage.get(key)
        assertTrue(retrieved?.isEmpty() ?: false)
    }
} 