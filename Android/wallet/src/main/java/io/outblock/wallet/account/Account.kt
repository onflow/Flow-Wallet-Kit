package io.outblock.wallet.account

import io.outblock.wallet.account.vm.COA
import io.outblock.wallet.account.vm.COA.Companion.createCOA
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import io.outblock.wallet.storage.StandardStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.Transaction
import org.onflow.flow.models.SigningAlgorithm
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Represents a Flow blockchain account with signing capabilities
 */
class Account(
    val account: Account,
    val chainID: ChainId,
    val key: KeyProtocol?,
    private val securityDelegate: SecurityCheckDelegate? = null,
    private val storage: StorageProtocol = StandardStorage()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tokenBalances = MutableStateFlow<List<TokenBalance>>(emptyList())
    val tokenBalances: StateFlow<List<TokenBalance>> = _tokenBalances.asStateFlow()

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
    suspend fun loadLinkedAccounts(): Pair<COA?, List<ChildAccount>> = coroutineScope {
        _isLoading.value = true
        try {
            val vmFetch = async { fetchVM() }
            val childFetch = async { fetchChild() }
            Pair(vmFetch.await(), childFetch.await())
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchChild(): List<ChildAccount> {
        val childs = flow.getChildMetadata(address = account.address) // to-do: implement on flow-kmm
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

    fun fetchVM(): COA? {
        val address = flow.getEVMAddress(account.address) ?: return null 
        val coa = createCOA(address, network = chainID) ?: throw WalletError.InvalidEVMAddress
        this.coa = coa
        return coa
    }

    suspend fun fetchAccount() {
        try {
            val cache = AccountCache(childs, coa)
            val cached = cache.loadCache()
            if (cached != null) {
                childs = cached.childs
                coa = cached.coa
            }
        } catch (e: Exception) {
            println("Error loading cache: ${e.message}")
        }
        loadLinkedAccounts()
        AccountCache(childs, coa).cache()
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
            if (!result.value) {
                throw WalletError.FailedPassSecurityCheck
            }
        }

        return key.sign(
            data = bytes,
            signAlgo = signKey.signingAlgorithm,
            hashAlgo = signKey.hashingAlgorithm
        )
    }

    /**
     * Fetches all token balances for the account
     */
    suspend fun fetchTokenBalances() {
        _isLoading.value = true
        try {
            val balances = mutableListOf<TokenBalance>()
            
            // Fetch Flow token balances
            val flowBalances = fetchFlowTokenBalances()
            balances.addAll(flowBalances)

            // If account has COA, fetch EVM token balances
            coa?.let { coa ->
                val evmBalances = coa.fetchEVMTokenBalances()
                balances.addAll(evmBalances)
            }

            _tokenBalances.value = balances
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Fetches Flow token balances (FT and NFT)
     */
    private suspend fun fetchFlowTokenBalances(): List<TokenBalance> {
        val balances = mutableListOf<TokenBalance>()
        
        // Fetch Flow token balance
        val flowBalance = FlowApi.getFlowBalance(chainID, account.address) // to-do: flow-kmm or API 
        balances.add(
            TokenBalance(
                tokenType = TokenBalance.TokenType.FLOW_FT,
                balance = flowBalance,
                symbol = "FLOW",
                name = "Flow Token",
                decimals = 8
            )
        )

        // Fetch other Flow FT balances
        val ftBalances = FlowApi.getFungibleTokenBalances(chainID, account.address) // to-do: flow-kmm or API 
        balances.addAll(ftBalances)

        // Fetch NFT balances
        val nftBalances = FlowApi.getNFTBalances(chainID, account.address) // to-do: flow-kmm or API 
        balances.addAll(nftBalances)

        return balances
    }

    /**
     * Fetches token permissions for all child accounts
     * @return Map of child account addresses to their token permissions
     */
    suspend fun fetchChildTokenPermissions(): Map<String, TokenPermissions> {
        val permissions = mutableMapOf<String, TokenPermissions>()
        childs?.forEach { child ->
            try {
                val childPermissions = child.fetchTokenPermissions()
                permissions[child.address.hex] = childPermissions
            } catch (e: Exception) {
                println("Error fetching permissions for child account ${child.address}: ${e.message}")
            }
        }
        return permissions
    }
}