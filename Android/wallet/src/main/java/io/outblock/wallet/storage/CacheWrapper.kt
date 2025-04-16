package io.outblock.wallet.storage

import java.util.Date

/**
 * Wrapper for cached data with timestamp and expiration
 * @param T The type of data being cached (must be serializable)
 */
data class CacheWrapper<T>(
    val data: T,
    val timestamp: Date = Date(),
    val expiresIn: Long? = null
) {
    /**
     * Whether the cached data has expired
     */
    val isExpired: Boolean
        get() = expiresIn?.let { 
            Date().time - timestamp.time > it 
        } ?: false
} 