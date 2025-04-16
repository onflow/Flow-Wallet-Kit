package io.outblock.wallet.keys

import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyPairGenerator
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class KeyProtocolTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var privateKey: PrivateKey
    private lateinit var secureElementKey: SecureElementKey
    private lateinit var seedPhraseKey: SeedPhraseKey

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Initialize different key types
        privateKey = PrivateKey(KeyPairGenerator.getInstance("EC").generateKeyPair(), mockStorage)
        secureElementKey = SecureElementKey(KeyStore.getInstance("AndroidKeyStore"), mockStorage)
        seedPhraseKey = SeedPhraseKey("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", mockStorage)
    }

    @Test
    fun `test key type consistency`() {
        assertEquals(KeyType.PRIVATE_KEY, privateKey.keyType)
        assertEquals(KeyType.SECURE_ELEMENT, secureElementKey.keyType)
        assertEquals(KeyType.SEED_PHRASE, seedPhraseKey.keyType)
    }

    @Test
    fun `test key creation with default options`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val newKey = key.create(mockStorage)
            assertNotNull(newKey)
            assertEquals(key.keyType, newKey.keyType)
        }
    }

    @Test
    fun `test key creation with advanced options`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val newKey = key.create(Unit, mockStorage)
            assertNotNull(newKey)
            assertEquals(key.keyType, newKey.keyType)
        }
    }

    @Test
    fun `test key storage and retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val storedKey = key.createAndStore(testId, testPassword, mockStorage)
            assertNotNull(storedKey)
            assertEquals(key.keyType, storedKey.keyType)
        }
    }

    @Test
    fun `test key retrieval with invalid password`() {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            assertFailsWith<WalletError.InvalidPassword> {
                key.get(testId, testPassword, mockStorage)
            }
        }
    }

    @Test
    fun `test key restoration`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val secret = key.secret
            val restoredKey = key.restore(secret, mockStorage)
            assertNotNull(restoredKey)
            assertEquals(key.keyType, restoredKey.keyType)
        }
    }

    @Test
    fun `test public key retrieval`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val publicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
            assertNotNull(publicKey)
            assertTrue(publicKey.isNotEmpty())
        }
    }

    @Test
    fun `test signing and verification`() {
        val message = "test message".toByteArray()
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val signature = key.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
            assertTrue(signature.isNotEmpty())
            assertTrue(key.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
        }
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            assertTrue(!key.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
        }
    }

    @Test
    fun `test key removal`() {
        val testId = "test_key"
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            key.remove(testId)
            // Verify that storage.remove was called with the correct parameters
            // This would typically be done with Mockito.verify()
        }
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val allKeys = key.allKeys()
            assertEquals(testKeys, allKeys)
        }
    }
} 