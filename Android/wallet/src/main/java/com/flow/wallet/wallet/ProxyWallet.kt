package com.flow.wallet.wallet

import android.util.Log
import com.flow.wallet.CryptoProvider
import com.flow.wallet.Network
import com.flow.wallet.account.Account
import com.flow.wallet.keys.CryptoProviderKey
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account as FlowAccount

/**
 * Proxy Wallet implementation
 * A wallet backed by external devices like Ledger or Passkey
 */
class ProxyWallet(
    private val cryptoProvider: CryptoProvider,
    networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
    storage: StorageProtocol
) : BaseWallet(WalletType.PROXY, networks.toMutableSet(), storage) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = ProxyWallet::class.java.simpleName
    
    // Create a key adapter that wraps the crypto provider
    private val key = CryptoProviderKey(cryptoProvider, storage)

    init {
        Log.d(TAG, "Initializing ProxyWallet")
        scope.launch {
            try {
                fetchAccounts()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ProxyWallet", e)
            }
        }
    }

    override fun getKeyForAccount(): KeyProtocol = key

    override suspend fun addAccount(account: Account) {
        Log.d(TAG, "Attempting to add account: ${account.address}")
        
        // For ProxyWallet, we don't necessarily have a local KeyProtocol
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

    override suspend fun fetchAccountsForNetwork(network: ChainId): List<FlowAccount> {
        return try {
            val publicKey = cryptoProvider.getPublicKey()
            Log.d(TAG, "Fetching accounts for ProxyWallet on $network with public key: ${publicKey.take(10)}...")
            val accounts = Network.findFlowAccountByKey(publicKey, network)
            Log.d(TAG, "Found ${accounts.size} accounts on $network")
            accounts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching accounts for ProxyWallet on $network", e)
            emptyList()
        }
    }
} 