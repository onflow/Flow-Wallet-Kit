package io.outblock.wallet.storage

import com.google.gson.Gson
import java.util.Date

/**
 * Interface defining caching behavior for wallet components
 * @param T The type of data being cached (must be serializable)
 */
interface Cacheable<T> {
    /**
     * Storage mechanism used for caching
     */
    val storage: StorageProtocol

    /**
     * Unique identifier for the cached data
     */
    val cacheId: String

    /**
     * The data to be cached
     */
    val cachedData: T?

    /**
     * Time interval after which the cache expires (null means never)
     */
    val cacheExpiration: Long?
        get() = null

    /**
     * Cache the current state
     * @param expiresIn Optional custom expiration time in milliseconds
     */
    fun cache(expiresIn: Long? = null) {
        cachedData?.let { data ->
            val wrapper = CacheWrapper(
                data = data,
                timestamp = Date(),
                expiresIn = expiresIn ?: cacheExpiration
            )
            val json = Gson().toJson(wrapper)
            storage.set(cacheId, json.toByteArray())
        }
    }

    /**
     * Load state from cache
     * @param ignoreExpiration Whether to ignore expiration time
     * @return Cached data if available and not expired
     */
    fun loadCache(ignoreExpiration: Boolean = false): T? {
        val data = storage.get(cacheId) ?: return null
        val json = String(data)
        val wrapper = Gson().fromJson(json, CacheWrapper::class.java)
        
        if (!ignoreExpiration && wrapper.isExpired) {
            deleteCache()
            return null
        }
        
        return wrapper.data as? T
    }

    /**
     * Delete cached state
     */
    fun deleteCache() {
        storage.remove(cacheId)
    }
} 