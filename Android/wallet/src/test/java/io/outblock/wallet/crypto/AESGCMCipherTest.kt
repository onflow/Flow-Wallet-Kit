package io.outblock.wallet.crypto

import io.outblock.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class AESGCMCipherTest : SymmetricEncryptionTest() {
    override fun createCipher(): SymmetricEncryption {
        return AESGCMCipher("test-key-123")
    }

    @Test
    fun testKeyProperties() {
        super.testKeyProperties()
    }

    @Test
    fun testEncryptionDecryptionRoundtrip() {
        super.testEncryptionDecryptionRoundtrip()
    }

    @Test
    fun testEmptyData() {
        super.testEmptyData()
    }

    @Test
    fun testLargeData() {
        super.testLargeData()
    }

    @Test
    fun testInvalidKey() {
        assertFailsWith<WalletError> {
            AESGCMCipher("")
        }
    }

    @Test
    fun testInvalidEncryptedData() {
        val cipher = createCipher()
        val invalidData = ByteArray(10) { it.toByte() }
        
        assertFailsWith<WalletError> {
            cipher.decrypt(invalidData)
        }
    }

    @Test
    fun testDifferentKeys() {
        val cipher1 = AESGCMCipher("key1")
        val cipher2 = AESGCMCipher("key2")
        val data = "Test data".toByteArray()
        
        val encrypted = cipher1.encrypt(data)
        assertFailsWith<WalletError> {
            cipher2.decrypt(encrypted)
        }
    }

    @Test
    fun testKeyDerivation() {
        val cipher1 = AESGCMCipher("test-key")
        val cipher2 = AESGCMCipher("test-key")
        
        assertEquals(cipher1.key.size, cipher2.key.size)
        assertTrue(cipher1.key.contentEquals(cipher2.key))
    }

    @Test
    fun testTamperedData() {
        val cipher = createCipher()
        val data = "Test data".toByteArray()
        val encrypted = cipher.encrypt(data)
        
        // Tamper with the encrypted data
        encrypted[0] = (encrypted[0] + 1).toByte()
        
        assertFailsWith<WalletError> {
            cipher.decrypt(encrypted)
        }
    }
} 