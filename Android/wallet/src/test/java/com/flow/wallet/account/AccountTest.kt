package com.flow.wallet.account

import com.flow.wallet.account.vm.COA
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.storage.InMemoryStorage
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.HashingAlgorithm

class AccountTest {
    private lateinit var mockFlowAccount: FlowAccount
    private lateinit var mockKey: KeyProtocol
    private lateinit var mockSecurityDelegate: SecurityCheckDelegate
    private lateinit var mockStorage: StorageProtocol
    private lateinit var account: Account

    @Before
    fun setup() {
        mockFlowAccount = mock(FlowAccount::class.java)
        mockKey = mock(KeyProtocol::class.java)
        mockSecurityDelegate = mock(SecurityCheckDelegate::class.java)
        mockStorage = InMemoryStorage()
        
        `when`(mockFlowAccount.address).thenReturn("0x1234")
        `when`(mockFlowAccount.keys).thenReturn(setOf(
            AccountPublicKey(
                index = "0",
                publicKey = "testPublicKey",
                signingAlgorithm = SigningAlgorithm.ECDSA_P256,
                hashingAlgorithm = HashingAlgorithm.SHA3_256,
                weight = "1000",
                revoked = false,
                sequenceNumber = "0"
            )
        ))

        account = Account(mockFlowAccount, ChainId.Testnet, mockKey, mockSecurityDelegate, mockStorage)
    }

    @Test
    fun `test account initialization`() {
        assertEquals(mockFlowAccount, account.account)
        assertEquals(ChainId.Testnet, account.chainID)
        assertEquals(mockKey, account.key)
        assertNull(account.childs)
        assertNull(account.coa)
    }

    @Test
    fun `test hasChild property`() {
        assertFalse(account.hasChild)
        
        account.childs = listOf(
            ChildAccount(
                address = FlowAddress("0x5678"),
                network = ChainId.Testnet,
                name = "Test Child",
                description = null,
                icon = null
            )
        )
        
        assertTrue(account.hasChild)
    }

    @Test
    fun `test hasCOA property`() {
        assertFalse(account.hasCOA)
        account.coa = mock(COA::class.java)
        assertTrue(account.hasCOA)
    }

    @Test
    fun `test canSign property`() {
        assertTrue(account.canSign) // because mockKey is not null
        
        val accountWithoutKey = Account(mockFlowAccount, ChainId.Testnet, null)
        assertFalse(accountWithoutKey.canSign)
    }

    @Test
    fun `test fetchChild`() = runBlocking {
        val childAccounts = account.fetchChild()
        
        assertNotNull(childAccounts)
        assertEquals(2, childAccounts.size) // Based on our dummy implementation
        
        val firstChild = childAccounts[0]
        assertEquals("Test Child Account", firstChild.name)
        assertEquals("A test child account", firstChild.description)
        assertEquals("https://example.com/icon.png", firstChild.icon)
    }

    @Test
    fun `test fullWeightKey property`() {
        val key = account.fullWeightKey
        assertNotNull(key)
        assertEquals("0", key?.index)
        assertEquals("1000", key?.weight)
        assertFalse(key?.revoked ?: true)
    }

    @Test
    fun `test cache operations`() = runBlocking {
        // Test cache ID
        val expectedCacheId = "Account-${ChainId.Testnet.description}-${mockFlowAccount.address}"
        assertEquals(expectedCacheId, account.cacheId)

        // Test cache data when no data is present
        assertNull(account.cachedData)

        // Set some data and test caching
        val testChilds = listOf(
            ChildAccount(
                address = FlowAddress("0x5678"),
                network = ChainId.Testnet,
                name = "Test Child",
                description = null,
                icon = null
            )
        )
        val testCOA = mock(COA::class.java)
        
        account.childs = testChilds
        account.coa = testCOA

        // Test cache data after setting values
        val cacheData = account.cachedData
        assertNotNull(cacheData)
        if (cacheData != null) {
            assertEquals(testChilds, cacheData.childs)
            assertEquals(testCOA, cacheData.coa)
        }

        // Test cache loading
        val newAccount = Account(mockFlowAccount, ChainId.Testnet, mockKey, null, mockStorage)
        newAccount.cache()
        
        val loadedAccount = Account(mockFlowAccount, ChainId.Testnet, mockKey, null, mockStorage)
        loadedAccount.loadCache()
        
        assertNotNull(loadedAccount.cachedData)
        assertEquals(testChilds, loadedAccount.cachedData?.childs)
        assertEquals(testCOA, loadedAccount.cachedData?.coa)
    }

    @Test
    fun `test cache operations with null values`() = runBlocking {
        // Test cache data with null values
        account.childs = null
        account.coa = null
        
        val cacheData = account.cachedData
        assertNotNull(cacheData)
        if (cacheData != null) {
            assertNull(cacheData.childs)
            assertNull(cacheData.coa)
        }
    }
} 