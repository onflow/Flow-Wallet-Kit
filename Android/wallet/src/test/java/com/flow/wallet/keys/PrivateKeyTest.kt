package com.flow.wallet.keys

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
import wallet.core.jni.PrivateKey as TWPrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(MockitoJUnitRunner::class)
class PrivateKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var twPrivateKey: TWPrivateKey
    private lateinit var privateKey: PrivateKey

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        twPrivateKey = TWPrivateKey()
        privateKey = PrivateKey(twPrivateKey, mockStorage)
    }

    @Test
    fun `test key creation with default options`() = runBlocking {
        val key = privateKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        assertTrue(key is PrivateKey)
    }

    @Test
    fun `test key creation with advanced options`() = runBlocking {
        val key = privateKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        assertTrue(key is PrivateKey)
    }

    @Test
    fun `test key creation and storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = privateKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        verify(mockStorage).set(testId, any())
        verify(mockStorage).set("${testId}_metadata", any())
    }

    @Test
    fun `test key retrieval`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = privateKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError> {
            privateKey.get(testId, testPassword, mockStorage)
        }
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key restoration`() = runBlocking {
        val secret = twPrivateKey.data()
        val restoredKey = privateKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.PRIVATE_KEY, restoredKey.keyType)
    }

    @Test
    fun `test key restoration with invalid data`() = runBlocking {
        val invalidSecret = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError> {
            privateKey.restore(invalidSecret, mockStorage)
        }
    }

    @Test
    fun `test public key retrieval for different algorithms`() {
        val p256Key = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        assertTrue(p256Key.isNotEmpty())
        assertTrue(secp256k1Key.isNotEmpty())
        assertFalse(p256Key.contentEquals(secp256k1Key))
    }

    @Test
    fun `test private key retrieval for different algorithms`() {
        val p256Key = privateKey.privateKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = privateKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        assertTrue(p256Key.isNotEmpty())
        assertTrue(secp256k1Key.isNotEmpty())
    }

    @Test
    fun `test signing and verification with different algorithms`() = runBlocking {
        val message = "test message".toByteArray()
        
        // Test ECDSA_P256 with SHA2_256
        val p256Signature = privateKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        assertTrue(p256Signature.isNotEmpty())
        assertTrue(privateKey.isValidSignature(p256Signature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
        
        // Test ECDSA_secp256k1 with SHA2_256
        val secp256k1Signature = privateKey.sign(message, SigningAlgorithm.ECDSA_secp256k1, HashingAlgorithm.SHA2_256)
        assertTrue(secp256k1Signature.isNotEmpty())
        assertTrue(privateKey.isValidSignature(secp256k1Signature, message, SigningAlgorithm.ECDSA_secp256k1, HashingAlgorithm.SHA2_256))
        
        // Test cross-algorithm verification (should fail)
        assertFalse(privateKey.isValidSignature(p256Signature, message, SigningAlgorithm.ECDSA_secp256k1, HashingAlgorithm.SHA2_256))
        assertFalse(privateKey.isValidSignature(secp256k1Signature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun `test signing with different hashing algorithms`() = runBlocking {
        val message = "test message".toByteArray()
        
        val sha2_256 = privateKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        val sha3_256 = privateKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
        
        assertTrue(sha2_256.isNotEmpty())
        assertTrue(sha3_256.isNotEmpty())
        assertFalse(sha2_256.contentEquals(sha3_256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertFalse(privateKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
        assertFalse(privateKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_secp256k1, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun `test key storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        privateKey.store(testId, testPassword)
        verify(mockStorage).set(testId, any())
        verify(mockStorage).set("${testId}_metadata", any())
    }

    @Test
    fun `test key removal`() = runBlocking {
        val testId = "test_key"
        
        privateKey.remove(testId)
        verify(mockStorage).remove(testId)
        verify(mockStorage).remove("${testId}_metadata")
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = privateKey.allKeys()
        assertEquals(testKeys, allKeys)
        verify(mockStorage).allKeys
    }

    @Test
    fun `test key export in different formats`() {
        val pkcs8Format = privateKey.exportPrivateKey(KeyFormat.PKCS8)
        val rawFormat = privateKey.exportPrivateKey(KeyFormat.RAW)
        
        assertTrue(pkcs8Format.isNotEmpty())
        assertTrue(rawFormat.isNotEmpty())
    }

    @Test
    fun `test key import in different formats`() {
        val pkcs8Data = privateKey.exportPrivateKey(KeyFormat.PKCS8)
        val rawData = privateKey.exportPrivateKey(KeyFormat.RAW)
        
        privateKey.importPrivateKey(pkcs8Data, KeyFormat.PKCS8)
        privateKey.importPrivateKey(rawData, KeyFormat.RAW)
    }

    @Test
    fun `test import invalid key data`() {
        val invalidData = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError> {
            privateKey.importPrivateKey(invalidData, KeyFormat.PKCS8)
        }
    }

    @Test
    fun `test export with invalid key`() {
        val invalidPrivateKey = PrivateKey(TWPrivateKey(), mockStorage)
        
        assertFailsWith<WalletError> {
            invalidPrivateKey.exportPrivateKey(KeyFormat.PKCS8)
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertFalse(privateKey.isHardwareBacked)
    }

    @Test
    fun `test id property generation`() {
        val id = privateKey.id
        assertTrue(id.isNotEmpty())
        assertTrue(id.matches(Regex("^[A-Za-z0-9+/]+={0,2}$"))) // Base64 pattern
    }

    @Test
    fun `test key properties`() {
        assertEquals(KeyType.PRIVATE_KEY, privateKey.keyType)
        assertEquals(Unit, privateKey.advance)
        assertNotNull(privateKey.key)
        assertNotNull(privateKey.secret)
    }

    @Test
    fun `test key storage with encryption`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        privateKey.store(testId, testPassword)
        verify(mockStorage).set(testId, any())
        verify(mockStorage).set("${testId}_metadata", any())
    }

    @Test
    fun `test key retrieval with decryption`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = privateKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key retrieval with invalid encrypted data`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val invalidEncryptedData = ByteArray(32) { it.toByte() }
        
        `when`(mockStorage.get(testId)).thenReturn(invalidEncryptedData)
        
        assertFailsWith<WalletError> {
            privateKey.get(testId, testPassword, mockStorage)
        }
        verify(mockStorage).get(testId)
    }
} 
