package io.outblock.wallet.wallet

import com.google.common.io.BaseEncoding
import io.outblock.wallet.Network
import io.outblock.wallet.account.Account
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.models.SigningAlgorithm

/**
 * Key Wallet implementation
 * A wallet backed by a cryptographic key
 */
class KeyWallet(
    private val key: KeyProtocol,
    networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
    storage: StorageProtocol
) : BaseWallet(WalletType.KEY, networks.toMutableSet(), storage) {
    override fun getKeyForAccount(): KeyProtocol = key

    override suspend fun addAccount(account: Account) {
        if (account.key != key) {
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

    /// Fetch accounts for a specific network
    /// - Parameters:
    ///   - chainID: The network to fetch accounts from
    /// - Returns: Array of Flow accounts
    ///
    /// For key-based wallets, this method:
    /// - Fetches accounts associated with both P256 and SECP256k1 public keys
    /// - Performs fetches in parallel for better performance
    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> = coroutineScope {
        val accounts = mutableListOf<FlowAccount>()

        // Fetch accounts for both signature algorithms in parallel
        val p256Accounts = async {
            key.publicKey(SigningAlgorithm.ECDSA_P256)?.let { publicKey ->
                Network.findFlowAccountByKey(BaseEncoding.base16().lowerCase().encode(publicKey), network)
            } ?: emptyList()
        }

        val secp256k1Accounts = async {
            key.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.let { publicKey ->
                Network.findFlowAccountByKey(BaseEncoding.base16().lowerCase().encode(publicKey), network)
            } ?: emptyList()
        }

        // Wait for both fetches to complete and combine results
        accounts.addAll(p256Accounts.await())
        accounts.addAll(secp256k1Accounts.await())

        accounts
    }
} 