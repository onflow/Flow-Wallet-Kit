package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(MockitoJUnitRunner::class)
class SeedPhraseKeyProviderTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var seedPhraseKeyProvider: SeedPhraseKeyProvider
    private val validSeedPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        seedPhraseKeyProvider = SeedPhraseKey(validSeedPhrase, "", "m/44'/539'/0'/0/0", mockStorage)
    }

    @Test
    fun `test key derivation`() {
        val derivedKey = seedPhraseKeyProvider.deriveKey(0)
        assertNotNull(derivedKey)
        assertTrue(derivedKey is PrivateKey)
    }

    @Test
    fun `test key derivation with different indices`() {
        val key1 = seedPhraseKeyProvider.deriveKey(0)
        val key2 = seedPhraseKeyProvider.deriveKey(1)
        
        assertNotNull(key1)
        assertNotNull(key2)
        assertTrue(key1 is PrivateKey)
        assertTrue(key2 is PrivateKey)
        
        // Different indices should produce different keys
        assertTrue(!key1.secret.contentEquals(key2.secret))
    }

    @Test
    fun `test key creation with default options`() = runBlocking {
        val key = seedPhraseKeyProvider.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key creation with advanced options`() = runBlocking {
        val key = seedPhraseKeyProvider.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key storage and retrieval`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKeyProvider.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test storage failure scenarios`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        `when`(mockStorage.set(any(), any())).thenThrow(RuntimeException("Storage error"))
        
        assertFailsWith<WalletError> {
            seedPhraseKeyProvider.createAndStore(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError> {
            seedPhraseKeyProvider.get(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test key restoration`() = runBlocking {
        val secret = "test_secret".toByteArray()
        val restoredKey = seedPhraseKeyProvider.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.SEED_PHRASE, restoredKey.keyType)
    }

    @Test
    fun `test key restoration with invalid data`() = runBlocking {
        val invalidSecret = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError> {
            seedPhraseKeyProvider.restore(invalidSecret, mockStorage)
        }
    }

    @Test
    fun `test public key retrieval for different algorithms`() {
        val p256Key = seedPhraseKeyProvider.publicKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = seedPhraseKeyProvider.publicKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        if (p256Key != null) {
            assertTrue(p256Key.isNotEmpty())
        }
        if (secp256k1Key != null) {
            assertTrue(secp256k1Key.isNotEmpty())
        }
    }

    @Test
    fun `test signing and verification`() = runBlocking {
        val message = "test message".toByteArray()
        val signature = seedPhraseKeyProvider.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(seedPhraseKeyProvider.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun `test signing with different hashing algorithms`() = runBlocking {
        val message = "test message".toByteArray()
        
        val sha2_256 = seedPhraseKeyProvider.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        val sha3_256 = seedPhraseKeyProvider.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
        
        assertTrue(sha2_256.isNotEmpty())
        assertTrue(sha3_256.isNotEmpty())
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertFalse(seedPhraseKeyProvider.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun `test key removal`() = runBlocking {
        val testId = "test_key"
        seedPhraseKeyProvider.remove(testId)
        verify(mockStorage).remove(testId)
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = seedPhraseKeyProvider.allKeys()
        assertEquals(testKeys, allKeys)
        verify(mockStorage).allKeys
    }

    @Test
    fun `test hardware backed property`() {
        assertFalse(seedPhraseKeyProvider.isHardwareBacked)
    }
} 
