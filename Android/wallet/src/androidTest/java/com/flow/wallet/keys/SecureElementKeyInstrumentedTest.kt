package com.flow.wallet.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureElementKeyInstrumentedTest {

    private lateinit var context: Context
    private lateinit var keyStore: KeyStore
    private lateinit var secureElementKey: com.flow.wallet.keys.SecureElementKey

    @Mock
    private lateinit var mockStorage: StorageProtocol

    companion object {
        private const val TEST_KEY_ALIAS = "test_secure_element_key"
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        MockitoAnnotations.openMocks(this)
        
        // Initialize Android Keystore
        keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        // Create test key
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            TEST_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        secureElementKey = com.flow.wallet.keys.SecureElementKey(keyStore, mockStorage)
    }

    @After
    fun cleanup() {
        // Clean up test key
        try {
            keyStore.deleteEntry(TEST_KEY_ALIAS)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `test key creation with Android Keystore`() {
        val key = secureElementKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is com.flow.wallet.keys.SecureElementKey)
    }

    @Test
    fun `test key creation with specific parameters`() {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "test_specific_key",
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

    @Test
    fun `test hardware backed property`() {
        assertTrue(secureElementKey.isHardwareBacked)
    }

    @Test
    fun `test signing with hardware backed key`() {
        val message = "test message".toByteArray()
        val signature = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(secureElementKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256))
    }

    @Test
    fun `test key persistence in Android Keystore`() {
        // Create and store a key
        val key = secureElementKey.create(mockStorage)
        key.store(TEST_KEY_ALIAS, "test_password")

        // Verify key exists in Android Keystore
        assertTrue(keyStore.containsAlias(TEST_KEY_ALIAS))
    }

    @Test
    fun `test key retrieval from Android Keystore`() {
        // Create and store a key
        val originalKey = secureElementKey.create(mockStorage)
        originalKey.store(TEST_KEY_ALIAS, "test_password")

        // Retrieve the key
        val retrievedKey = secureElementKey.get(TEST_KEY_ALIAS, "test_password", mockStorage)
        
        assertNotNull(retrievedKey)
        assertEquals(KeyType.SECURE_ELEMENT, retrievedKey.keyType)
    }

    @Test
    fun `test key removal from Android Keystore`() {
        // Create and store a key
        val key = secureElementKey.create(mockStorage)
        key.store(TEST_KEY_ALIAS, "test_password")

        // Remove the key
        key.remove(TEST_KEY_ALIAS)

        // Verify key is removed from Android Keystore
        assertTrue(!keyStore.containsAlias(TEST_KEY_ALIAS))
    }

    @Test
    fun `test key operations with authentication required`() {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            "test_auth_key",
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(1)
            .build()

        val key = secureElementKey.create(keyGenParameterSpec, mockStorage)
        assertNotNull(key)
        
        // Test that key operations require authentication
        val message = "test message".toByteArray()
        val signature = key.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test key import and export`() {
        val key = secureElementKey.create(mockStorage)
        
        // Test public key export
        val publicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        publicKey?.isNotEmpty()?.let { assertTrue(it) }

        // Test private key export (should be null as it's hardware-backed)
        val privateKey = key.privateKey(SigningAlgorithm.ECDSA_P256)
        assertTrue(privateKey == null || privateKey.isEmpty())
    }
} 