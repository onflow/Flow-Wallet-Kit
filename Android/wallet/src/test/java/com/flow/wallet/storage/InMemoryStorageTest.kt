package com.flow.wallet.storage

import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
                storage.exists(key)
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertTrue(storage.exists(key))
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
} 