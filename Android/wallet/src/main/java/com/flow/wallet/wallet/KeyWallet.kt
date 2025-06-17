package com.flow.wallet.wallet

import com.flow.wallet.Network
import com.flow.wallet.NativeLibraryManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
        // Ensure native library is loaded before proceeding
        if (!NativeLibraryManager.ensureLibraryLoaded()) {
            Log.e(TAG, "Cannot initialize KeyWallet: TrustWalletCore not available")
            throw WalletError.InitHDWalletFailed
        }
        
        Log.d(TAG, "Initializing KeyWallet with networks: ${networks.joinToString()}")
        // Initialize wallet by fetching accounts with proper error handling
        scope.launch {
            try {
                Log.d(TAG, "Starting initial account fetch")
                checkMemoryBeforeOperation()
                fetchAccounts()
                Log.d(TAG, "Initial account fetch completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing wallet", e)
                // Don't crash the app, just log the error
            }
        }
    }

    override fun getKeyForAccount(): KeyProtocol = key

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
    /// - Performs fetches sequentially to reduce resource usage
    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
        NativeLibraryManager.throwIfNotLoaded()
        checkMemoryBeforeOperation()
        
        val accounts = mutableListOf<FlowAccount>()
        val maxRetries = 2 // Reduced from potentially infinite retries
        val baseTimeout = 10000L // 10 seconds instead of longer timeouts
        var retryCount = 0

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

                // Fetch accounts sequentially instead of in parallel to reduce resource usage
                Log.d(TAG, "Starting sequential account fetch for both key types")
                
                // Fetch P256 accounts first
                p256PublicKey?.let { publicKey ->
                    try {
                        val encodedKey = publicKey.toFlowIndexerHex()
                        Log.d(TAG, "Fetching P256 accounts for key: ${encodedKey.take(10)}...")
                        val p256Accounts = withTimeout(baseTimeout) {
                            Network.findFlowAccountByKey(encodedKey, network)
                        }
                        Log.d(TAG, "Found ${p256Accounts.size} P256 accounts on network $network")
                        accounts.addAll(p256Accounts)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error looking up P256 accounts on network $network", e)
                    }
                }

                // Small delay between requests to be nicer to the server
                delay(500)

                // Fetch SECP256k1 accounts second
                secp256k1PublicKey?.let { publicKey ->
                    try {
                        val encodedKey = publicKey.toFlowIndexerHex()
                        Log.d(TAG, "Fetching SECP256k1 accounts for key: ${encodedKey.take(10)}...")
                        val secp256k1Accounts = withTimeout(baseTimeout) {
                            Network.findFlowAccountByKey(encodedKey, network)
                        }
                        Log.d(TAG, "Found ${secp256k1Accounts.size} SECP256k1 accounts on network $network")
                        accounts.addAll(secp256k1Accounts)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error looking up SECP256k1 accounts on network $network", e)
                    }
                }
                
                Log.d(TAG, "Successfully fetched accounts for network $network: ${accounts.size} total accounts found")
                break
            } catch (e: Exception) {
                retryCount++
                if (retryCount == maxRetries) {
                    Log.e(TAG, "Failed to fetch accounts for network $network after $maxRetries attempts", e)
                    // Don't throw exception, just return what we have
                    break
                }
                val backoffTime = baseTimeout * (1 shl retryCount)
                Log.d(TAG, "Retry attempt $retryCount of $maxRetries for network $network. Waiting ${backoffTime}ms before retry")
                delay(backoffTime)
            }
        }

        if (accounts.isEmpty()) {
            Log.d(TAG, "No accounts found for any public key on network $network")
        } else {
            Log.d(TAG, "Found ${accounts.size} accounts on network $network")
        }

        return accounts
    }
    
    /**
     * Check available memory before performing memory-intensive operations
     */
    private fun checkMemoryBeforeOperation() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        Log.d(TAG, "Memory usage: ${memoryUsagePercent.toInt()}% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
        
        if (memoryUsagePercent > 85) {
            Log.w(TAG, "High memory usage detected, forcing garbage collection")
            System.gc()
            
            // Check again after GC
            val newUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            val newMemoryUsagePercent = (newUsedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            if (newMemoryUsagePercent > 90) {
                throw WalletError.LoadCacheFailed
            }
        }
    }
} 