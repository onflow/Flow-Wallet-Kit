package com.flow.wallet.storage

import android.content.Context
import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyStore
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class HardwareBackedStorageTest : com.flow.wallet.storage.StorageProtocolTest() {
    @Mock
    private lateinit var mockContext: Context

    private val testAlias = "test-alias"
    private val testKeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    override fun createStorage(): com.flow.wallet.storage.StorageProtocol {
        return com.flow.wallet.storage.HardwareBackedStorage(mockContext)
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
        assertEquals(com.flow.wallet.storage.SecurityLevel.HARDWARE_BACKED, storage.securityLevel)
    }

    @Test
    fun testKeyGeneration() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        assertTrue(storage.exists(key))
        
        val retrieved = storage.get(key)
        assertTrue(value.contentEquals(retrieved))
    }

    @Test
    fun testKeyStoreOperations() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        assertTrue(testKeyStore.containsAlias(testAlias))
    }

    @Test
    fun testInvalidKeyStore() {
        `when`(mockContext.packageName).thenReturn("invalid.package")
        
        assertFailsWith<WalletError> {
            com.flow.wallet.storage.HardwareBackedStorage(mockContext, testAlias)
        }
    }

    @Test
    fun testKeyStoreCleanup() {
        val storage = createStorage()
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        storage.removeAll()
        
        assertFalse(testKeyStore.containsAlias(testAlias))
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
    fun testKeyStoreSecurity() {
        val storage = createStorage()
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        
        // Verify key is hardware-backed
        val keyEntry = testKeyStore.getEntry(testAlias, null) as KeyStore.SecretKeyEntry
        assertTrue(keyEntry.secretKey.isHardwareBacked)
    }
} 