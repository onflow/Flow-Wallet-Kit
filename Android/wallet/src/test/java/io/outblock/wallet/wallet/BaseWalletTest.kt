package io.outblock.wallet.wallet

import io.outblock.wallet.account.Account
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseWalletTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    
    private class TestWallet(
        networks: Set<ChainId>,
        storage: StorageProtocol
    ) : BaseWallet(WalletType.KEY, networks.toMutableSet(), storage) {
        override fun getKeyForAccount(): KeyProtocol? = null
        
        override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
            return emptyList()
        }
    }

    @Test
    fun testWalletInitialization() = runBlocking {
        val wallet = TestWallet(testNetworks, storage)
        
        assertEquals(WalletType.KEY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testNetworkManagement() = runBlocking {
        val wallet = TestWallet(testNetworks, storage)
        
        // Test adding network
        wallet.addNetwork(ChainId.Emulator)
        assertTrue(wallet.networks.contains(ChainId.Emulator))
        
        // Test removing network
        wallet.removeNetwork(ChainId.Emulator)
        assertFalse(wallet.networks.contains(ChainId.Emulator))
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val wallet = TestWallet(testNetworks, storage)
        val testAccount = Account(
            FlowAccount(
                address = "0x123",
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = null,
                links = null
            ),
            ChainId.Mainnet,
            null
        )
        
        // Test adding account
        wallet.addAccount(testAccount)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        assertEquals(testAccount, wallet.accounts[ChainId.Mainnet]?.first())
        
        // Test getting account
        val retrievedAccount = wallet.getAccount("0x123")
        assertNotNull(retrievedAccount)
        assertEquals(testAccount, retrievedAccount)
        
        // Test removing account
        wallet.removeAccount("0x123")
        assertNull(wallet.getAccount("0x123"))
    }

    @Test
    fun testLoadingState() = runBlocking {
        val wallet = TestWallet(testNetworks, storage)
        
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        val wallet = TestWallet(testNetworks, storage)
        
        // Test cache ID
        assertEquals("Accounts/KEY", wallet.cacheId)
        
        // Test cache data
        assertNull(wallet.cachedData)
    }
} 