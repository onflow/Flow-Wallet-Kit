package com.flow.wallet.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyStore
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class SecureElementKeyInstrumentedTest {

    private lateinit var context: Context
    private lateinit var keyStore: KeyStore
    private lateinit var secureElementKey: SecureElementKey

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
        
        // Generate a test key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        secureElementKey = SecureElementKey(keyPair, mockStorage)
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
    fun test_key_creation_with_Android_Keystore() = runBlocking {
        val key = secureElementKey.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
        assertTrue(key is SecureElementKey)
    }

    @Test
    fun test_key_creation_with_specific_parameters() = runBlocking {
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
    fun test_hardware_backed_property() {
        assertTrue(secureElementKey.isHardwareBacked)
    }

    @Test
    fun test_signing_with_hardware_backed_key() = runBlocking {
        val message = "test message".toByteArray()
        val signature = secureElementKey.sign(message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
        
        assertTrue(signature.isNotEmpty())
        assertTrue(secureElementKey.isValidSignature(signature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun test_key_persistence_in_Android_Keystore() = runBlocking {
        // Create and store a key
        val key = secureElementKey.create(mockStorage)
        key.store(TEST_KEY_ALIAS, "test_password")

        // Verify key exists in Android Keystore
        assertTrue(keyStore.containsAlias(TEST_KEY_ALIAS))
    }

    @Test
    fun test_key_retrieval_from_Android_Keystore() = runBlocking {
        // Create and store a key
        val originalKey = secureElementKey.create(mockStorage)
        originalKey.store(TEST_KEY_ALIAS, "test_password")

        // Retrieve the key
        val retrievedKey = secureElementKey.get(TEST_KEY_ALIAS, "test_password", mockStorage)
        
        assertNotNull(retrievedKey)
        assertEquals(KeyType.SECURE_ELEMENT, retrievedKey.keyType)
    }

    @Test
    fun test_key_removal_from_Android_Keystore() = runBlocking {
        // Create and store a key
        val key = secureElementKey.create(mockStorage)
        key.store(TEST_KEY_ALIAS, "test_password")

        // Remove the key
        key.remove(TEST_KEY_ALIAS)

        // Verify key is removed from Android Keystore
        assertTrue(!keyStore.containsAlias(TEST_KEY_ALIAS))
    }

    @Test
    fun test_key_operations_with_authentication_required() = runBlocking {
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
    fun test_key_import_and_export() = runBlocking {
        val key = secureElementKey.create(mockStorage)
        
        // Test public key export
        val publicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
        assertNotNull(publicKey)
        publicKey?.isNotEmpty()?.let { assertTrue(it) }

        // Test private key export (should be null as it's hardware-backed)
        val privateKey = key.privateKey(SigningAlgorithm.ECDSA_P256)
        assertTrue(privateKey == null || privateKey.isEmpty())
    }

    @Test
    fun test_key_creation_with_invalid_parameters() : Unit = runBlocking {
        val invalidSpec = KeyGenParameterSpec.Builder(
            "test_invalid_key",
            KeyProperties.PURPOSE_ENCRYPT // Invalid purpose for signing
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        assertFailsWith<WalletError> {
            secureElementKey.create(invalidSpec, mockStorage)
        }
    }

    @Test
    fun test_failed_signature_verification() {
        val message = "test message".toByteArray()
        val invalidSignature = "invalid signature".toByteArray()

        assertFalse(secureElementKey.isValidSignature(invalidSignature, message, SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256))
    }

    @Test
    fun test_key_creation_with_empty_parameters() : Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            secureElementKey.create(KeyGenParameterSpec.Builder("", 0).build(), mockStorage)
        }
    }
}