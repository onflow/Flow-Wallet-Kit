package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.storage.InMemoryStorage
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import kotlin.test.assertNotNull

class ProxyWalletTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    
    @Test
    fun testProxyWalletInitialization() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        
        assertEquals(WalletType.PROXY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testGetKeyForAccount() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        
        // Proxy wallet should not have a key
        assertNull(wallet.getKeyForAccount())
    }

    @Test
    fun testFetchAccountsForNetwork() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        
        // Test fetching accounts for a network
        val accounts = wallet.fetchAccountsForNetwork(ChainId.Mainnet)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        val testAccount = Account(
            org.onflow.flow.models.Account(
                address = "0x123",
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            ChainId.Mainnet,
            null
        )
        
        // Test adding account
        wallet.addAccount(testAccount)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        
        // Test getting account
        val retrievedAccount = wallet.getAccount(testAddress)
        assertNotNull(retrievedAccount)
        assertEquals(testAddress, retrievedAccount.address)
        
        // Test removing account
        wallet.removeAccount(testAddress)
        assertNull(wallet.getAccount(testAddress))
    }

    @Test
    fun testLoadingState() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        val wallet = ProxyWallet(testNetworks, storage)
        
        // Test cache ID
        assertEquals("Accounts/PROXY", wallet.cacheId)
        
        // Test cache data
        assertNull(wallet.cachedData)
    }
} 