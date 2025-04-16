package io.outblock.wallet

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.outblock.wallet.errors.WalletError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import java.net.URL
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

/**
 * Network layer for Flow blockchain interactions
 * Handles communication with Flow blockchain and key indexer services
 */
object Network {
    private val ktorClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    /**
     * Response structure from the Flow key indexer service
     */
    @Serializable
    data class KeyIndexerResponse(
        @SerialName("publicKey")
        val publicKey: String,
        @SerialName("accounts")
        val accounts: List<Account>
    ) {
        /**
         * Represents an account entry in the key indexer response
         */
        @Serializable
        data class Account(
            @SerialName("address") // is this byte or hex
            val address: String,
            @SerialName("keyId")
            val keyId: Int,
            @SerialName("weight")
            val weight: Int,
            @SerialName("sigAlgo")
            val sigAlgo: Int,
            @SerialName("hashAlgo")
            val hashAlgo: Int,
            @SerialName("signing")
            val signing: SigningAlgorithm,
            @SerialName("hashing")
            val hashing: HashingAlgorithm,
            @SerialName("isRevoked")
            val isRevoked: Boolean
        )

        /**
         * Converts the key indexer response to Flow account objects
         * Aggregates keys by account address
         */
        val accountResponse: List<FlowAccount>
            get() {
                val response = mutableListOf<FlowAccount>()
                for (account in accounts) {
                    val existingAccount = response.find { it.address == account.address }
                    if (existingAccount != null) {
                        // Add key to existing account
                        val index = response.indexOf(existingAccount)
                        val existingKeys = existingAccount.keys?.toMutableSet() ?: mutableSetOf()
                        existingKeys.add(
                            AccountPublicKey(
                                index = account.keyId.toString(),
                                publicKey = publicKey,
                                signingAlgorithm = account.signing,
                                hashingAlgorithm = account.hashing,
                                weight = account.weight.toString(),
                                revoked = account.isRevoked,
                                sequenceNumber = "0"
                            )
                        )
                        response[index] = existingAccount.copy(keys = existingKeys)
                    } else {
                        // Create new account with first key
                        response.add(
                            FlowAccount(
                                address = account.address,
                                keys = setOf(
                                    AccountPublicKey(
                                        index = account.keyId.toString(),
                                        publicKey = publicKey,
                                        signingAlgorithm = account.signing,
                                        hashingAlgorithm = account.hashing,
                                        weight = account.weight.toString(),
                                        revoked = account.isRevoked,
                                        sequenceNumber = "0"
                                    )
                                ),
                                balance = "0",
                                expandable = AccountExpandable(), // ??
                                contracts = emptyMap(), //?
                                links = null // ??
                            )
                        )
                    }
                }
                return response
            }
    }

    /**
     * Find account information using the key indexer service
     * @param publicKey The public key to search for
     * @param chainId The Flow network to search on
     * @return Key indexer response containing account information
     * @throws WalletError if the request fails
     */
    suspend fun findAccount(publicKey: String, chainId: ChainId): KeyIndexerResponse {
        val url = chainId.keyIndexerUrl(publicKey) ?: throw WalletError.IncorrectKeyIndexerURL
        val response = ktorClient.get(url) {
            headers {
                append("Accept", "application/json")
            }
        }

        if (!response.status.isSuccess()) {
            throw WalletError.KeyIndexerRequestFailed
        }

        return response.body()
    }

    /**
     * Find accounts associated with a public key
     * @param publicKey The public key to search for
     * @param chainId The Flow network to search on
     * @return Array of accounts associated with the key
     */
    suspend fun findAccountByKey(publicKey: String, chainId: ChainId): List<KeyIndexerResponse.Account> {
        val model = findAccount(publicKey, chainId)
        return model.accounts
    }

    /**
     * Find Flow accounts associated with a public key
     * @param publicKey The public key to search for
     * @param chainId The Flow network to search on
     * @return Array of Flow accounts associated with the key
     */
    suspend fun findFlowAccountByKey(publicKey: String, chainId: ChainId): List<FlowAccount> {
        val model = findAccount(publicKey, chainId)
        return model.accountResponse
    }

    private fun ChainId.keyIndexerUrl(publicKey: String): URL? {
        val baseUrl = when (this) {
            ChainId.Mainnet -> "https://mainnet.onflow.org"
            ChainId.Testnet -> "https://testnet.onflow.org"
            ChainId.Emulator -> "http://localhost:8080"
            else -> {throw Exception("Chain not supported")}
        }
        return URL("$baseUrl/v1/accounts?publicKey=$publicKey")
    }
} 