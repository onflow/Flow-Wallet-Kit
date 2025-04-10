package io.outblock.wallet.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Standard storage implementation using SharedPreferences
 * Provides medium-security storage for non-sensitive data
 * Uses Android's standard secure storage with MODE_PRIVATE
 */
class StandardStorage(
    private val context: Context,
    private val fileName: String = "standard_storage"
) : StorageProtocol {

    private val prefs: SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override val securityLevel: SecurityLevel = SecurityLevel.STANDARD

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

    override fun exists(key: String): Boolean {
        return prefs.contains(key)
    }
} 