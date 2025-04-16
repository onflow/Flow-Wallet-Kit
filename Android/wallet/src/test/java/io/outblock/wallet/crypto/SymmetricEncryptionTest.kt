package io.outblock.wallet.crypto

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

/**
 * Base test class for symmetric encryption implementations
 * Provides common test cases that should be valid for all symmetric encryption algorithms
 */
abstract class SymmetricEncryptionTest {
    /**
     * Factory method to create the specific encryption implementation to test
     */
    abstract fun createCipher(): SymmetricEncryption

    @Test
    fun testKeyProperties() {
        val cipher = createCipher()
        assertTrue(cipher.key.isNotEmpty(), "Key should not be empty")
        assertTrue(cipher.keySize > 0, "Key size should be positive")
    }

    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val cipher = createCipher()
        val originalData = "Test data for encryption".toByteArray()
        
        val encrypted = cipher.encrypt(originalData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(originalData.size, decrypted.size, "Decrypted data size should match original")
        assertTrue(originalData.contentEquals(decrypted), "Decrypted data should match original")
    }

    @Test
    fun testEmptyData() {
        val cipher = createCipher()
        val emptyData = ByteArray(0)
        
        val encrypted = cipher.encrypt(emptyData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(0, decrypted.size, "Decrypted empty data should be empty")
    }

    @Test
    fun testLargeData() {
        val cipher = createCipher()
        val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() } // 1MB of data
        
        val encrypted = cipher.encrypt(largeData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(largeData.size, decrypted.size, "Decrypted large data size should match original")
        assertTrue(largeData.contentEquals(decrypted), "Decrypted large data should match original")
    }
} 