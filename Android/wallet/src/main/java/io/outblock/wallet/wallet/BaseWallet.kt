package io.outblock.wallet.wallet

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.outblock.wallet.account.Account
import io.outblock.wallet.account.SecurityCheckDelegate
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.Cacheable
import io.outblock.wallet.storage.StorageProtocol
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
) : Wallet, Cacheable {

    companion object {
        private const val CACHE_PREFIX = "Accounts"
        private val gson = Gson()
        private val typeToken = object : TypeToken<Map<ChainId, List<FlowAccount>>>() {}.type
    }

    // Loading state management
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Cacheable implementation
    override val cachedData: Any?
        get() = flowAccounts?.let { gson.toJson(it) }

    override val cacheId: String
        get() = "$CACHE_PREFIX/${type.name}"

    // Raw Flow accounts data, used for caching
    protected var flowAccounts: Map<ChainId, List<FlowAccount>>? = null

    override val accounts: MutableMap<ChainId, MutableList<Account>> = mutableMapOf()

    override suspend fun addNetwork(network: ChainId) {
        networks.add(network)
    }

    override suspend fun removeNetwork(network: ChainId) {
        networks.remove(network)
        accounts.remove(network)
    }

    override suspend fun getAccount(address: String): Account? {
        return accounts.values.flatten().find { it.address == address }
    }

    override suspend fun refreshAccounts() {
        fetchAccounts()
    }

    override suspend fun fetchAccounts() {
        _isLoading.value = true
        try {
            // Try to load from cache first
            val cachedData = loadCache()
            if (cachedData != null) {
                val cachedAccounts = gson.fromJson<Map<ChainId, List<FlowAccount>>>(cachedData.toString(), typeToken)
                flowAccounts = cachedAccounts
                accounts.clear()
                for ((network, acc) in cachedAccounts) {
                    accounts[network] = acc.map {
                        Account(it, network, getKeyForAccount(), securityDelegate)
                    }.toMutableList()
                }
            }
        } catch (e: Exception) {
            // Handle cache loading error
            println("Error loading cache: ${e.message}")
        }

        // Fetch fresh data from networks
        fetchAllNetworkAccounts()
        // Cache the new data
        cache()
    } finally {
        _isLoading.value = false
    }

    protected suspend fun fetchAllNetworkAccounts() {
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
        accounts.clear()
        accounts.putAll(newAccounts)
    }

    protected abstract fun getKeyForAccount(): KeyProtocol?
} 