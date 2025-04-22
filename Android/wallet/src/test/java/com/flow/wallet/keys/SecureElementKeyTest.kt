package com.flow.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
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
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(MockitoJUnitRunner::class)
class SecureElementKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var secureElementKey: SecureElementKey
    private lateinit var keyPair: KeyPair

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Generate test key pair using standard Java crypto
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256k1"))
        keyPair = keyPairGenerator.generateKeyPair()
        secureElementKey = SecureElementKey(keyPair, mockStorage)
    }

    @Test
    fun `test key creation with default options`() = runBlocking {
        val key = secureElementKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is SecureElementKey)
        assertTrue(key.isHardwareBacked)
    }

    @Test
    fun `test key creation with advanced options`() = runBlocking {
        val key = secureElementKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is SecureElementKey)
        assertTrue(key.isHardwareBacked)
    }

    @Test
    fun `test key creation and storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test key retrieval`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            secureElementKey.get(testId, testPassword, mockStorage)
        }
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key restoration`() = runBlocking {
        val secret = "test_secret".toByteArray()
        val restoredKey = secureElementKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.SECURE_ELEMENT, restoredKey.keyType)
    }

    @Test
    fun `test key restoration with invalid data`() = runBlocking {
        val invalidSecret = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError.InvalidPrivateKey> {
            secureElementKey.restore(invalidSecret, mockStorage)
        }
    }

    @Test
    fun `test public key retrieval`() {
        val publicKey = secureElementKey.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun `test public key retrieval for different algorithms`() {
        val p256Key = secureElementKey.publicKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = secureElementKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        assertTrue(p256Key.isNotEmpty())
        assertTrue(secp256k1Key.isNotEmpty())
        assertFalse(p256Key.contentEquals(secp256k1Key))
    }

    @Test
    fun `test private key retrieval`() {
        val privateKeyBytes = secureElementKey.privateKey(SigningAlgorithm.ECDSA_P256)
        assertNull(privateKeyBytes) // Hardware-backed keys don't expose private key material
    }

    @Test
    fun `test signing and verification`() = runBlocking {
        val message = "test message".toByteArray()
        val signature = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(secureElementKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test signing with different hashing algorithms`() = runBlocking {
        val message = "test message".toByteArray()
        
        val sha2_256 = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        val sha3_256 = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
        
        assertTrue(sha2_256.isNotEmpty())
        assertTrue(sha3_256.isNotEmpty())
        assertFalse(sha2_256.contentEquals(sha3_256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertFalse(secureElementKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        secureElementKey.store(testId, testPassword)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test key removal`() = runBlocking {
        val testId = "test_key"
        
        secureElementKey.remove(testId)
        verify(mockStorage).remove(testId)
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = secureElementKey.allKeys()
        assertEquals(testKeys, allKeys)
        verify(mockStorage).allKeys
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(secureElementKey.isHardwareBacked)
    }

    @Test
    fun `test key properties`() {
        assertNotNull(secureElementKey.id)
        assertTrue(secureElementKey.secret.isEmpty()) // Hardware-backed keys don't expose secret material
        assertEquals(Unit, secureElementKey.advance)
        assertEquals(KeyType.SECURE_ELEMENT, secureElementKey.keyType)
    }

    @Test
    fun `test key generation with secure element`() = runBlocking {
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
        assertTrue(key.isHardwareBacked)
    }
} 