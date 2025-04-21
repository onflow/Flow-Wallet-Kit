package com.flow.wallet.storage

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

/**
 * Protocol defining caching behavior for wallet components
 * @param T The type of data being cached (must be serializable)
 */
interface Cacheable<T> where T : @Serializable Any {
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
        val data = cachedData ?: return
        val wrapper = CacheWrapper(
            data = data,
            expiresIn = expiresIn ?: cacheExpiration
        )
        val json = kotlinx.serialization.json.Json.encodeToString(wrapper)
        storage.set(cacheId, json.toByteArray())
    }

    /**
     * Load state from cache
     * @param ignoreExpiration Whether to ignore expiration time
     * @return Cached data if available and not expired
     */
    fun loadCache(ignoreExpiration: Boolean = false): T? {
        val data = storage.get(cacheId) ?: return null
        val json = String(data)
        val wrapper = kotlinx.serialization.json.Json.decodeFromString<CacheWrapper<T>>(json)
        
        if (!ignoreExpiration && wrapper.isExpired) {
            deleteCache()
            return null
        }
        
        return wrapper.data
    }

    /**
     * Delete cached state
     */
    fun deleteCache() {
        storage.remove(cacheId)
    }
}

/**
 * Extension functions for easier cache expiration configuration
 */
fun Cacheable<*>.expiresInDays(days: Long) = TimeUnit.DAYS.toMillis(days)
fun Cacheable<*>.expiresInHours(hours: Long) = TimeUnit.HOURS.toMillis(hours)
fun Cacheable<*>.expiresInMinutes(minutes: Long) = TimeUnit.MINUTES.toMillis(minutes)
fun Cacheable<*>.expiresInSeconds(seconds: Long) = TimeUnit.SECONDS.toMillis(seconds) 