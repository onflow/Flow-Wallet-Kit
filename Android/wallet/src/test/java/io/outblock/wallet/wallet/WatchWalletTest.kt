package io.outblock.wallet.wallet

import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.InMemoryStorage
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchWalletTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    
    @Test
    fun testWatchWalletInitialization() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        assertEquals(WalletType.WATCH, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testGetKeyForAccount() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        // Watch wallet should not have a key
        assertNull(wallet.getKeyForAccount())
    }

    @Test
    fun testFetchAccountsForNetwork() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        // Test fetching accounts for a network
        val accounts = wallet.fetchAccountsForNetwork(ChainId.Mainnet)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        val testAccount = FlowAccount(
            address = testAddress,
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
        val retrievedAccount = wallet.getAccount(testAddress)
        assertNotNull(retrievedAccount)
        assertEquals(testAddress, retrievedAccount.address)
        
        // Test removing account
        wallet.removeAccount(testAddress)
        assertNull(wallet.getAccount(testAddress))
    }

    @Test
    fun testLoadingState() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        // Test cache ID
        assertEquals("Accounts/WATCH", wallet.cacheId)
        
        // Test cache data
        assertNull(wallet.cachedData)
    }

    @Test
    fun testAddressProperty() = runBlocking {
        val wallet = WatchWallet(testNetworks, storage, testAddress)
        
        // Test that the watched address is correctly stored
        assertEquals(testAddress, wallet.address)
    }
} 