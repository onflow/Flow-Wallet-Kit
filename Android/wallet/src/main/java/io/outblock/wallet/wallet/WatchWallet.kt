package io.outblock.wallet.wallet

import io.outblock.wallet.account.Account
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.StorageProtocol
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

/**
 * Watch Wallet implementation
 * A read-only wallet that can monitor addresses without signing capability
 * 
 * TODO: Implement proper account fetching
 */
class WatchWallet(
    private val address: String,
    networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
    storage: StorageProtocol
) : BaseWallet(WalletType.WATCH, networks.toMutableSet(), storage) {
    override fun getKeyForAccount(): KeyProtocol? = null

    override suspend fun addAccount(account: Account) {
        if (account.key != null) {
            throw WalletError.InvalidWalletType
        }
        val networkAccounts = accounts.getOrPut(account.chainID) { mutableListOf() }
        networkAccounts.add(account)
    }

    override suspend fun removeAccount(address: String) {
        accounts.values.forEach { accountList ->
            accountList.removeIf { it.address == address }
        }
    }

    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
        // TODO: Implement proper account fetching from blockchain
        return emptyList()
    }
} 