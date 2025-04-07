package io.outblock.wallet.keys

import io.outblock.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

/**
 * Protocol defining the interface for cryptographic key management
 * Provides a unified interface for different types of keys (Secure Enclave, Seed Phrase, Private Key, etc.)
 */
interface KeyProtocol {
    /**
     * The type of key implementation
     */
    val keyType: KeyType

    /**
     * Storage implementation for persisting key data
     */
    var storage: StorageProtocol

    /**
     * Get the public key for a signature algorithm
     * @param signAlgo Signature algorithm
     * @return Public key data
     */
    fun publicKey(signAlgo: SigningAlgorithm): ByteArray?

    /**
     * Get the private key for a signature algorithm
     * @param signAlgo Signature algorithm
     * @return Private key data
     */
    fun privateKey(signAlgo: SigningAlgorithm): ByteArray?

    /**
     * Sign data using specified algorithms
     * @param data Data to sign
     * @param signAlgo Signature algorithm
     * @param hashAlgo Hash algorithm
     * @return Signature data
     */
    suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray

    /**
     * Verify a signature
     * @param signature Signature to verify
     * @param message Original message that was signed
     * @param signAlgo Signature algorithm used
     * @return Whether the signature is valid
     */
    fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: HashingAlgorithm): Boolean

    /**
     * Store the key
     * @param id Unique identifier for the key
     * @param password Password for encrypting the key
     */
    suspend fun store(id: String, password: String)

    /**
     * Remove a stored key
     * @param id Unique identifier of the key to remove
     */
    suspend fun remove(id: String)

    /**
     * Get all stored key identifiers
     * @return Array of key identifiers
     */
    fun allKeys(): List<String>
}

/**
 * Types of cryptographic keys supported
 */
enum class KeyType {
    SECURE_ENCLAVE,
    SEED_PHRASE,
    PRIVATE_KEY,
    KEY_STORE
} 