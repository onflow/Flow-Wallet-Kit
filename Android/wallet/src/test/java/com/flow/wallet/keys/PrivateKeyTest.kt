package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class PrivateKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var keyPair: KeyPair
    private lateinit var privateKey: PrivateKey

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256k1")
        keyPairGenerator.initialize(ecSpec)
        keyPair = keyPairGenerator.generateKeyPair()
        privateKey = PrivateKey(keyPair, mockStorage)
    }

    @Test
    fun `test key creation with default options`() {
        val key = privateKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        assertTrue(key is PrivateKey)
    }

    @Test
    fun `test key creation with advanced options`() {
        val key = privateKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
        assertTrue(key is PrivateKey)
    }

    @Test
    fun `test key creation and storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = privateKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
    }

    @Test
    fun `test key retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = privateKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.PRIVATE_KEY, key.keyType)
    }

    @Test
    fun `test key retrieval with invalid password`() {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            privateKey.get(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test key restoration`() {
        val secret = keyPair.private.encoded
        val restoredKey = privateKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.PRIVATE_KEY, restoredKey.keyType)
    }

    @Test
    fun `test public key retrieval`() {
        val publicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun `test private key retrieval`() {
        val privateKeyBytes = privateKey.privateKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(privateKeyBytes)
        assertTrue(privateKeyBytes.isNotEmpty())
    }

    @Test
    fun `test signing and verification`() {
        val message = "test message".toByteArray()
        val signature = privateKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(privateKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertTrue(!privateKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        
        privateKey.store(testId, testPassword)
        // Verify that storage.set was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test key removal`() {
        val testId = "test_key"
        
        privateKey.remove(testId)
        // Verify that storage.remove was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = privateKey.allKeys()
        assertEquals(testKeys, allKeys)
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
    fun `test id property generation`() {
        val id = privateKey.id
        assertTrue(id.isNotEmpty())
        assertTrue(id.matches(Regex("^[A-Za-z0-9+/]+={0,2}$"))) // Base64 pattern
    }

    @Test
    fun `test encryption and decryption roundtrip`() {
        val testData = "test data".toByteArray()
        val testPassword = "test password"
        
        val encryptedData = privateKey.encryptData(testData, testPassword)
        val decryptedData = privateKey.decryptData(encryptedData, testPassword)
        
        assertTrue(testData.contentEquals(decryptedData))
    }

    @Test
    fun `test import invalid key data`() {
        val invalidData = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError.InvalidPrivateKey> {
            privateKey.importPrivateKey(invalidData, KeyFormat.PKCS8)
        }
    }

    @Test
    fun `test export with invalid key`() {
        val invalidKeyPair = KeyPair(null, null)
        val invalidPrivateKey = PrivateKey(invalidKeyPair, mockStorage)
        
        assertFailsWith<WalletError.InvalidPrivateKey> {
            invalidPrivateKey.exportPrivateKey(KeyFormat.PKCS8)
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(!privateKey.isHardwareBacked)
    }
} 