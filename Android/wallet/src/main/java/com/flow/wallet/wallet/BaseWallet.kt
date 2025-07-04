package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import com.flow.wallet.storage.Cacheable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

/**
 * Serializable data class for caching account data
 * Since ChainId is not serializable, we use string keys
 */
@Serializable
data class AccountsCache(
    val accounts: Map<String, List<FlowAccount>> = emptyMap()
)

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
 * - Loading state management
 * - Security checks
 * - Account caching for improved performance
 */
abstract class BaseWallet(
    override val type: WalletType,
    override val networks: MutableSet<ChainId>,
    override val storage: StorageProtocol,
    override val securityDelegate: SecurityCheckDelegate? = null
) : Wallet, Cacheable<AccountsCache> {

    companion object {
        private const val CACHE_PREFIX = "Accounts"
    }

    // Loading state management
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Accounts as mutable map
    internal val _accounts: MutableMap<ChainId, MutableList<Account>> = mutableMapOf()
    
    // Flow accounts for caching
    internal var flowAccounts: Map<ChainId, List<FlowAccount>> = emptyMap()
    
    // Accounts as flow for reactive updates
    internal val _accountsFlow = MutableStateFlow<Map<ChainId, List<Account>>>(emptyMap())
    override val accountsFlow: StateFlow<Map<ChainId, List<Account>>> = _accountsFlow.asStateFlow()
    
    // Accounts as map (legacy support)
    override val accounts: Map<ChainId, List<Account>>
        get() = _accounts

    // Cacheable implementation
    override val cachedData: AccountsCache?
        get() = AccountsCache(
            accounts = flowAccounts.mapKeys { it.key.id }
        )

    override val cacheId: String
        get() = "$CACHE_PREFIX/${type.name}"

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

    // Helper function to convert string back to ChainId
    private fun stringToChainId(chainIdStr: String): ChainId? {
        return when (chainIdStr) {
            "flow-mainnet" -> ChainId.Mainnet
            "flow-testnet" -> ChainId.Testnet
            "flow-canarynet" -> ChainId.Canary
            "flow-emulator" -> ChainId.Emulator
            else -> null
        }
    }

    override suspend fun fetchAccounts() {
        _isLoading.value = true
        try {
            // Try to load from cache first
            val cachedAccounts = loadCache()
            if (cachedAccounts != null) {
                val accountsMap = mutableMapOf<ChainId, List<FlowAccount>>()
                
                // Convert cached string keys back to ChainId
                for ((chainIdStr, accounts) in cachedAccounts.accounts) {
                    val chainId = stringToChainId(chainIdStr) ?: continue
                    accountsMap[chainId] = accounts
                }
                
                flowAccounts = accountsMap
                _accounts.clear()
                for ((network, acc) in accountsMap) {
                    _accounts[network] = acc.map { account ->
                        Account(account, network, getKeyForAccount(), securityDelegate)
                    }.toMutableList()
                }
                _accountsFlow.value = _accounts.toMap()
            }
            
            // Fetch fresh data from networks
            fetchAllNetworkAccounts()
        } catch (e: Exception) {
            println("Error fetching accounts: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchAllNetworkAccounts() {
        val newAccounts = mutableMapOf<ChainId, MutableList<Account>>()
        val newFlowAccounts = mutableMapOf<ChainId, List<FlowAccount>>()

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

        _accounts.clear()
        _accounts.putAll(newAccounts)
        flowAccounts = newFlowAccounts
        _accountsFlow.value = _accounts.toMap()
        
        // Cache the fresh data
        try {
            cache()
        } catch (e: Exception) {
            println("Error caching accounts: ${e.message}")
        }
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
            addNetwork(network)

            // Fetch the account directly from the Flow network using flow-kmm FlowApi
            val flowApiAccount = org.onflow.flow.FlowApi(network).getAccount(address)
            // Convert from flow-kmm Account to wallet-kit FlowAccount format
            val flowAccount = FlowAccount(
                address = if (flowApiAccount.address.startsWith("0x")) flowApiAccount.address else "0x${flowApiAccount.address}",
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

        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun addAccount(account: Account) {
        val network = account.chainID
        // Get or create the network list
        val networkAccounts = _accounts.getOrPut(network) { mutableListOf() }
        
        // Remove existing account with same address if any
        networkAccounts.removeIf { it.address == account.address }

        // Add the new account
        networkAccounts.add(account)

        // Update the flow
        _accountsFlow.value = _accounts.toMap()
    }

    override suspend fun removeAccount(address: String) {
        var accountRemoved = false
        
        // Remove from all networks
        _accounts.forEach { (_, accounts) ->
            val sizeBefore = accounts.size
            accounts.removeIf { it.address == address }
            val sizeAfter = accounts.size
            
            if (sizeBefore != sizeAfter) {
                accountRemoved = true
            }
        }
        
            // Update the flow
            _accountsFlow.value = _accounts.toMap()
    }

    protected abstract fun getKeyForAccount(): KeyProtocol?
} 