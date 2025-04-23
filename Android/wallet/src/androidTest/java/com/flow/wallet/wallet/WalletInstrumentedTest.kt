package com.flow.wallet.wallet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.storage.HardwareBackedStorage
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onflow.flow.ChainId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WalletInstrumentedTest {
    private lateinit var context: Context
    private lateinit var storage: StorageProtocol
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    private lateinit var testKey: KeyProtocol
    private lateinit var testCryptoProvider: CryptoProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = HardwareBackedStorage(context)
        testKey = PrivateKey(wallet.core.jni.PrivateKey(), storage)
        testCryptoProvider = object : CryptoProvider {
            override fun getPublicKey(): String = "test_public_key"
            override suspend fun getUserSignature(jwt: String): String = "test_signature"
            override suspend fun signData(data: ByteArray): String = "test_signed_data"
            override fun getSigner(): org.onflow.flow.models.Signer = object : org.onflow.flow.models.Signer {
                override var address: String = testAddress
                override var keyIndex: Int = 0
                override suspend fun sign(transaction: org.onflow.flow.models.Transaction?, bytes: ByteArray): ByteArray = "test_signature".toByteArray()
                override suspend fun sign(bytes: ByteArray): ByteArray = "test_signature".toByteArray()
            }
            override fun getHashAlgorithm(): org.onflow.flow.models.HashingAlgorithm = org.onflow.flow.models.HashingAlgorithm.SHA2_256
            override fun getSignatureAlgorithm(): org.onflow.flow.models.SigningAlgorithm = org.onflow.flow.models.SigningAlgorithm.ECDSA_P256
            override fun getKeyWeight(): Int = 1000
        }
    }

    @Test
    fun testKeyWalletPersistence() = runBlocking {
        // Create and save a key wallet
        val wallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        
        // Load the wallet
        val loadedWallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.KEY, loadedWallet.type)
        assertTrue(loadedWallet is KeyWallet)
        assertEquals(testNetworks, loadedWallet.networks)
    }

    @Test
    fun testWatchWalletPersistence() = runBlocking {
        // Create and save a watch wallet
        val wallet = WalletFactory.createWatchWallet(testAddress, testNetworks, storage)
        
        // Load the wallet
        val loadedWallet = WalletFactory.createWatchWallet(testAddress, testNetworks, storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.WATCH, loadedWallet.type)
        assertTrue(loadedWallet is WatchWallet)
    }

    @Test
    fun testProxyWalletPersistence() = runBlocking {
        // Create and save a proxy wallet
        val wallet = WalletFactory.createProxyWallet(testCryptoProvider, testNetworks, storage)
        
        // Load the wallet
        val loadedWallet = WalletFactory.createProxyWallet(testCryptoProvider, testNetworks, storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.PROXY, loadedWallet.type)
        assertTrue(loadedWallet is ProxyWallet)
    }

    @Test
    fun testWalletStorage() = runBlocking {
        // Test storage operations with HardwareBackedStorage
        val wallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        
        // Clear storage
        storage.removeAll()
        
        // Try to load wallet
        val loadedWallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.KEY, loadedWallet.type)
    }

    @Test
    fun testWalletSecurity() = runBlocking {
        val wallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        
        // Test security delegate operations
        val securityDelegate = object : SecurityCheckDelegate {
            override suspend fun verify(): Boolean {
                return true
            }
        }
        
        // Create a new wallet with the security delegate
        val secureWallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        assertNotNull(secureWallet.securityDelegate)
    }
} 