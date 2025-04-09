package io.outblock.wallet.wallet

import io.outblock.wallet.account.Account
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 */
abstract class BaseWallet(
    override val type: WalletType,
    override val networks: MutableSet<ChainId>,
    override val storage: StorageProtocol
) : Wallet {
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

    override suspend fun fetchAccounts() = coroutineScope {
        // Fetch accounts from all networks in parallel
        val networkAccounts = networks.map { network ->
            async {
                try {
                    val flowAccounts = fetchAccountsForNetwork(network)
                    val mappedAccounts = flowAccounts.mapNotNull { flowAccount ->
                        Account(flowAccount, network, getKeyForAccount())
                    }
                    network to mappedAccounts
                } catch (e: Exception) {
                    network to emptyList<Account>()
                }
            }
        }.awaitAll()

        // Update accounts map
        networkAccounts.forEach { (network, accounts) ->
            this@BaseWallet.accounts[network] = accounts.toMutableList()
        }
    }

    protected abstract fun getKeyForAccount(): KeyProtocol?
} 