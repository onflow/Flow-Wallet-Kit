package io.outblock.wallet.account

import io.outblock.wallet.account.vm.COA
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.flow.AccountKey
import io.outblock.wallet.errors.WalletError
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.Transaction

/**
 * Represents a Flow blockchain account with signing capabilities
 */
class Account(
    val account: Account,
    val chainID: ChainId,
    val key: AccountPublicKey?
) {

    // Properties

    var childs: List<ChildAccount>? = null
    var coa: COA? = null

    val hasChild: Boolean
        get() = !(childs?.isEmpty() ?: true)

    val hasCOA: Boolean
        get() = coa != null

    val canSign: Boolean
        get() = key != null

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

        // Retrieve the public key from the provider (hex string)
        val providerPublicKey = keyInstance.getPublicKey()

        // Filter account keys where:
        // - The key is not revoked
        // - The key's weight is >= 1000
        // - The stored public key (hex) matches the provider's public key (ignoring case)
        val matchingKeys = account.keys.filter {
            !it.revoked &&
                    it.weight.toInt() >= 1000 &&
                    it.publicKey.equals(providerPublicKey, ignoreCase = true)
        }
        keys.addAll(matchingKeys)

        return keys
    }

    // Account Relationships

    suspend fun loadLinkedAccounts(): Pair<COA?, List<ChildAccount>> {
        val vmFetch = fetchVM()
        val childFetch = fetchChild()
        return Pair(vmFetch, childFetch)
    }

    suspend fun fetchChild(): List<ChildAccount> {
        val childs = flow.getChildMetadata(address = account.address)
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

    suspend fun fetchVM(): COA? {
        val address = flow.getEVMAddress(address = account.address) ?: return null
        return COA.create(address, network = chainID) ?: throw WalletError.InvalidEVMAddress
    }

    // FlowSigner Implementation

    override val address: String
        get() = account.address

    override val keyIndex: Int
        get() = findKeyInAccount().firstOrNull()?.index?.toInt() ?: 0

    override suspend fun sign(transaction: Transaction, bytes: ByteArray): ByteArray {
        val key = key ?: throw WalletError.EmptySignKey
        val signKey = findKeyInAccount().firstOrNull() ?: throw WalletError.EmptySignKey

        return key.sign(
            data = signableData,
            signAlgo = signKey.signAlgo,
            hashAlgo = signKey.hashAlgo
        )
    }
}