package com.flow.wallet.account

import com.flow.wallet.account.vm.COA
import com.flow.wallet.account.vm.COA.Companion.createCOA
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.security.SecurityCheckDelegate
import com.flow.wallet.storage.Cacheable
import com.flow.wallet.storage.FileSystemStorage
import com.flow.wallet.storage.StorageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.evm.EVMManager
import org.onflow.flow.models.Account
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.Transaction
import java.io.File

/**
 * Represents a Flow blockchain account with signing capabilities
 */
class Account(
    val account: Account,
    val chainID: ChainId,
    val key: KeyProtocol?,
    private val securityDelegate: SecurityCheckDelegate? = null,
    override val storage: StorageProtocol = FileSystemStorage(File(System.getProperty("java.io.tmpdir"), "account_storage"))
) : Cacheable<AccountCache> {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tokenBalances = MutableStateFlow<List<TokenBalance>>(emptyList())
    val tokenBalances: StateFlow<List<TokenBalance>> = _tokenBalances.asStateFlow()

    val evmManager = EVMManager(chainID)

    init {
        // Initialize account by fetching linked accounts
        scope.launch {
            try {
                loadLinkedAccounts()
            } catch (e: Exception) {
                println("Error initializing account: ${e.message}")
            }
        }
    }

    // Properties
    var childs: List<ChildAccount>? = null
    var coa: COA? = null

    val hasChild: Boolean
        get() = !(childs?.isEmpty() ?: true)

    val hasCOA: Boolean
        get() = coa != null

    val hasLinkedAccounts: Boolean
        get() = hasChild || hasCOA

    val canSign: Boolean
        get() = key != null

    val hexAddr: String
        get() = account.address

    // Key Management
    val fullWeightKey: AccountPublicKey?
        get() = fullWeightKeys.firstOrNull()

    val hasFullWeightKey: Boolean
        get() = fullWeightKeys.isNotEmpty()

    private val fullWeightKeys: List<AccountPublicKey>
        get() = account.keys?.filter { !it.revoked && it.weight >= 1000.toString() } ?: emptyList()

    // Cacheable implementation
    override val cachedData: AccountCache?
        get() = AccountCache(childs, coa)

    override val cacheId: String
        get() = "Account-${chainID.description}-${account.address}"

    private fun findKeyInAccount(): List<AccountPublicKey> {
        val keyInstance = key ?: return emptyList()
        val keys = mutableListOf<AccountPublicKey>()

        val p256PublicKey = keyInstance.publicKey(SigningAlgorithm.ECDSA_P256)
        val secpPublicKey = keyInstance.publicKey(SigningAlgorithm.ECDSA_secp256k1)

        val matchingKeys = account.keys?.filter {
            !it.revoked &&
                    it.weight.toInt() >= 1000 && (
                    (p256PublicKey != null && it.publicKey.equals(p256PublicKey.toString(Charsets.ISO_8859_1), ignoreCase = true)) ||
                    (secpPublicKey != null && it.publicKey.equals(secpPublicKey.toString(Charsets.ISO_8859_1), ignoreCase = true))
            )
        }
        keys.addAll(matchingKeys ?: emptyList())

        return keys
    }

    // Account Relationships
    suspend fun loadLinkedAccounts() {
        _isLoading.value = true
        try {
            // Try to load from cache first
            val cached = loadCache()
            if (cached != null) {
                childs = cached.childs
                coa = cached.coa
            }

            // Fetch fresh data
            val (vmAccounts, childAccounts) = fetchLinkedAccounts()
            coa = vmAccounts
            childs = childAccounts

            // Cache the results
            cache()
        } catch (e: Exception) {
            println("Error loading linked accounts: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchLinkedAccounts(): Pair<COA?, List<ChildAccount>> = coroutineScope {
        val vmFetch = async { fetchVM() }
        val childFetch = async { fetchChild() }
        Pair(vmFetch.await(), childFetch.await())
    }

    suspend fun fetchChild(): List<ChildAccount> {
        val childs = evmManager.getChildAccountMetadata(FlowAddress(account.address))
        val childAccounts = childs.mapNotNull { (addr, metadata) ->
            ChildAccount(
                address = FlowAddress(addr),
                network = chainID,
                name = metadata.name,
                description = metadata.description,
                icon = metadata.thumbnail?.url
            )
        }
        this.childs = childAccounts
        return childAccounts
    }

    suspend fun fetchVM(): COA {
        val address = evmManager.getEVMAddress(FlowAddress(account.address))
        val coa = createCOA(address, network = chainID)
        this.coa = coa
        return coa
    }

    // FlowSigner Implementation
    val address: String
        get() = account.address

    val keyIndex: Int
        get() = findKeyInAccount().firstOrNull()?.index?.toInt() ?: 0

    suspend fun sign(transaction: Transaction, bytes: ByteArray): ByteArray {
        val key = key ?: throw WalletError.EmptySignKey
        val signKey = findKeyInAccount().firstOrNull() ?: throw WalletError.EmptySignKey

        if (securityDelegate != null) {
            val result = securityDelegate.verify()
            if (!result) {
                throw WalletError.FailedPassSecurityCheck
            }
        }

        return key.sign(
            data = bytes,
            signAlgo = signKey.signingAlgorithm,
            hashAlgo = signKey.hashingAlgorithm
        )
    }
}