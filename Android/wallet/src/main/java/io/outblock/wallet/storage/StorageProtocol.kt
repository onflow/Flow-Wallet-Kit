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
} 