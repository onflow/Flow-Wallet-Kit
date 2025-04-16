package io.outblock.wallet.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.outblock.wallet.errors.WalletError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class HardwareBackedStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testAlias = "test-alias-${System.currentTimeMillis()}"
    private val testKeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    @Test
    fun testHardwareBackedKeyGeneration() {
        val storage = HardwareBackedStorage(context, testAlias)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        assertTrue(storage.exists(key))
        
        val retrieved = storage.get(key)
        assertTrue(value.contentEquals(retrieved))
        
        // Verify key is hardware-backed
        val keyEntry = testKeyStore.getEntry(testAlias, null) as KeyStore.SecretKeyEntry
        assertTrue(keyEntry.secretKey.isHardwareBacked)
    }

    @Test
    fun testKeyStoreOperations() {
        val storage = HardwareBackedStorage(context, testAlias)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        assertTrue(testKeyStore.containsAlias(testAlias))
        
        // Verify key properties
        val keyEntry = testKeyStore.getEntry(testAlias, null) as KeyStore.SecretKeyEntry
        val secretKey = keyEntry.secretKey
        assertTrue(secretKey.isHardwareBacked)
        assertEquals("AES", secretKey.algorithm)
    }

    @Test
    fun testKeyStoreCleanup() {
        val storage = HardwareBackedStorage(context, testAlias)
        val keys = listOf("key1", "key2", "key3")
        val value = "test".toByteArray()
        
        keys.forEach { storage.set(it, value) }
        storage.removeAll()
        
        assertFalse(testKeyStore.containsAlias(testAlias))
    }

    @Test
    fun testKeyStoreSecurity() {
        val storage = HardwareBackedStorage(context, testAlias)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        
        // Verify key is hardware-backed and has correct properties
        val keyEntry = testKeyStore.getEntry(testAlias, null) as KeyStore.SecretKeyEntry
        val secretKey = keyEntry.secretKey
        assertTrue(secretKey.isHardwareBacked)
        assertEquals("AES", secretKey.algorithm)
        assertEquals(256, secretKey.encoded.size * 8) // 256-bit key
    }

    @Test
    fun testKeyStorePersistence() {
        val storage = HardwareBackedStorage(context, testAlias)
        val key = "test-key"
        val value = "test-value".toByteArray()
        
        storage.set(key, value)
        
        // Create new instance to verify persistence
        val newStorage = HardwareBackedStorage(context, testAlias)
        assertTrue(newStorage.exists(key))
        assertTrue(value.contentEquals(newStorage.get(key)))
    }

    @Test
    fun testKeyStoreErrorHandling() {
        val invalidAlias = ""
        assertFailsWith<WalletError> {
            HardwareBackedStorage(context, invalidAlias)
        }
    }

    @Test
    fun testKeyStoreConcurrentAccess() {
        val storage = HardwareBackedStorage(context, testAlias)
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
} 