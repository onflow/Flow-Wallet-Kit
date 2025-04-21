package com.flow.wallet.wallet

import io.outblock.wallet.keys.PrivateKey
import io.outblock.wallet.storage.InMemoryStorage
import io.outblock.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.onflow.flow.ChainId

class WalletFactoryTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    private val testProxyAddress = "0x456"
    private val testKey = PrivateKey.generate()
    
    @Test
    fun testCreateKeyWallet() = runBlocking {
        val wallet = WalletFactory.createKeyWallet(testNetworks, storage, testKey)
        
        assertNotNull(wallet)
        assertEquals(WalletType.KEY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is KeyWallet)
    }

    @Test
    fun testCreateWatchWallet() = runBlocking {
        val wallet = WalletFactory.createWatchWallet(testNetworks, storage, testAddress)
        
        assertNotNull(wallet)
        assertEquals(WalletType.WATCH, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is WatchWallet)
        assertEquals(testAddress, (wallet as WatchWallet).address)
    }

    @Test
    fun testCreateProxyWallet() = runBlocking {
        val wallet = WalletFactory.createProxyWallet(testNetworks, storage, testAddress, testProxyAddress)
        
        assertNotNull(wallet)
        assertEquals(WalletType.PROXY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is ProxyWallet)
        assertEquals(testAddress, (wallet as ProxyWallet).address)
        assertEquals(testProxyAddress, wallet.proxyAddress)
    }

    @Test
    fun testCreateWalletWithType() = runBlocking {
        // Test creating KEY wallet
        val keyWallet = WalletFactory.createWallet(WalletType.KEY, testNetworks, storage, testKey)
        assertNotNull(keyWallet)
        assertEquals(WalletType.KEY, keyWallet.type)
        assertTrue(keyWallet is KeyWallet)

        // Test creating WATCH wallet
        val watchWallet = WalletFactory.createWallet(WalletType.WATCH, testNetworks, storage, testAddress)
        assertNotNull(watchWallet)
        assertEquals(WalletType.WATCH, watchWallet.type)
        assertTrue(watchWallet is WatchWallet)

        // Test creating PROXY wallet
        val proxyWallet = WalletFactory.createWallet(WalletType.PROXY, testNetworks, storage, testAddress, testProxyAddress)
        assertNotNull(proxyWallet)
        assertEquals(WalletType.PROXY, proxyWallet.type)
        assertTrue(proxyWallet is ProxyWallet)
    }

    @Test
    fun testCreateWalletWithInvalidType() = runBlocking {
        // Test creating wallet with invalid type
        val wallet = WalletFactory.createWallet(WalletType.UNKNOWN, testNetworks, storage)
        assertNotNull(wallet)
        assertEquals(WalletType.UNKNOWN, wallet.type)
    }
} 