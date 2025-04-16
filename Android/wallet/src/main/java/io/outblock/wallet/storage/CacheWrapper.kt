package io.outblock.wallet.storage

import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Wrapper for cached data with timestamp and expiration
 * @param T The type of data being cached
 */
@Serializable
data class CacheWrapper<T>(
    /**
     * The actual cached data
     */
    val data: T,

    /**
     * When the data was cached
     */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Time interval in milliseconds after which the cache expires (null means never)
     */
    val expiresIn: Long? = null
) {
    /**
     * Whether the cached data has expired
     */
    val isExpired: Boolean
        get() = expiresIn?.let { 
            System.currentTimeMillis() - timestamp > it 
        } ?: false
} 