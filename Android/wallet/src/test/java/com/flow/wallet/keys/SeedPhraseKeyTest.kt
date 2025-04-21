package com.flow.wallet.keys

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
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class SeedPhraseKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var keyPair: KeyPair
    private lateinit var seedPhraseKey: SeedPhraseKey

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256k1")
        keyPairGenerator.initialize(ecSpec)
        keyPair = keyPairGenerator.generateKeyPair()
        seedPhraseKey = SeedPhraseKey("test mnemonic", "test passphrase", "m/44'/539'/0'/0/0", keyPair, mockStorage)
    }

    @Test
    fun `test key creation with default options`() {
        val key = seedPhraseKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        assertTrue(key is SeedPhraseKey)
    }

    @Test
    fun `test key creation with advanced options`() {
        val key = seedPhraseKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        assertTrue(key is SeedPhraseKey)
    }

    @Test
    fun `test key creation and storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key retrieval with invalid password`() {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            seedPhraseKey.get(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test key restoration`() {
        val secret = "test mnemonic".toByteArray()
        val restoredKey = seedPhraseKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.SEED_PHRASE, restoredKey.keyType)
    }

    @Test
    fun `test public key retrieval`() {
        val publicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
    }

    @Test
    fun `test private key retrieval`() {
        val privateKeyBytes = seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(privateKeyBytes)
        assertTrue(privateKeyBytes.isNotEmpty())
    }

    @Test
    fun `test signing and verification`() {
        val message = "test message".toByteArray()
        val signature = seedPhraseKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(seedPhraseKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertTrue(!seedPhraseKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key storage`() {
        val testId = "test_key"
        val testPassword = "test_password"
        
        seedPhraseKey.store(testId, testPassword)
        // Verify that storage.set was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test key removal`() {
        val testId = "test_key"
        
        seedPhraseKey.remove(testId)
        // Verify that storage.remove was called with the correct parameters
        // This would typically be done with Mockito.verify()
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = seedPhraseKey.allKeys()
        assertEquals(testKeys, allKeys)
    }

    @Test
    fun `test mnemonic generation and validation`() {
        val generatedMnemonic = seedPhraseKey.generateMnemonic()
        assertTrue(generatedMnemonic.isNotEmpty())
        assertTrue(generatedMnemonic.split(" ").size >= 12) // BIP39 requires at least 12 words
    }

    @Test
    fun `test key derivation with different paths`() {
        val testPath = "m/44'/539'/0'/0/1"
        val derivedKey = seedPhraseKey.deriveKey(1)
        assertNotNull(derivedKey)
        assertEquals(KeyType.PRIVATE_KEY, derivedKey.keyType)
    }

    @Test
    fun `test key data parsing and serialization`() {
        val keyData = seedPhraseKey.createKeyData()
        assertTrue(keyData.isNotEmpty())
        
        val (parsedMnemonic, parsedPassphrase, parsedPath) = seedPhraseKey.parseKeyData(keyData)
        assertEquals(seedPhraseKey.mnemonic, parsedMnemonic)
        assertEquals(seedPhraseKey.passphrase, parsedPassphrase)
        assertEquals(seedPhraseKey.derivationPath, parsedPath)
    }

    @Test
    fun `test key derivation with invalid path`() {
        assertFailsWith<WalletError.InitPrivateKeyFailed> {
            seedPhraseKey.deriveKeyPair("invalid/path")
        }
    }

    @Test
    fun `test key data parsing with invalid data`() {
        val invalidData = "invalid json".toByteArray()
        assertFailsWith<WalletError.InvalidKeyStoreJSON> {
            seedPhraseKey.parseKeyData(invalidData)
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(!seedPhraseKey.isHardwareBacked)
    }
} 