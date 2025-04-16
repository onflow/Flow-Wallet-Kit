package io.outblock.wallet.storage

import io.outblock.wallet.errors.WalletError

/**
 * Protocol defining storage behavior for wallet data
 */
interface StorageProtocol {
    /**
     * Get all keys currently stored in the storage
     */
    val allKeys: List<String>

    /**
     * Find keys matching a keyword
     * @param keyword Search term to match against keys
     * @return List of matching keys
     * @throws WalletError if operation fails
     */
    fun findKey(keyword: String): List<String>

    /**
     * Get data from storage
     * @param key The key to retrieve data for
     * @return The stored data, or null if not found
     * @throws WalletError if operation fails
     */
    fun get(key: String): ByteArray?

    /**
     * Store data in storage
     * @param key The key to store data under
     * @param data The data to store
     * @throws WalletError if operation fails
     */
    fun set(key: String, data: ByteArray)

    /**
     * Remove data from storage
     * @param key The key to remove data for
     * @throws WalletError if operation fails
     */
    fun remove(key: String)

    /**
     * Remove all data from storage
     * @throws WalletError if operation fails
     */
    fun removeAll()
}

/**
 * Enum representing different security levels for storage backends
 */
enum class SecurityLevel {
    /**
     * Highest security level - uses hardware-backed encryption
     * (e.g., Android Keystore, Secure Enclave)
     */
    HARDWARE_BACKED,

    /**
     * Medium security level - uses standard secure storage
     * (e.g., SharedPreferences with MODE_PRIVATE)
     */
    STANDARD,

    /**
     * Lowest security level - uses in-memory storage
     * Data is lost when app is closed
     */
    IN_MEMORY
} 