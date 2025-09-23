package com.flow.wallet.account

import com.flow.wallet.account.vm.COA
import com.flow.wallet.storage.StorageProtocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Contextual

/**
 * Structure representing cacheable account data
 */
@Serializable
data class AccountCache(
    val childs: List<@Contextual ChildAccount>?,
    @Contextual val coa: COA?
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
        get() = "Account-${coa?.chainID?.description}-${coa?.address}"

    /**
     * Cache the current account data
     */
    fun cache() {
        val json = json.encodeToString(this)
        storage.set(cacheId, json.toByteArray())
    }

    /**
     * Load cached account data
     */
    fun loadCache(): AccountCache? {
        val cachedData = storage.get(cacheId) ?: return null
        return try {
            json.decodeFromString<AccountCache>(cachedData.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
} 
