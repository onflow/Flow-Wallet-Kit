package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.Cacheable
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

/**
 * Base interface for all wallet types
 */
interface Wallet {
    val type: WalletType
    val accounts: Map<ChainId, List<Account>>
    val accountsFlow: StateFlow<Map<ChainId, List<Account>>>
    val networks: Set<ChainId>
    val storage: StorageProtocol
    val isLoading: StateFlow<Boolean>
    val securityDelegate: SecurityCheckDelegate?
    
    suspend fun addNetwork(network: ChainId)
    suspend fun removeNetwork(network: ChainId)
    suspend fun addAccount(account: Account)
    suspend fun removeAccount(address: String)
    suspend fun getAccount(address: String): Account?
    suspend fun refreshAccounts()
    suspend fun fetchAccounts()
    suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount>
    suspend fun fetchAccountByAddress(address: String, network: ChainId)
}

/**
 * Enum representing different wallet types
 */
enum class WalletType {
    WATCH,
    KEY,
    PROXY
}

/**
 * Base class for wallet implementations
 * Provides core wallet functionality including:
 * - Account management across multiple networks
 * - Caching of account data
 * - Loading state management
 * - Security checks
 */
abstract class BaseWallet(
    override val type: WalletType,
    override val networks: MutableSet<ChainId>,
    override val storage: StorageProtocol,
    override val securityDelegate: SecurityCheckDelegate? = null
) : Wallet, Cacheable<Map<ChainId, List<FlowAccount>>> {

    companion object {
        private const val CACHE_PREFIX = "Accounts"
    }

    // Loading state management
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Cacheable implementation
    override val cachedData: Map<ChainId, List<FlowAccount>>?
        get() = flowAccounts

    override val cacheId: String
        get() = "$CACHE_PREFIX/${type.name}"

    // Raw Flow accounts data, used for caching
    private var flowAccounts: Map<ChainId, List<FlowAccount>>? = null

    // Accounts as mutable map
    internal val _accounts: MutableMap<ChainId, MutableList<Account>> = mutableMapOf()
    
    // Accounts as flow for reactive updates
    internal val _accountsFlow = MutableStateFlow<Map<ChainId, List<Account>>>(emptyMap())
    override val accountsFlow: StateFlow<Map<ChainId, List<Account>>> = _accountsFlow.asStateFlow()
    
    // Accounts as map (legacy support)
    override val accounts: Map<ChainId, List<Account>>
        get() = _accounts

    override suspend fun addNetwork(network: ChainId) {
        networks.add(network)
    }

    override suspend fun removeNetwork(network: ChainId) {
        networks.remove(network)
        _accounts.remove(network)
        _accountsFlow.value = _accounts.toMap()
    }

    override suspend fun getAccount(address: String): Account? {
        return _accounts.values.flatten().find { it.address == address }
    }

    override suspend fun refreshAccounts() {
        fetchAccounts()
    }

    override suspend fun fetchAccounts() {
        _isLoading.value = true
        try {
            // Try to load from cache first
            val cachedAccounts = loadCache()
            if (cachedAccounts != null) {
                flowAccounts = cachedAccounts
                _accounts.clear()
                for ((network, acc) in cachedAccounts) {
                    _accounts[network] = acc.map { account ->
                        Account(account, network, getKeyForAccount(), securityDelegate)
                    }.toMutableList()
                }
                _accountsFlow.value = _accounts.toMap()
            }

            // Fetch fresh data from networks
            fetchAllNetworkAccounts()
            // Cache the new data
            cache()
        } catch (e: Exception) {
            // Handle cache loading error
            println("Error loading cache: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchAllNetworkAccounts() {
        val newFlowAccounts = mutableMapOf<ChainId, List<FlowAccount>>()
        val newAccounts = mutableMapOf<ChainId, MutableList<Account>>()

        // Fetch accounts from all networks in parallel
        coroutineScope {
            val networkFetches = networks.map { network ->
                async {
                    try {
                        val accounts = fetchAccountsForNetwork(network)
                        if (accounts.isNotEmpty()) {
                            newFlowAccounts[network] = accounts
                            newAccounts[network] = accounts.map { 
                                Account(it, network, getKeyForAccount(), securityDelegate)
                            }.toMutableList()
                        }
                    } catch (e: Exception) {
                        println("Error fetching accounts for network $network: ${e.message}")
                    }
                }
            }
            networkFetches.awaitAll()
        }

        flowAccounts = newFlowAccounts
        _accounts.clear()
        _accounts.putAll(newAccounts)
        _accountsFlow.value = _accounts.toMap()
    }

    /**
     * Fetch account directly by address from the Flow network
     * This is useful for newly created accounts that may not be indexed yet
     * @param address The Flow address to fetch
     * @param network The network where the account exists
     * @throws Exception if the account cannot be fetched
     */
    override suspend fun fetchAccountByAddress(address: String, network: ChainId) {
        try {
            if (!networks.contains(network)) {
                addNetwork(network)
            }
            
            // Fetch the account directly from the Flow network using flow-kmm FlowApi
            val flowApiAccount = org.onflow.flow.FlowApi(network).getAccount(address)
            
            // Convert from flow-kmm Account to wallet-kit FlowAccount format
            val flowAccount = FlowAccount(
                address = flowApiAccount.address,
                balance = flowApiAccount.balance,
                keys = flowApiAccount.keys?.map { key ->
                    org.onflow.flow.models.AccountPublicKey(
                        index = key.index,
                        publicKey = key.publicKey,
                        signingAlgorithm = key.signingAlgorithm,
                        hashingAlgorithm = key.hashingAlgorithm,
                        weight = key.weight,
                        revoked = key.revoked,
                        sequenceNumber = key.sequenceNumber
                    )
                }?.toSet() ?: emptySet(),
                contracts = flowApiAccount.contracts,
                expandable = flowApiAccount.expandable,
                links = flowApiAccount.links
            )
            
            // Create wrapper account
            val account = Account(flowAccount, network, getKeyForAccount(), securityDelegate)
            
            // Add to wallet
            addAccount(account)
            
            // Update cached data
            val currentFlowAccounts = flowAccounts?.toMutableMap() ?: mutableMapOf()
            val networkAccounts = currentFlowAccounts[network]?.toMutableList() ?: mutableListOf()
            
            // Remove existing account with same address if any
            networkAccounts.removeIf { it.address == address }
            // Add the new account
            networkAccounts.add(flowAccount)
            currentFlowAccounts[network] = networkAccounts
            
            flowAccounts = currentFlowAccounts
            cache() // Persist to cache
            
            println("Successfully fetched and added account $address to network $network")
        } catch (e: Exception) {
            println("Error fetching account $address from network $network: ${e.message}")
            throw e
        }
    }

    protected abstract fun getKeyForAccount(): KeyProtocol?
} 