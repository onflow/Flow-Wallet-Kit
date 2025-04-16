package io.outblock.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class SecureElementKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var secureElementKey: SecureElementKey
    private lateinit var keyStore: KeyStore

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        secureElementKey = SecureElementKey(keyStore, mockStorage)
    }

    @Test
    fun `test key creation with default options`() {
        val key = secureElementKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is SecureElementKey)
    }

    @Test
    fun `test key creation with advanced options`() {
        val key = secureElementKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is SecureElementKey)
    }

    @Test
    fun `test key creation and storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
    }

    @Test
    fun `test key retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
    }

    @Test
    fun `test key retrieval with invalid password`() {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            secureElementKey.get(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test key restoration`() {
        val secret = "test_secret".toByteArray()
        val restoredKey = secureElementKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.SECURE_ELEMENT, restoredKey.keyType)
    }

    @Test
    fun `test public key retrieval`() {
        val publicKey = secureElementKey.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun `test private key retrieval`() {
        val privateKeyBytes = secureElementKey.privateKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(privateKeyBytes)
        assertTrue(privateKeyBytes.isNotEmpty())
    }

    @Test
    fun `test signing and verification`() {
        val message = "test message".toByteArray()
        val signature = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(secureElementKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertTrue(!secureElementKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        
        secureElementKey.store(testId, testPassword)
        // Verify that storage.set was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test key removal`() {
        val testId = "test_key"
        
        secureElementKey.remove(testId)
        // Verify that storage.remove was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = secureElementKey.allKeys()
        assertEquals(testKeys, allKeys)
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(secureElementKey.isHardwareBacked)
    }

    @Test
    fun `test key generation with secure element`() {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "test_key_alias",
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        val key = secureElementKey.create(keyGenParameterSpec, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
    }
} 