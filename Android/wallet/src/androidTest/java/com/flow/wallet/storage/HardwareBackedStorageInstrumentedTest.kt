package com.flow.wallet.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class HardwareBackedStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testAlias = "test-alias-${System.currentTimeMillis()}"
    private val testKeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    @Test
    fun testHardwareBackedKeyGeneration() {
        val storage = HardwareBackedStorage(context)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        assertTrue(storage.get(key) != null)
        
        val retrieved = storage.get(key)
        assertTrue(value.contentEquals(retrieved))
        
        // Verify key exists in keystore
        val keyAlias = "key_$key"
        assertTrue(testKeyStore.containsAlias(keyAlias))
    }

    @Test
    fun testKeyStoreOperations() {
        val storage = HardwareBackedStorage(context)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        val keyAlias = "key_$key"
        assertTrue(testKeyStore.containsAlias(keyAlias))
        
        // Verify key properties
        val keyEntry = testKeyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        val secretKey = keyEntry.secretKey
        assertEquals("AES", secretKey.algorithm)
    }

    @Test
    fun testKeyStoreCleanup() {
        val storage = HardwareBackedStorage(context)
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        storage.removeAll()
        
        keys.forEach { key ->
            val keyAlias = "key_$key"
            assertFalse(testKeyStore.containsAlias(keyAlias))
        }
    }

    @Test
    fun testKeyStoreSecurity() {
        val storage = HardwareBackedStorage(context)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        
        // Verify key properties
        val keyAlias = "key_$key"
        val keyEntry = testKeyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        val secretKey = keyEntry.secretKey
        assertEquals("AES", secretKey.algorithm)
        assertEquals(256, secretKey.encoded.size * 8) // 256-bit key
    }

    @Test
    fun testKeyStorePersistence() {
        val storage = HardwareBackedStorage(context)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        
        // Create new instance to verify persistence
        val newStorage = HardwareBackedStorage(context)
        assertTrue(newStorage.get(key) != null)
        assertTrue(value.contentEquals(newStorage.get(key)))
    }

    @Test
    fun testKeyStoreConcurrentAccess() {
        val storage = HardwareBackedStorage(context)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        // Simulate concurrent access
        val threads = List(10) {
            Thread {
                storage.set(key, value)
                storage.get(key)
                storage.get(key) != null
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertTrue(storage.get(key) != null)
        assertTrue(value.contentEquals(storage.get(key)))
    }
} 