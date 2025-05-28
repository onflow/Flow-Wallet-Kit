package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.InMemoryStorage
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import kotlin.test.assertFailsWith

class KeyWalletTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    private val testKey = PrivateKey.create(storage)
    private val differentKey = PrivateKey.create(storage)
    
    @Test
    fun testKeyWalletInitialization() = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        
        assertEquals(WalletType.KEY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet.accounts.isEmpty())
        assertFalse(wallet.isLoading.first())
        assertNull(wallet.securityDelegate)
    }

    @Test
    fun testFetchAccountsForNetwork() = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        
        // Test fetching accounts for a network
        val accounts = wallet.fetchAccountsForNetwork(ChainId.Mainnet)
        assertTrue(accounts.isEmpty())
    }

    @Test
    fun testAccountManagement() = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        val testAccount = Account(
            org.onflow.flow.models.Account(
                address = testAddress,
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            ChainId.Mainnet,
            testKey
        )
        
        // Test adding account
        wallet.addAccount(testAccount)
        assertEquals(1, wallet.accounts[ChainId.Mainnet]?.size)
        
        // Test getting account
        val retrievedAccount = wallet.getAccount(testAddress)
        assertNotNull(retrievedAccount)
        assertEquals(testAddress, retrievedAccount?.address)
        
        // Test removing account
        wallet.removeAccount(testAddress)
        assertNull(wallet.getAccount(testAddress))
    }

    @Test
    fun testAccountKeyValidation(): Unit = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        val invalidAccount = Account(
            org.onflow.flow.models.Account(
                address = testAddress,
                balance = "0",
                keys = emptySet(),
                contracts = emptyMap(),
                expandable = AccountExpandable(),
                links = null
            ),
            ChainId.Mainnet,
            differentKey
        )
        
        // Test that adding an account with a different key fails
        assertFailsWith<WalletError> {
            wallet.addAccount(invalidAccount)
        }
    }

    @Test
    fun testLoadingState() = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        
        // Test loading state during refresh
        wallet.refreshAccounts()
        assertFalse(wallet.isLoading.first())
    }

    @Test
    fun testCacheOperations() = runBlocking {
        val wallet = KeyWallet(testKey, testNetworks, storage)
        
        // Test cache ID
        assertTrue(wallet.cacheId.startsWith("key_wallet_"))
        
        // Test cache data
//        assertTrue(wallet.cachedData.isEmpty())
    }
} 