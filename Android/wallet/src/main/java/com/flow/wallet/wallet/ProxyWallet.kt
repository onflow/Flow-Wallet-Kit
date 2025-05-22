package com.flow.wallet.wallet

import com.flow.wallet.account.Account
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

/**
 * Proxy Wallet implementation
 * A wallet backed by external devices like Ledger or Passkey
 * 
 * TODO: Implement proper hardware device integration and account fetching
 */
class ProxyWallet(
    networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
    storage: StorageProtocol
) : BaseWallet(WalletType.PROXY, networks.toMutableSet(), storage) {
    override fun getKeyForAccount(): KeyProtocol? = null

    override suspend fun addAccount(account: Account) {
        if (account.key != null) {
            throw WalletError.InvalidWalletType
        }
        val networkAccounts = accounts.getOrPut(account.chainID) { mutableListOf() }
        networkAccounts.add(account)
    }

    override suspend fun awaitFirstAccount() {
        while (accounts.isEmpty()) {
            kotlinx.coroutines.delay(50)
        }
    }

    override suspend fun removeAccount(address: String) {
        accounts.values.forEach { accountList ->
            accountList.removeIf { it.address == address }
        }
    }

    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
        // TODO: Implement proper account fetching using hardware device
        return emptyList()
    }
} 