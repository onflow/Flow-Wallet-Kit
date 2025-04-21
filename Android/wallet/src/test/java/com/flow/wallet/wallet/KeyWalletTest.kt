package com.flow.wallet.wallet

import io.outblock.wallet.keys.PrivateKey
import io.outblock.wallet.storage.InMemoryStorage
import io.outblock.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

class KeyWalletTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testKey = PrivateKey.generate()
    
    @Test
    fun testKeyWalletInitialization() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        
        assertEquals(WalletType.KEY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testGetKeyForAccount() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        
        val key = wallet.getKeyForAccount()
        assertNotNull(key)
        assertEquals(testKey, key)
    }

    @Test
    fun testFetchAccountsForNetwork() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        
        // Test fetching accounts for a network
        val accounts = wallet.fetchAccountsForNetwork(ChainId.Mainnet)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        val testAccount = FlowAccount(
            address = "0x123",
            balance = "0",
            keys = emptySet(),
            contracts = emptyMap(),
            expandable = null,
            links = null
        )
        
        // Test adding account
        wallet.addAccount(testAccount, ChainId.Mainnet)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        
        // Test getting account
        val retrievedAccount = wallet.getAccount("0x123")
        assertNotNull(retrievedAccount)
        assertEquals("0x123", retrievedAccount.address)
        
        // Test removing account
        wallet.removeAccount("0x123")
        assertNull(wallet.getAccount("0x123"))
    }

    @Test
    fun testLoadingState() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        val wallet = KeyWallet(testNetworks, storage, testKey)
        
        // Test cache ID
        assertEquals("Accounts/KEY", wallet.cacheId)
        
        // Test cache data
        assertNull(wallet.cachedData)
    }
} 