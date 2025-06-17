package com.flow.wallet

import android.util.Log
import com.flow.wallet.errors.WalletError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.net.URL
import org.onflow.flow.models.Account as FlowAccount

/**
 * Network layer for Flow blockchain interactions
 * Handles communication with Flow blockchain and key indexer services
 */
object Network {
    const val TAG = "Network"
    // Define custom JSON configuration
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(SigningAlgorithm::class, SigningAlgorithmSerializer)
            contextual(HashingAlgorithm::class, HashingAlgorithmSerializer)
        }
    }

    private var _ktorClient: HttpClient? = null
    private val ktorClient: HttpClient by lazy {
        createHttpClient()
    }
    
    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            
            // Add timeouts and connection limits for device compatibility
            install(HttpTimeout) {
                requestTimeoutMillis = 15000L // 15 seconds
                connectTimeoutMillis = 10000L // 10 seconds
                socketTimeoutMillis = 15000L  // 15 seconds
            }
            
            // Add logging for debugging (only in debug builds)
            if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            android.util.Log.d(TAG, message)
                        }
                    }
                    level = LogLevel.INFO // Reduce logging verbosity
                }
            }
            
            // Configure engine for better device compatibility
            engine {
                // Limit concurrent connections to prevent overwhelming device
                maxConnectionsCount = 50
                threadsCount = 4 // Reduced thread count for lower-end devices
            }
        }
    }

    /**
     * Clean up network resources
     * Call this when the wallet is no longer needed
     */
    fun cleanup() {
        _ktorClient?.close()
        _ktorClient = null
    }

    /**
     * Custom serializer for SigningAlgorithm that can handle both int and string values
     */
    object SigningAlgorithmSerializer : KSerializer<SigningAlgorithm> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SigningAlgorithm", PrimitiveKind.STRING)
        
        override fun serialize(encoder: Encoder, value: SigningAlgorithm) {
            encoder.encodeString(value.value)
        }
        
        override fun deserialize(decoder: Decoder): SigningAlgorithm {
            return try {
                val stringValue = decoder.decodeString()
                SigningAlgorithm.decode(stringValue) ?: SigningAlgorithm.ECDSA_P256
            } catch (e: Exception) {
                try {
                    val intValue = decoder.decodeInt()
                    when (intValue) {
                        1 -> SigningAlgorithm.ECDSA_P256
                        2 -> SigningAlgorithm.ECDSA_secp256k1
                        else -> SigningAlgorithm.ECDSA_P256
                    }
                } catch (e: Exception) {
                    SigningAlgorithm.ECDSA_P256
                }
            }
        }
    }
    
    /**
     * Custom serializer for HashingAlgorithm that can handle both int and string values
     */
    object HashingAlgorithmSerializer : KSerializer<HashingAlgorithm> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HashingAlgorithm", PrimitiveKind.STRING)
        
        override fun serialize(encoder: Encoder, value: HashingAlgorithm) {
            encoder.encodeString(value.value)
        }
        
        override fun deserialize(decoder: Decoder): HashingAlgorithm {
            return try {
                val stringValue = decoder.decodeString()
                HashingAlgorithm.decode(stringValue) ?: HashingAlgorithm.SHA2_256
            } catch (e: Exception) {
                try {
                    val intValue = decoder.decodeInt()
                    HashingAlgorithm.fromCadenceIndex(intValue)
                } catch (e: Exception) {
                    HashingAlgorithm.SHA2_256
                }
            }
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
            @SerialName("address")
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
            @Serializable(with = SigningAlgorithmSerializer::class)
            val signing: SigningAlgorithm,
            @SerialName("hashing")
            @Serializable(with = HashingAlgorithmSerializer::class)
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
                                expandable = AccountExpandable(),
                                contracts = emptyMap(),
                                links = null
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
        val url = chainId.keyIndexerUrl(publicKey)
        val response = ktorClient.get(url) {
            headers {
                append("Accept", "application/json")
            }
        }

        if (!response.status.isSuccess()) {
            throw WalletError.KeyIndexerRequestFailed
        }
        
        val responseText = response.bodyAsText()

        try {
            return parseKeyIndexerResponse(responseText, publicKey)
        } catch (e: Exception) {
            Log.d(TAG,"Parsing failed: ${e.message}")
            e.printStackTrace()
            
            // Try automatic deserialization as a fallback
            try {
                return json.decodeFromString<KeyIndexerResponse>(responseText)
            } catch (e: Exception) {
                Log.d(TAG,"Automatic deserialization also failed: ${e.message}")
                e.printStackTrace()
                // Return empty response in case of failures
                return KeyIndexerResponse(publicKey, emptyList())
            }
        }
    }
    
    /**
     * Manual parsing of the key indexer response to avoid serialization issues
     */
    private fun parseKeyIndexerResponse(jsonString: String, publicKey: String): KeyIndexerResponse {
        Log.d(TAG,"Attempting manual parsing of response")
        val jsonElement = json.parseToJsonElement(jsonString)
        val jsonObject = jsonElement.jsonObject
        
        val accounts = mutableListOf<KeyIndexerResponse.Account>()
        
        // If the response contains an "accounts" field, parse it
        if (jsonObject.containsKey("accounts")) {
            val accountsArray = jsonObject["accounts"]?.jsonArray ?: JsonArray(emptyList())
            
            for (accountElement in accountsArray) {
                try {
                    val accountObj = accountElement.jsonObject
                    
                    // Extract fields with fallbacks
                    val address = accountObj["address"]?.jsonPrimitive?.content ?: ""
                    val keyId = accountObj["keyId"]?.jsonPrimitive?.intOrNull ?: 0
                    val weight = accountObj["weight"]?.jsonPrimitive?.intOrNull ?: 1000
                    val sigAlgo = accountObj["sigAlgo"]?.jsonPrimitive?.intOrNull ?: 1
                    val hashAlgo = accountObj["hashAlgo"]?.jsonPrimitive?.intOrNull ?: 1
                    
                    // Handle signing algorithm
                    val signing = when (sigAlgo) {
                        1 -> SigningAlgorithm.ECDSA_P256
                        2 -> SigningAlgorithm.ECDSA_secp256k1
                        else -> SigningAlgorithm.ECDSA_P256
                    }
                    
                    // Handle hashing algorithm
                    val hashing = HashingAlgorithm.fromCadenceIndex(hashAlgo)
                    
                    // Check if revoked
                    val isRevoked = accountObj["isRevoked"]?.jsonPrimitive?.booleanOrNull ?: false
                    
                    // Create account object
                    val account = KeyIndexerResponse.Account(
                        address = address,
                        keyId = keyId,
                        weight = weight,
                        sigAlgo = sigAlgo,
                        hashAlgo = hashAlgo,
                        signing = signing,
                        hashing = hashing,
                        isRevoked = isRevoked
                    )
                    
                    accounts.add(account)
                } catch (e: Exception) {
                    Log.d(TAG,"Error parsing account: ${e.message}")
                }
            }
        } else {
            Log.d(TAG,"No 'accounts' field found in the response")
        }

        Log.d(TAG, "Manual parsing complete, found ${accounts.size} accounts")
        return KeyIndexerResponse(publicKey, accounts)
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
        Log.d(TAG,"Key indexer response: " + model.accountResponse)
        return model.accountResponse
    }

    private fun ChainId.keyIndexerUrl(publicKey: String): URL {
        val baseUrl = when (this) {
            ChainId.Mainnet -> "https://production.key-indexer.flow.com"
            ChainId.Testnet -> "https://staging.key-indexer.flow.com"
            else -> {throw Exception("Chain not supported")}
        }
        return URL("$baseUrl/key/$publicKey")
    }
} 