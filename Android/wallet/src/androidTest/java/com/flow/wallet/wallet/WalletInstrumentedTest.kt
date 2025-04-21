package com.flow.wallet.wallet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.AndroidStorage
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
    private val testProxyAddress = "0x456"
    private val testKey = PrivateKey.generate()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = AndroidStorage(context)
    }

    @Test
    fun testKeyWalletPersistence() = runBlocking {
        // Create and save a key wallet
        val wallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(testNetworks, storage, testKey)
        wallet.save()

        // Load the wallet
        val loadedWallet = com.flow.wallet.wallet.WalletFactory.loadWallet(storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.KEY, loadedWallet.type)
        assertTrue(loadedWallet is KeyWallet)
        assertEquals(testNetworks, loadedWallet.networks)
    }

    @Test
    fun testWatchWalletPersistence() = runBlocking {
        // Create and save a watch wallet
        val wallet = com.flow.wallet.wallet.WalletFactory.createWatchWallet(testNetworks, storage, testAddress)
        wallet.save()

        // Load the wallet
        val loadedWallet = com.flow.wallet.wallet.WalletFactory.loadWallet(storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.WATCH, loadedWallet.type)
        assertTrue(loadedWallet is WatchWallet)
        assertEquals(testAddress, (loadedWallet as WatchWallet).address)
    }

    @Test
    fun testProxyWalletPersistence() = runBlocking {
        // Create and save a proxy wallet
        val wallet = com.flow.wallet.wallet.WalletFactory.createProxyWallet(testNetworks, storage, testAddress, testProxyAddress)
        wallet.save()

        // Load the wallet
        val loadedWallet = com.flow.wallet.wallet.WalletFactory.loadWallet(storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.PROXY, loadedWallet.type)
        assertTrue(loadedWallet is ProxyWallet)
        assertEquals(testAddress, (loadedWallet as ProxyWallet).address)
        assertEquals(testProxyAddress, loadedWallet.proxyAddress)
    }

    @Test
    fun testWalletStorage() = runBlocking {
        // Test storage operations with AndroidStorage
        val wallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(testNetworks, storage, testKey)
        
        // Save wallet
        wallet.save()
        
        // Clear storage
        storage.clear()
        
        // Try to load wallet
        val loadedWallet = com.flow.wallet.wallet.WalletFactory.loadWallet(storage)
        assertNotNull(loadedWallet)
        assertEquals(WalletType.UNKNOWN, loadedWallet.type)
    }

    @Test
    fun testWalletSecurity() = runBlocking {
        val wallet = com.flow.wallet.wallet.WalletFactory.createKeyWallet(testNetworks, storage, testKey)
        
        // Test security delegate operations
        wallet.securityDelegate = object : WalletSecurityDelegate {
            override fun onSecurityCheckRequired() {
                // Security check implementation
            }
        }
        
        assertNotNull(wallet.securityDelegate)
    }
} 