package io.outblock.wallet.wallet

import com.google.common.io.BaseEncoding
import io.outblock.wallet.Network
import io.outblock.wallet.account.Account
import io.outblock.wallet.account.SecurityCheckDelegate
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    storage: StorageProtocol,
    securityDelegate: SecurityCheckDelegate? = null
) : BaseWallet(WalletType.KEY, networks.toMutableSet(), storage, securityDelegate) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Initialize wallet by fetching accounts
        scope.launch {
            try {
                fetchAccounts()
            } catch (e: Exception) {
                println("Error initializing wallet: ${e.message}")
                // TODO: Handle initialization error (e.g., notify user, retry logic)
            }
        }
    }

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
    /// - Implements retry logic for failed requests
    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> = coroutineScope {
        val accounts = mutableListOf<FlowAccount>()
        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries) {
            try {
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
                
                // If we got here, the fetch was successful
                break
            } catch (e: Exception) {
                retryCount++
                if (retryCount == maxRetries) {
                    println("Failed to fetch accounts for network $network after $maxRetries attempts: ${e.message}")
                    throw e
                }
                // Exponential backoff
                kotlinx.coroutines.delay(1000L * (1 shl retryCount))
            }
        }

        accounts
    }
} 