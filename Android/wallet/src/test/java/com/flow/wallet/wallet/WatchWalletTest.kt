package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.storage.InMemoryStorage
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import org.onflow.flow.models.Account as FlowAccount

class WatchWalletTest {
    private lateinit var storage: StorageProtocol
    private lateinit var testNetworks: Set<ChainId>
    private lateinit var testAddress: String
    private lateinit var wallet: WatchWallet

    @Before
    fun setup() {
        storage = InMemoryStorage()
        testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
        testAddress = "0x123"
        wallet = WatchWallet(testAddress, testNetworks, storage)
    }
    
    @Test
    fun testWatchWalletInitialization() = runBlocking {
        assertEquals(WalletType.WATCH, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testFetchAccountsForNetwork() = runBlocking {
        // Test fetching accounts for a network
        val accounts = wallet.fetchAccountsForNetwork(ChainId.Mainnet)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val testAccount = Account(
            account = FlowAccount(
                address = testAddress,
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            chainID = ChainId.Mainnet,
            key = null
        )
        
        // Test adding account
        wallet.addAccount(testAccount)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        assertNotNull(wallet.accounts[ChainId.Mainnet]?.firstOrNull { it.address == testAddress })
        
        // Test getting account
        val retrievedAccount = wallet.getAccount(testAddress)
        assertNotNull(retrievedAccount)
        if (retrievedAccount != null) {
            assertEquals(testAddress, retrievedAccount.address)
        }
        
        // Test getting non-existent account
        assertNull(wallet.getAccount("nonexistent"))
        
        // Test removing account
        wallet.removeAccount(testAddress)
        assertNull(wallet.getAccount(testAddress))
        assertTrue(wallet.accounts[ChainId.Mainnet]?.isEmpty() ?: false)
    }

    @Test
    fun testLoadingState() = runBlocking {
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        // Test cache ID
        assertEquals("Accounts/WATCH", wallet.cacheId)
        
        // Test cache data
        assertNull(wallet.cachedData)
        
        // Test caching
        wallet.cache()
        assertNull(wallet.loadCache())
    }

    @Test
    fun testMultipleNetworks() = runBlocking {
        val testAccount = Account(
            account = FlowAccount(
                address = testAddress,
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable =AccountExpandable(),
                links = null
            ),
            chainID = ChainId.Mainnet,
            key = null
        )
        
        // Add account to both networks
        wallet.addAccount(testAccount)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        
        // Remove account
        wallet.removeAccount(testAddress)
        assertEquals(0, wallet.accounts[ChainId.Mainnet]?.size ?: 0)
    }

    @Test
    fun testAccountUpdates() = runBlocking {
        val initialAccount = Account(
            account = FlowAccount(
                address = testAddress,
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            chainID = ChainId.Mainnet,
            key = null
        )
        
        val updatedAccount = Account(
            account = FlowAccount(
                address = testAddress,
                balance = "100",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            chainID = ChainId.Mainnet,
            key = null
        )
        
        // Add initial account
        wallet.addAccount(initialAccount)
        assertEquals("0", wallet.getAccount(testAddress)?.account?.balance)
        
        // Update account
        wallet.addAccount(updatedAccount)
        assertEquals("100", wallet.getAccount(testAddress)?.account?.balance)
    }
} 