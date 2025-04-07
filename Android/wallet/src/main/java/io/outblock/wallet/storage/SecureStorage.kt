package io.outblock.wallet.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Secure storage implementation using EncryptedSharedPreferences
 * Provides encrypted storage for sensitive data like cryptographic keys
 */
class SecureStorage(
    private val context: Context,
    private val fileName: String = "secure_storage"
) : StorageProtocol {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: EncryptedSharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    } catch (e: GeneralSecurityException) {
        throw RuntimeException("Failed to create secure storage", e)
    } catch (e: IOException) {
        throw RuntimeException("Failed to create secure storage", e)
    }

    override val allKeys: List<String>
        get() = encryptedPrefs.all.keys.toList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? {
        return try {
            encryptedPrefs.getString(key, null)?.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun set(key: String, value: ByteArray) {
        encryptedPrefs.edit()
            .putString(key, value.toString(Charsets.ISO_8859_1))
            .apply()
    }

    override fun remove(key: String) {
        encryptedPrefs.edit()
            .remove(key)
            .apply()
    }

    override fun removeAll() {
        encryptedPrefs.edit()
            .clear()
            .apply()
    }
} 