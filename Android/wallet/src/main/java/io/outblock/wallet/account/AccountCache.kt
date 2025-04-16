package io.outblock.wallet.account

import io.outblock.wallet.account.vm.COA
import io.outblock.wallet.storage.StorageProtocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Contextual

/**
 * Structure representing cacheable account data
 */
@Serializable
data class AccountCache(
    val childs: List<ChildAccount>?,
    @Contextual
    val coa: COA?
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Storage mechanism used for caching
     */
    val storage: StorageProtocol
        get() = storage

    /**
     * Unique identifier for caching account data
     */
    val cacheId: String
        get() = "Account-${chainID.name}-${account.address}"

    /**
     * Cache the current account data
     */
    suspend fun cache() {
        val json = json.encodeToString(this)
        storage.set(cacheId, json.toByteArray())
    }

    /**
     * Load cached account data
     */
    suspend fun loadCache(): AccountCache? {
        val cachedData = storage.get(cacheId) ?: return null
        return try {
            json.decodeFromString<AccountCache>(cachedData.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
} 