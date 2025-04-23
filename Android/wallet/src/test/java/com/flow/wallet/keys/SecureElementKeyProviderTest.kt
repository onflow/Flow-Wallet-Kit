package com.flow.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
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
import java.security.KeyPair
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(MockitoJUnitRunner::class)
class SecureElementKeyProviderTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var secureElementKeyProvider: SecureElementKeyProvider
    private lateinit var keyPair: KeyPair

    companion object {
        private const val TEST_KEY_ALIAS = "test_secure_element_key"
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Generate key using Trust Wallet Core
        val privateKey = PrivateKey()
        val publicKey = privateKey.getPublicKeySecp256k1(false)
        keyPair = KeyPair(publicKey, privateKey)
        secureElementKeyProvider = SecureElementKey(keyPair, mockStorage)
    }

    @Test
    fun `test key creation with default parameters`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key.isHardwareBacked)
    }

    @Test
    fun `test key creation with specific parameters`() = runBlocking {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            TEST_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        val key = secureElementKeyProvider.create(keyGenParameterSpec, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key.isHardwareBacked)
    }

    @Test
    fun `test key creation with authentication required`() = runBlocking {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            TEST_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(1)
            .build()

        val key = secureElementKeyProvider.create(keyGenParameterSpec, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key.isHardwareBacked)
    }

    @Test
    fun `test key creation with invalid parameters`() = runBlocking {
        val invalidSpec = KeyGenParameterSpec.Builder(
            TEST_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT // Invalid purpose for signing
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        assertFailsWith<WalletError.InitPrivateKeyFailed> {
            secureElementKeyProvider.create(invalidSpec, mockStorage)
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(secureElementKeyProvider.isHardwareBacked)
    }

    @Test
    fun `test key storage and retrieval`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKeyProvider.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            secureElementKeyProvider.get(testId, testPassword, mockStorage)
        }
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key properties`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        assertNotNull(key.id)
        assertTrue(key.secret.isEmpty()) // Hardware-backed keys don't expose secret material
        assertEquals(Unit, key.advance)
    }

    @Test
    fun `test public key retrieval`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        val publicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun `test private key retrieval`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        val privateKey = key.privateKey(SigningAlgorithm.ECDSA_P256)
        assertNull(privateKey) // Hardware-backed keys don't expose private key material
    }

    @Test
    fun `test signing and verification`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        val message = "test message".toByteArray()
        
        val signature = key.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        assertTrue(signature.isNotEmpty())
        assertTrue(key.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test invalid signature verification`() = runBlocking {
        val key = secureElementKeyProvider.create(mockStorage)
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertFalse(key.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key removal`() = runBlocking {
        val testId = "test_key"
        secureElementKeyProvider.remove(testId)
        verify(mockStorage).remove(testId)
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = secureElementKeyProvider.allKeys()
        assertEquals(testKeys, allKeys)
        verify(mockStorage).allKeys
    }

    @Test
    fun `test restore operation not supported`() = runBlocking {
        val secret = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError.NoImplement> {
            secureElementKeyProvider.restore(secret, mockStorage)
        }
    }
} 