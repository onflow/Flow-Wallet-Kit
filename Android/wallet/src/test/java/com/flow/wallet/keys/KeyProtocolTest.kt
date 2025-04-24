package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import wallet.core.jni.PrivateKey as TWPrivateKey
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyPairGenerator
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class KeyProtocolTest {

//    companion object {
//        @JvmStatic
//        @BeforeClass
//        fun loadNativeLib() {
//            System.loadLibrary("TrustWalletCore")
//        }
//    }

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var privateKey: PrivateKey
    private lateinit var secureElementKey: SecureElementKey
    private lateinit var seedPhraseKey: SeedPhraseKey

    @Before
    fun setup() {
        System.loadLibrary("TrustWalletCore")
        MockitoAnnotations.openMocks(this)
        
        // Initialize different key types
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        // Initialize PrivateKey with TWPrivateKey
        val twPrivateKey = TWPrivateKey()
        privateKey = PrivateKey(twPrivateKey, mockStorage)
        
        // Initialize SecureElementKey with KeyPair
        secureElementKey = SecureElementKey(keyPair, mockStorage)
        
        // Initialize SeedPhraseKey with required parameters
        seedPhraseKey = SeedPhraseKey(
            mnemonicString = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = keyPair,
            storage = mockStorage
        )
    }

    @Test
    fun `test key type consistency`() {
        assertEquals(KeyType.PRIVATE_KEY, privateKey.keyType)
        assertEquals(KeyType.SECURE_ELEMENT, secureElementKey.keyType)
        assertEquals(KeyType.SEED_PHRASE, seedPhraseKey.keyType)
    }

    @Test
    fun `test key properties`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            assertNotNull(key.key)
            assertNotNull(key.secret)
            assertNotNull(key.advance)
            assertNotNull(key.id)
            assertTrue(key.id.isNotEmpty())
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(secureElementKey.isHardwareBacked)
        assertTrue(!privateKey.isHardwareBacked)
        assertTrue(!seedPhraseKey.isHardwareBacked)
    }

    @Test
    fun `test key creation with default options`() = runBlocking {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val newKey = key.create(mockStorage)
            assertNotNull(newKey)
            assertEquals(key.keyType, newKey.keyType)
        }
    }

    @Test
    fun `test key creation with advanced options`() = runBlocking {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val newKey = key.create(Unit, mockStorage)
            assertNotNull(newKey)
            assertEquals(key.keyType, newKey.keyType)
        }
    }

    @Test
    fun `test key storage and retrieval`() = runBlocking {
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
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            assertFailsWith<WalletError> {
                key.get(testId, testPassword, mockStorage)
            }
        }
    }

    @Test
    fun `test key restoration`() = runBlocking {
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
            if (publicKey != null) {
                assertTrue(publicKey.isNotEmpty())
            }
        }
    }

    @Test
    fun `test private key retrieval`() {
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val privateKey = key.privateKey(SigningAlgorithm.ECDSA_P256)
            assertNotNull(privateKey)
            if (privateKey != null) {
                assertTrue(privateKey.isNotEmpty())
            }
        }
    }

    @Test
    fun `test signing and verification`() = runBlocking {
        val message = "test message".toByteArray()
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            val signature = key.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
            assertTrue(signature.isNotEmpty())
            assertTrue(key.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
        }
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            assertTrue(!key.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
        }
    }

    @Test
    fun `test key storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        listOf(privateKey, secureElementKey, seedPhraseKey).forEach { key ->
            key.store(testId, testPassword)
            // Verify that storage.set was called with the correct parameters
            // This would typically be done with Mockito.verify()
        }
    }

    @Test
    fun `test key removal`() = runBlocking {
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

    @Test
    fun `test secure element specific functionality`() {
        assertTrue(secureElementKey.isSecureElementAvailable())
        val properties = secureElementKey.getKeyProperties()
        assertTrue(properties.isNotEmpty())
        assertTrue(properties.containsKey("isHardwareBacked"))
    }

    @Test
    fun `test seed phrase specific functionality`() {
        assertTrue(seedPhraseKey.mnemonic.isNotEmpty())
        assertTrue(seedPhraseKey.derivationPath.isNotEmpty())
        
        val derivedKey = seedPhraseKey.deriveKey(0)
        assertNotNull(derivedKey)
        assertEquals(KeyType.PRIVATE_KEY, derivedKey.keyType)
    }

    @Test
    fun `test error cases`() : Unit = runBlocking {
        // Test empty key error
        val emptyKey = PrivateKey(TWPrivateKey(), mockStorage)
        assertFailsWith<WalletError> {
            emptyKey.importPrivateKey(ByteArray(0), KeyFormat.RAW)
        }

        // Test invalid private key error
        val invalidKey = PrivateKey(TWPrivateKey(), mockStorage)
        assertFailsWith<WalletError> {
            invalidKey.importPrivateKey(ByteArray(31), KeyFormat.RAW) // Invalid length for private key
        }

        // Test sign error
        val signKey = PrivateKey(TWPrivateKey(), mockStorage)
        assertFailsWith<WalletError> {
            signKey.sign(ByteArray(0), SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
        }
    }
} 