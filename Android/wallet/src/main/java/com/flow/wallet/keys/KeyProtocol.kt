package com.flow.wallet.keys

import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

/**
 * Types of cryptographic keys supported
 */
enum class KeyType {
    /**
     * Key stored in device's secure element (e.g., Android Keystore)
     */
    SECURE_ELEMENT,

    /**
     * Key derived from a BIP39 seed phrase
     */
    SEED_PHRASE,

    /**
     * Raw private key
     */
    PRIVATE_KEY,

    /**
     * Key stored in encrypted JSON keystore format
     */
    KEY_STORE
}

/**
 * Protocol defining the interface for cryptographic key management
 * Provides a unified interface for different types of keys (Secure Enclave, Seed Phrase, Private Key, etc.)
 */
interface KeyProtocol {
    /**
     * The concrete key type
     */
    val key: Any

    /**
     * The type used for secret key material
     */
    val secret: ByteArray

    /**
     * The type used for advanced key creation options
     */
    val advance: Any

    /**
     * The type of key implementation
     */
    val keyType: KeyType

    /**
     * Storage implementation for persisting key data
     */
    var storage: StorageProtocol

    // MARK: - Key Creation and Recovery

    /**
     * Create a new key with advanced options
     * @param advance Advanced creation options
     * @param storage Storage implementation
     * @return New key instance
     */
    suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol

    /**
     * Create a new key with default options
     * @param storage Storage implementation
     * @return New key instance
     */
    suspend fun create(storage: StorageProtocol): KeyProtocol

    /**
     * Create and store a new key
     * @param id Unique identifier for the key
     * @param password Password for encrypting the key
     * @param storage Storage implementation
     * @return New key instance
     */
    suspend fun createAndStore(id: String, password: String, storage: StorageProtocol): KeyProtocol

    /**
     * Retrieve a stored key
     * @param id Unique identifier for the key
     * @param password Password for decrypting the key
     * @param storage Storage implementation
     * @return Retrieved key instance
     */
    suspend fun get(id: String, password: String, storage: StorageProtocol): KeyProtocol

    /**
     * Restore a key from secret material
     * @param secret Secret key material
     * @param storage Storage implementation
     * @return Restored key instance
     */
    suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol

    // MARK: - Key Operations

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
    fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm): Boolean

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

    /**
     * Check if the key is hardware-backed
     * @return Whether the key is stored in a secure element
     */
    val isHardwareBacked: Boolean

    /**
     * Get the key's unique identifier
     * @return Unique identifier for the key
     */
    val id: String
}


