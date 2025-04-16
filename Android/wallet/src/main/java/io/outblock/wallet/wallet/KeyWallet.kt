package io.outblock.wallet.wallet

import com.google.common.io.BaseEncoding
import io.outblock.wallet.Network
import io.outblock.wallet.account.Account
import io.outblock.wallet.account.SecurityCheckDelegate
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.storage.Cacheable
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
) : BaseWallet(WalletType.KEY, networks.toMutableSet(), storage, securityDelegate), Cacheable<Map<ChainId, List<FlowAccount>>> {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override val accounts: MutableMap<ChainId, MutableList<Account>> = mutableMapOf()

    override val cacheId: String
        get() = "key_wallet_${key.publicKey(SigningAlgorithm.ECDSA_P256)?.let { BaseEncoding.base16().lowerCase().encode(it) } ?: ""}"

    override val cachedData: Map<ChainId, List<FlowAccount>>?
        get() = accounts.mapValues { (_, list) -> list.map { it.flowAccount } }

    init {
        println("Initializing KeyWallet with networks: ${networks.joinToString()}")
        // Initialize wallet by fetching accounts
        scope.launch {
            try {
                println("Starting initial account fetch")
                // Try to load from cache first
                try {
                    println("Attempting to load accounts from cache")
                    val cachedAccounts = loadCache()
                    if (cachedAccounts != null) {
                        println("Successfully loaded ${cachedAccounts.size} accounts from cache")
                        accounts.clear()
                        for ((network, acc) in cachedAccounts) {
                            accounts[network] = acc.map { account ->
                                Account(account, network, key, securityDelegate)
                            }.toMutableList()
                        }
                    } else {
                        println("No cached accounts found")
                    }
                } catch (e: Exception) {
                    println("Failed to load accounts from cache: ${e.message}")
                    e.printStackTrace()
                    // Clear cache on failure
                    try {
                        println("Clearing invalid cache")
                        deleteCache()
                    } catch (e: Exception) {
                        println("Failed to clear cache: ${e.message}")
                    }
                }
                
                fetchAccounts()
                println("Initial account fetch completed successfully")
                
                // Cache the results
                try {
                    println("Caching fetched accounts")
                    cache()
                    println("Successfully cached accounts")
                } catch (e: Exception) {
                    println("Failed to cache accounts: ${e.message}")
                }
            } catch (e: Exception) {
                println("Error initializing wallet: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun getKeyForAccount(): KeyProtocol = key

    override suspend fun addAccount(account: Account) {
        println("Attempting to add account: ${account.address}")
        if (account.key != key) {
            println("Account key mismatch - rejecting account addition")
            throw WalletError.InvalidWalletType
        }
        val networkAccounts = accounts.getOrPut(account.chainID) { mutableListOf() }
        networkAccounts.add(account)
        println("Successfully added account ${account.address} to network ${account.chainID}")
        cache() // Persist changes to cache
    }

    override suspend fun removeAccount(address: String) {
        println("Attempting to remove account: $address")
        var removed = false
        accounts.values.forEach { accountList ->
            if (accountList.removeIf { it.address == address }) {
                removed = true
            }
        }
        if (removed) {
            println("Successfully removed account: $address")
            cache() // Persist changes to cache
        } else {
            println("Account not found for removal: $address")
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
    /// - Uses key indexer API to find all related on-chain accounts
    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> = coroutineScope {
        println("Starting account fetch for network: $network")
        val accounts = mutableListOf<FlowAccount>()
        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries) {
            try {
                println("Fetch attempt ${retryCount + 1} of $maxRetries for network $network")
                
                // Get public keys for both supported signature algorithms
                val p256PublicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
                val secp256k1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_secp256k1)

                if (p256PublicKey == null && secp256k1PublicKey == null) {
                    println("No valid public keys found for key indexer lookup on network $network")
                    break
                }

                println("Found ${if (p256PublicKey != null) "P256" else "no"} and ${if (secp256k1PublicKey != null) "SECP256k1" else "no"} keys for lookup")

                // Fetch accounts for both signature algorithms in parallel
                println("Starting parallel account fetch for both key types")
                val p256Accounts = async {
                    p256PublicKey?.let { publicKey ->
                        try {
                            val encodedKey = BaseEncoding.base16().lowerCase().encode(publicKey)
                            println("Looking up P256 accounts for key: $encodedKey on network $network")
                            val accounts = Network.findFlowAccountByKey(encodedKey, network)
                            println("Found ${accounts.size} P256 accounts on network $network")
                            accounts.forEach { account ->
                                println("P256 Account ${account.address} with ${account.keys?.size ?: 0} keys")
                            }
                            accounts
                        } catch (e: Exception) {
                            println("Error looking up P256 accounts on network $network: ${e.message}")
                            e.printStackTrace()
                            emptyList()
                        }
                    } ?: emptyList()
                }

                val secp256k1Accounts = async {
                    secp256k1PublicKey?.let { publicKey ->
                        try {
                            val encodedKey = BaseEncoding.base16().lowerCase().encode(publicKey)
                            println("Looking up SECP256k1 accounts for key: $encodedKey on network $network")
                            val accounts = Network.findFlowAccountByKey(encodedKey, network)
                            println("Found ${accounts.size} SECP256k1 accounts on network $network")
                            accounts.forEach { account ->
                                println("SECP256k1 Account ${account.address} with ${account.keys?.size ?: 0} keys")
                            }
                            accounts
                        } catch (e: Exception) {
                            println("Error looking up SECP256k1 accounts on network $network: ${e.message}")
                            e.printStackTrace()
                            emptyList()
                        }
                    } ?: emptyList()
                }

                println("Waiting for parallel account fetches to complete")
                // Wait for both fetches to complete and combine results
                val p256Results = p256Accounts.await()
                val secp256k1Results = secp256k1Accounts.await()
                
                accounts.addAll(p256Results)
                accounts.addAll(secp256k1Results)
                
                println("Successfully fetched accounts for network $network: ${accounts.size} total accounts found")
                accounts.forEach { account ->
                    println("Final Account ${account.address} with ${account.keys?.size ?: 0} keys")
                }
                break
            } catch (e: Exception) {
                retryCount++
                if (retryCount == maxRetries) {
                    println("Failed to fetch accounts for network $network after $maxRetries attempts: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
                val backoffTime = 1000L * (1 shl retryCount)
                println("Retry attempt $retryCount of $maxRetries for network $network. Waiting ${backoffTime}ms before retry")
                kotlinx.coroutines.delay(backoffTime)
            }
        }

        if (accounts.isEmpty()) {
            println("No accounts found for any public key on network $network")
        } else {
            println("Found ${accounts.size} accounts on network $network")
            accounts.forEach { account ->
                println("Account ${account.address} with ${account.keys?.size ?: 0} keys")
            }
        }

        accounts
    }
} 