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
        assertTrue(cipher.key.isNotEmpty())
        assertTrue(cipher.keySize > 0)
    }

    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val cipher = createCipher()
        val originalData = "Test data for encryption".toByteArray()
        
        val encrypted = cipher.encrypt(originalData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(originalData.size, decrypted.size)
        assertTrue(originalData.contentEquals(decrypted))
    }

    @Test
    fun testEmptyData() {
        val cipher = createCipher()
        val emptyData = ByteArray(0)
        
        val encrypted = cipher.encrypt(emptyData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(0, decrypted.size)
    }

    @Test
    fun testLargeData() {
        val cipher = createCipher()
        val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() } // 1MB of data
        
        val encrypted = cipher.encrypt(largeData)
        val decrypted = cipher.decrypt(encrypted)
        
        assertEquals(largeData.size, decrypted.size)
        assertTrue(largeData.contentEquals(decrypted))
    }
} 