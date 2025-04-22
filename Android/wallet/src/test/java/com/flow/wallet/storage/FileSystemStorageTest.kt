package com.flow.wallet.storage

import com.flow.wallet.errors.WalletError
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.io.File
import java.util.UUID

class FileSystemStorageTest : StorageProtocolTest() {
    private val testDir = File(System.getProperty("java.io.tmpdir"), "wallet-test-${UUID.randomUUID()}")
    
    override fun createStorage(): StorageProtocol {
        return FileSystemStorage(testDir)
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
        assertEquals(SecurityLevel.STANDARD, storage.securityLevel)
    }

    @Test
    fun testDirectoryCreation() {
        val storage = createStorage()
        assertTrue(testDir.exists())
        assertTrue(testDir.isDirectory)
    }

    @Test
    fun testFileOperations() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        val file = File(testDir, key)
        assertTrue(file.exists())
        assertTrue(file.isFile)
        
        storage.remove(key)
        assertFalse(file.exists())
    }

    @Test
    fun testInvalidDirectory() {
        val invalidDir = File("/invalid/path/that/does/not/exist")
        assertFailsWith<WalletError> {
            FileSystemStorage(invalidDir)
        }
    }

    @Test
    fun testFilePermissions() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        val file = File(testDir, key)
        
        // Verify file permissions
        assertTrue(file.canRead())
        assertTrue(file.canWrite())
        assertFalse(file.canExecute())
    }

    @Test
    fun testCleanup() {
        val storage = createStorage()
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        storage.removeAll()
        
        // Verify all files are removed
        assertTrue(testDir.listFiles()?.isEmpty() ?: true)
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
    fun testCacheSize() {
        val storage = createStorage() as FileSystemStorage
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        // Test empty cache
        assertEquals(0L, storage.getCacheSize())
        
        // Test with files
        keys.forEach { storage.set(it, value) }
        val expectedSize = keys.size * value.size.toLong()
        assertEquals(expectedSize, storage.getCacheSize())
        
        // Test after removal
        storage.remove(keys[0])
        assertEquals(expectedSize - value.size, storage.getCacheSize())
    }

    @Test
    fun testModificationDate() {
        val storage = createStorage() as FileSystemStorage
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Test non-existent key
        assertNull(storage.getModificationDate(key))
        
        // Test after setting
        storage.set(key, value)
        val date = storage.getModificationDate(key)
        assertNotNull(date)
        assertTrue(date.time <= System.currentTimeMillis())
        
        // Test after update
        Thread.sleep(100) // Ensure different timestamp
        storage.set(key, value)
        val newDate = storage.getModificationDate(key)
        assertNotNull(newDate)
        assertTrue(newDate.time > date.time)
    }

    @Test
    fun testItemCount() {
        val storage = createStorage() as FileSystemStorage
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        // Test empty storage
        assertEquals(0, storage.getItemCount())
        
        // Test with files
        keys.forEach { storage.set(it, value) }
        assertEquals(keys.size, storage.getItemCount())
        
        // Test after removal
        storage.remove(keys[0])
        assertEquals(keys.size - 1, storage.getItemCount())
    }

    @Test
    fun testFileOperationErrors() {
        val storage = createStorage() as FileSystemStorage
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Test read error
        storage.set(key, value)
        val file = File(testDir, key)
        file.setReadable(false)
        assertFailsWith<WalletError> {
            storage.get(key)
        }
        file.setReadable(true)
        
        // Test write error
        file.setWritable(false)
        assertFailsWith<WalletError> {
            storage.set(key, value)
        }
        file.setWritable(true)
        
        // Test delete error
        file.setWritable(false)
        assertFailsWith<WalletError> {
            storage.remove(key)
        }
        file.setWritable(true)
    }
} 