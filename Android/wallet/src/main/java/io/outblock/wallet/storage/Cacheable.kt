package io.outblock.wallet.storage

/**
 * Protocol defining the interface for cacheable objects
 * Provides a unified interface for objects that can be cached to storage
 */
interface Cacheable {
    /**
     * The type of data being cached
     */
    val cachedData: Any?

    /**
     * Unique identifier for caching the data
     */
    val cacheId: String

    /**
     * Storage mechanism used for caching
     */
    val storage: StorageProtocol

    /**
     * Save the current state to cache
     */
    fun cache() {
        cachedData?.let { data ->
            storage.set(cacheId, data.toString().toByteArray())
        }
    }

    /**
     * Load data from cache
     * @return The cached data, or null if not found
     */
    fun loadCache(): Any? {
        return storage.get(cacheId)?.let { bytes ->
            String(bytes)
        }
    }
} 