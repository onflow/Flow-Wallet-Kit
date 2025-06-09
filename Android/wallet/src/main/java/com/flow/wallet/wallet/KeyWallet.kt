package com.flow.wallet.wallet

import com.flow.wallet.Network
import com.flow.wallet.account.Account
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.models.SigningAlgorithm
import android.util.Log
import com.flow.wallet.keys.toFlowIndexerHex

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
    private val TAG = KeyWallet::class.java.simpleName

    init {
        Log.d(TAG, "Initializing KeyWallet with networks: ${networks.joinToString()}")
        // Initialize wallet by fetching accounts
        scope.launch {
            try {
                Log.d(TAG, "Starting initial account fetch")
                fetchAccounts()
                Log.d(TAG, "Initial account fetch completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing wallet", e)
            }
        }
    }

    public override fun getKeyForAccount(): KeyProtocol = key

    override suspend fun addAccount(account: Account) {
        Log.d(TAG, "Attempting to add account: ${account.address}")
        if (account.key != key) {
            Log.d(TAG, "Account key mismatch - rejecting account addition")
            throw WalletError.InvalidWalletType
        }
        val networkAccounts = _accounts.getOrPut(account.chainID) { mutableListOf() }
        if (!networkAccounts.contains(account)) {
            networkAccounts.add(account)
            _accountsFlow.value = _accounts.toMap()
            Log.d(TAG, "Successfully added account ${account.address} to network ${account.chainID}")
        } else {
            Log.d(TAG, "Account ${account.address} already exists in network ${account.chainID}")
        }
    }

    override suspend fun removeAccount(address: String) {
        Log.d(TAG, "Attempting to remove account: $address")
        var removed = false
        _accounts.values.forEach { accountList ->
            if (accountList.removeIf { it.address == address }) {
                removed = true
            }
        }
        if (removed) {
            _accountsFlow.value = _accounts.toMap()
            Log.d(TAG, "Successfully removed account: $address")
        } else {
            Log.d(TAG, "Account not found for removal: $address")
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
        Log.d(TAG, "Starting account fetch for network: $network")
        val accounts = mutableListOf<FlowAccount>()
        var retryCount = 0
        val maxRetries = 3
        val baseTimeout = 5000L // 5 seconds base timeout

        while (retryCount < maxRetries) {
            try {
                Log.d(TAG, "Fetch attempt ${retryCount + 1} of $maxRetries for network $network")
                
                // Get public keys for both supported signature algorithms
                val p256PublicKey = key.publicKey(SigningAlgorithm.ECDSA_P256)
                val secp256k1PublicKey = key.publicKey(SigningAlgorithm.ECDSA_secp256k1)

                if (p256PublicKey == null && secp256k1PublicKey == null) {
                    Log.d(TAG, "No valid public keys found for key indexer lookup on network $network")
                    break
                }

                Log.d(TAG, "Found ${if (p256PublicKey != null) "P256" else "no"} and ${if (secp256k1PublicKey != null) "SECP256k1" else "no"} keys for lookup")

                // Fetch accounts for both signature algorithms in parallel with timeout
                Log.d(TAG, "Starting parallel account fetch for both key types")
                val p256Accounts = async {
                    p256PublicKey?.let { publicKey ->
                        try {
                            val encodedKey = publicKey.toFlowIndexerHex()
                            Log.d(TAG, "Fetching P256 accounts for key: ${encodedKey.take(10)}...")
                            val accounts = Network.findFlowAccountByKey(encodedKey, network)
                            Log.d(TAG, "Found ${accounts.size} P256 accounts on network $network")
                            accounts.forEach { account ->
                                Log.d(TAG, "P256 Account ${account.address} with ${account.keys?.size ?: 0} keys")
                            }
                            accounts
                        } catch (e: Exception) {
                            when (e) {
                                is io.ktor.client.network.sockets.ConnectTimeoutException -> {
                                    Log.e(TAG, "Timeout while fetching P256 accounts on network $network", e)
                                }
                                else -> {
                                    Log.e(TAG, "Error looking up P256 accounts on network $network", e)
                                }
                            }
                            emptyList()
                        }
                    } ?: emptyList()
                }

                val secp256k1Accounts = async {
                    secp256k1PublicKey?.let { publicKey ->
                        try {
                            val encodedKey = publicKey.toFlowIndexerHex()
                            Log.d(TAG, "Fetching SECP256k1 accounts for key: ${encodedKey.take(10)}...")
                            val accounts = Network.findFlowAccountByKey(encodedKey, network)
                            Log.d(TAG, "Found ${accounts.size} SECP256k1 accounts on network $network")
                            accounts.forEach { account ->
                                Log.d(TAG, "SECP256k1 Account ${account.address} with ${account.keys?.size ?: 0} keys")
                            }
                            accounts
                        } catch (e: Exception) {
                            when (e) {
                                is io.ktor.client.network.sockets.ConnectTimeoutException -> {
                                    Log.e(TAG, "Timeout while fetching SECP256k1 accounts on network $network", e)
                                }
                                else -> {
                                    Log.e(TAG, "Error looking up SECP256k1 accounts on network $network", e)
                                }
                            }
                            emptyList()
                        }
                    } ?: emptyList()
                }

                Log.d(TAG, "Waiting for parallel account fetches to complete")
                // Wait for both fetches to complete and combine results
                val p256Results = p256Accounts.await()
                val secp256k1Results = secp256k1Accounts.await()
                
                accounts.addAll(p256Results)
                accounts.addAll(secp256k1Results)
                
                Log.d(TAG, "Successfully fetched accounts for network $network: ${accounts.size} total accounts found")
                accounts.forEach { account ->
                    Log.d(TAG, "Final Account ${account.address} with ${account.keys?.size ?: 0} keys")
                }
                break
            } catch (e: Exception) {
                retryCount++
                if (retryCount == maxRetries) {
                    Log.e(TAG, "Failed to fetch accounts for network $network after $maxRetries attempts", e)
                    throw e
                }
                val backoffTime = baseTimeout * (1 shl retryCount)
                Log.d(TAG, "Retry attempt $retryCount of $maxRetries for network $network. Waiting ${backoffTime}ms before retry")
                kotlinx.coroutines.delay(backoffTime)
            }
        }

        if (accounts.isEmpty()) {
            Log.d(TAG, "No accounts found for any public key on network $network")
        } else {
            Log.d(TAG, "Found ${accounts.size} accounts on network $network")
            accounts.forEach { account ->
                Log.d(TAG, "Account ${account.address} with ${account.keys?.size ?: 0} keys")
            }
        }

        accounts
    }
} 