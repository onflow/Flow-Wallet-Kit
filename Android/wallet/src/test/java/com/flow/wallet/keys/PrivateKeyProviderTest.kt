package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.security.KeyPair
import java.security.KeyPairGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class PrivateKeyProviderTest {

    @Mock
    private lateinit var mockStorage: StorageProtocol

    private lateinit var privateKeyProvider: PrivateKeyProvider
    private lateinit var keyPair: KeyPair

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        keyPair = keyPairGenerator.generateKeyPair()
        privateKeyProvider = PrivateKey(keyPair, mockStorage)
    }

    @Test
    fun `test export private key in PKCS8 format`() {
        val exportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.PKCS8)
        assertNotNull(exportedKey)
        assertTrue(exportedKey.isNotEmpty())
    }

    @Test
    fun `test export private key in RAW format`() {
        val exportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.RAW)
        assertNotNull(exportedKey)
        assertTrue(exportedKey.isNotEmpty())
    }

    @Test
    fun `test import private key in PKCS8 format`() {
        val exportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.PKCS8)
        privateKeyProvider.importPrivateKey(exportedKey, KeyFormat.PKCS8)
        // Verify that the key was imported successfully
        val reExportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.PKCS8)
        assertEquals(exportedKey.size, reExportedKey.size)
    }

    @Test
    fun `test import private key in RAW format`() {
        val exportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.RAW)
        privateKeyProvider.importPrivateKey(exportedKey, KeyFormat.RAW)
        // Verify that the key was imported successfully
        val reExportedKey = privateKeyProvider.exportPrivateKey(KeyFormat.RAW)
        assertEquals(exportedKey.size, reExportedKey.size)
    }

    @Test
    fun `test import invalid private key`() {
        val invalidKey = ByteArray(32) { it.toByte() }
        assertFailsWith<WalletError.InvalidPrivateKey> {
            privateKeyProvider.importPrivateKey(invalidKey, KeyFormat.PKCS8)
        }
    }

    @Test
    fun `test export and import roundtrip`() {
        val originalKey = privateKeyProvider.exportPrivateKey(KeyFormat.PKCS8)
        privateKeyProvider.importPrivateKey(originalKey, KeyFormat.PKCS8)
        val importedKey = privateKeyProvider.exportPrivateKey(KeyFormat.PKCS8)
        
        assertEquals(originalKey.size, importedKey.size)
        assertTrue(originalKey.contentEquals(importedKey))
    }
} 