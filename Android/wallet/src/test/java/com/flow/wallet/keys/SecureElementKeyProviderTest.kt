package com.flow.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class SecureElementKeyProviderTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var secureElementKeyProvider: SecureElementKeyProvider
    private lateinit var keyStore: KeyStore

    companion object {
        private const val TEST_KEY_ALIAS = "test_secure_element_key"
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        secureElementKeyProvider = SecureElementKey(keyStore, mockStorage)
    }

    @Test
    fun `test key creation with default parameters`() {
        val key = secureElementKeyProvider.create(mockStorage)
        assertNotNull(key)
        assertEquals(KeyType.SECURE_ELEMENT, key.keyType)
    }

    @Test
    fun `test key creation with specific parameters`() {
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
    }

    @Test
    fun `test key creation with authentication required`() {
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
    }

    @Test
    fun `test key creation with invalid parameters`() {
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
    fun `test key storage and retrieval`() {
        val testId = "test_key"
        val testPassword = "test_password"
        val encryptedData = "encrypted_data".toByteArray()
        
        `when`(mockStorage.get(testId)).thenReturn(encryptedData)
        
        val key = secureElementKeyProvider.createAndStore(testId, testPassword, mockStorage)
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
            secureElementKeyProvider.get(testId, testPassword, mockStorage)
        }
    }
} 