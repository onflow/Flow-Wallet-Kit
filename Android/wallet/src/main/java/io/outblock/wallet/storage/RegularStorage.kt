package io.outblock.wallet.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Regular storage implementation using SharedPreferences
 * Provides unencrypted storage for non-sensitive data like cache
 */
class RegularStorage(
    private val context: Context,
    private val fileName: String = "regular_storage"
) : StorageProtocol {

    private val prefs: SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE) as SharedPreferences

    override val allKeys: List<String>
        get() = prefs.all.keys.toList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? {
        return try {
            prefs.getString(key, null)?.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun set(key: String, value: ByteArray) {
        prefs.edit()
            .putString(key, value.toString(Charsets.ISO_8859_1))
            .apply()
    }

    override fun remove(key: String) {
        prefs.edit()
            .remove(key)
            .apply()
    }

    override fun removeAll() {
        prefs.edit()
            .clear()
            .apply()
    }
} 