package io.outblock.wallet.account

import io.outblock.wallet.account.vm.COA
import io.outblock.wallet.account.vm.COA.Companion.createCOA
import io.outblock.wallet.keys.KeyProtocol
import io.outblock.wallet.errors.WalletError
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.Transaction
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.Signer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Represents a Flow blockchain account with signing capabilities
 */
class Account(
    val account: Account,
    val chainID: ChainId,
    val key: KeyProtocol?
) {

    companion object {
        // Dummy flow implementation
        private val flow = object {
            fun getChildMetadata(address: String): Map<String, ChildMetadata> {
                // Return dummy child metadata
                return mapOf(
                    "0x1234" to ChildMetadata(
                        name = "Test Child Account",
                        description = "A test child account",
                        thumbnail = Thumbnail("https://example.com/icon.png")
                    ),
                    "0x5678" to ChildMetadata(
                        name = "Another Child Account",
                        description = "Another test child account",
                        thumbnail = null
                    )
                )
            }

            fun getEVMAddress(address: String): String? {
                // Return a dummy EVM address
                return "0x742d35Cc6634C0532925a3b844Bc454e4438f44e"
            }
        }

        // Data classes for child metadata
        data class ChildMetadata(
            val name: String,
            val description: String,
            val thumbnail: Thumbnail?
        )

        data class Thumbnail(val url: String)
    }

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

        // Get public keys for both supported signature algorithms
        val p256PublicKey = keyInstance.publicKey(SigningAlgorithm.ECDSA_P256)
        val secpPublicKey = keyInstance.publicKey(SigningAlgorithm.ECDSA_secp256k1)

        // Filter account keys where:
        // - The key is not revoked
        // - The key's weight is >= 1000
        // - The stored public key matches either of the provider's public keys
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

    /**
     * Load all linked accounts (VM and child accounts) concurrently
     * @return Pair containing COA (if exists) and list of child accounts
     */
    suspend fun loadLinkedAccounts(): Pair<COA?, List<ChildAccount>> = coroutineScope {
        // Execute both fetch operations concurrently
        val vmFetch = async { fetchVM() }
        val childFetch = async { fetchChild() }
        
        // Wait for both operations to complete
        Pair(vmFetch.await(), childFetch.await())
    }

    suspend fun fetchChild(): List<ChildAccount> {
        val childs = flow.getChildMetadata(address = account.address) // not yet implemented
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

    /**
     * Fetch virtual machine accounts
     * @return COA instance if found, null if no COA exists
     * @throws WalletError.InvalidEVMAddress if the EVM address is invalid
     */
    suspend fun fetchVM(): COA? {
        val address = flow.getEVMAddress(account.address) ?: return null

        val coa = createCOA(address, network = chainID) ?: throw WalletError.InvalidEVMAddress
        
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

        return key.sign(
            data = bytes,
            signAlgo = signKey.signingAlgorithm,
            hashAlgo = signKey.hashingAlgorithm
        )
    }
}