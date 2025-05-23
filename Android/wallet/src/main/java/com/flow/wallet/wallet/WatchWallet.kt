package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.FlowApi

/**
 * Watch Wallet implementation
 * A read-only wallet for monitoring addresses without signing capability
 */
class WatchWallet(
    private val address: String,
    networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
    storage: StorageProtocol,
    securityDelegate: SecurityCheckDelegate? = null
) : BaseWallet(WalletType.WATCH, networks.toMutableSet(), storage, securityDelegate) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Initialize wallet by fetching accounts
        scope.launch {
            try {
                fetchAccounts()
            } catch (e: Exception) {
                println("Error initializing wallet: ${e.message}")
            }
        }
    }

    override fun getKeyForAccount(): KeyProtocol? = null

    override suspend fun addAccount(account: Account) {
        throw WalletError.InvalidWalletType
    }

    override suspend fun removeAccount(address: String) {
        var removed = false
        _accounts.values.forEach { accountList ->
            if (accountList.removeIf { it.address == address }) {
                removed = true
            }
        }
        if (removed) {
            _accountsFlow.value = _accounts.toMap()
        }
    }

    /// Fetch accounts for a specific network
    /// - Parameters:
    ///   - chainID: The network to fetch accounts from
    /// - Returns: Array of Flow accounts
    ///
    /// For watch-only wallets, this method:
    /// - Retrieves the account at the watched address
    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
        return try {
            listOf(FlowApi(network).getAccount(address))
        } catch (e: Exception) {
            println("Error fetching account for network $network: ${e.message}")
            emptyList()
        }
    }
} 