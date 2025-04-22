package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import com.trustwallet.wallet.core.CoinType
import com.trustwallet.wallet.core.HDWallet
import com.trustwallet.wallet.core.PrivateKey
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

@RunWith(MockitoJUnitRunner::class)
class SeedPhraseKeyTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var keyPair: KeyPair
    private lateinit var seedPhraseKey: SeedPhraseKey
    private lateinit var hdWallet: HDWallet

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Generate key using Trust Wallet Core
        hdWallet = HDWallet.generate()
        val privateKey = hdWallet.getKeyForCoin(CoinType.FLOW)
        val publicKey = privateKey.getPublicKeySecp256k1(false)
        keyPair = KeyPair(publicKey, privateKey)
        seedPhraseKey = SeedPhraseKey(hdWallet.mnemonic, "", "m/44'/539'/0'/0/0", keyPair, mockStorage)
    }

    @Test
    fun `test key creation with default options`() = runBlocking {
        val key = seedPhraseKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        assertTrue(key is SeedPhraseKey)
    }

    @Test
    fun `test key creation with advanced options`() = runBlocking {
        val key = seedPhraseKey.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        assertTrue(key is SeedPhraseKey)
    }

    @Test
    fun `test key creation and storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKey.createAndStore(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test key retrieval`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKey.get(testId, testPassword, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key retrieval with invalid password`() = runBlocking {
        val testId = "test_key"
        val testPassword = "invalid_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        assertFailsWith<WalletError.InvalidPassword> {
            seedPhraseKey.get(testId, testPassword, mockStorage)
        }
        verify(mockStorage).get(testId)
    }

    @Test
    fun `test key restoration`() = runBlocking {
        val secret = hdWallet.mnemonic.toByteArray()
        val restoredKey = seedPhraseKey.restore(secret, mockStorage)
        assertNotNull(restoredKey)
        assertEquals(KeyType.SEED_PHRASE, restoredKey.keyType)
    }

    @Test
    fun `test public key retrieval for different algorithms`() {
        val p256Key = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        assertTrue(p256Key.isNotEmpty())
        assertTrue(secp256k1Key.isNotEmpty())
        assertFalse(p256Key.contentEquals(secp256k1Key))
    }

    @Test
    fun `test private key retrieval for different algorithms`() {
        val p256Key = seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_P256)
        val secp256k1Key = seedPhraseKey.privateKey(SigningAlgorithm.ECDSA_secp256k1)
        
        assertNotNull(p256Key)
        assertNotNull(secp256k1Key)
        assertTrue(p256Key.isNotEmpty())
        assertTrue(secp256k1Key.isNotEmpty())
        assertFalse(p256Key.contentEquals(secp256k1Key))
    }

    @Test
    fun `test signing and verification`() = runBlocking {
        val message = "test message".toByteArray()
        val signature = seedPhraseKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(seedPhraseKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test signing with different hashing algorithms`() = runBlocking {
        val message = "test message".toByteArray()
        
        val sha2_256 = seedPhraseKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        val sha3_256 = seedPhraseKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA3_256)
        
        assertTrue(sha2_256.isNotEmpty())
        assertTrue(sha3_256.isNotEmpty())
        assertFalse(sha2_256.contentEquals(sha3_256))
    }

    @Test
    fun `test invalid signature verification`() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()
        
        assertFalse(seedPhraseKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key storage`() = runBlocking {
        val testId = "test_key"
        val testPassword = "test_password"
        
        seedPhraseKey.store(testId, testPassword)
        verify(mockStorage).set(testId, any())
    }

    @Test
    fun `test key removal`() = runBlocking {
        val testId = "test_key"
        
        seedPhraseKey.remove(testId)
        verify(mockStorage).remove(testId)
    }

    @Test
    fun `test getting all keys`() {
        val testKeys = listOf("key1", "key2")
        `when`(mockStorage.allKeys).thenReturn(testKeys)
        
        val allKeys = seedPhraseKey.allKeys()
        assertEquals(testKeys, allKeys)
        verify(mockStorage).allKeys
    }

    @Test
    fun `test mnemonic generation and validation`() {
        val generatedMnemonic = HDWallet.generateMnemonic()
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
    fun `test hardware backed property`() {
        assertFalse(seedPhraseKey.isHardwareBacked)
    }
} 