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
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class SeedPhraseKeyProviderTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var seedPhraseKeyProvider: SeedPhraseKeyProvider
    private val validSeedPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val invalidSeedPhrase = "invalid seed phrase"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        seedPhraseKeyProvider = SeedPhraseKey(validSeedPhrase, mockStorage)
    }

    @Test
    fun `test seed phrase validation`() {
        assertTrue(seedPhraseKeyProvider.validateSeedPhrase(validSeedPhrase))
        assertTrue(!seedPhraseKeyProvider.validateSeedPhrase(invalidSeedPhrase))
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
    fun `test mnemonic generation`() {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = seedPhraseKeyProvider.generateMnemonic(entropy)
        assertNotNull(mnemonic)
        assertTrue(mnemonic.isNotEmpty())
        assertTrue(seedPhraseKeyProvider.validateSeedPhrase(mnemonic))
    }

    @Test
    fun `test key creation with default options`() {
        val key = seedPhraseKeyProvider.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key creation with advanced options`() {
        val key = seedPhraseKeyProvider.create(Unit, mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SEED_PHRASE, key.keyType)
    }

    @Test
    fun `test key storage and retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = seedPhraseKeyProvider.createAndStore(testId, testPassword, mockStorage)
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
            seedPhraseKeyProvider.get(testId, testPassword, mockStorage)
        }
    }

    @Test
    fun `test hardware backed property`() {
        assertTrue(!seedPhraseKeyProvider.isHardwareBacked)
    }
} 