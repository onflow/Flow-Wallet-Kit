package io.outblock.wallet.storage

/**
 * Protocol defining the interface for secure data storage
 * Provides a unified interface for storing and retrieving sensitive data like keys,
 * allowing different storage backends to be used interchangeably.
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
     */
    fun findKey(keyword: String): List<String>

    /**
     * Retrieve data for a key
     * @param key Key to lookup
     * @return Stored data, or null if not found
     */
    fun get(key: String): ByteArray?

    /**
     * Store data for a key
     * @param key Key to store under
     * @param value Data to store
     */
    fun set(key: String, value: ByteArray)

    /**
     * Remove data for a key
     * @param key Key to remove
     */
    fun remove(key: String)

    /**
     * Remove all stored data
     */
    fun removeAll()

    /**
     * Check if a key exists in storage
     * @param key Key to check
     * @return Whether the key exists
     */
    fun exists(key: String): Boolean

    /**
     * Get the security level of this storage backend
     * @return SecurityLevel indicating the security guarantees
     */
    val securityLevel: SecurityLevel
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
     * High security level - uses software encryption
     * (e.g., EncryptedSharedPreferences)
     */
    ENCRYPTED,

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