package io.outblock.wallet.storage

import android.content.Context
import android.content.SharedPreferences
import io.outblock.wallet.errors.WalletError
import java.io.File
import java.io.FileNotFoundException
import java.util.Date

/**
 * File system-based storage implementation for caching and non-sensitive data
 * Provides flexible storage options:
 * - File-based storage in cache directory
 * - SharedPreferences storage for simple key-value pairs
 * - Configurable security level
 */
class FileSystemStorage(
    private val context: Context,
    private val directoryName: String = "file_storage",
    private val useSharedPreferences: Boolean = false,
    private val prefsName: String = "standard_storage"
) : StorageProtocol {

    private val storageDir: File = File(context.cacheDir, directoryName).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val prefs: SharedPreferences? = if (useSharedPreferences) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    } else null

    override val securityLevel: SecurityLevel = SecurityLevel.STANDARD

    override val allKeys: List<String>
        get() = try {
            if (useSharedPreferences) {
                prefs?.all?.keys?.toList() ?: emptyList()
            } else {
                storageDir.listFiles()?.map { it.name } ?: emptyList()
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }

    override fun findKey(keyword: String): List<String> {
        return try {
            allKeys.filter { it.contains(keyword, ignoreCase = true) }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun get(key: String): ByteArray {
        return try {
            if (useSharedPreferences) {
                prefs?.getString(key, null)?.toByteArray() 
                    ?: throw WalletError.EmptyKeychain
            } else {
                val file = File(storageDir, key)
                if (!file.exists()) {
                    throw WalletError.EmptyKeychain
                }
                file.readBytes()
            }
        } catch (e: FileNotFoundException) {
            throw WalletError.EmptyKeychain
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun set(key: String, value: ByteArray) {
        try {
            if (useSharedPreferences) {
                prefs?.edit()
                    ?.putString(key, value.toString(Charsets.ISO_8859_1))
                    ?.apply()
            } else {
                File(storageDir, key).writeBytes(value)
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun remove(key: String) {
        try {
            if (useSharedPreferences) {
                prefs?.edit()?.remove(key)?.apply()
            } else {
                File(storageDir, key).delete()
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun removeAll() {
        try {
            if (useSharedPreferences) {
                prefs?.edit()?.clear()?.apply()
            } else {
                storageDir.deleteRecursively()
                storageDir.mkdirs()
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun exists(key: String): Boolean {
        return try {
            if (useSharedPreferences) {
                prefs?.contains(key) ?: false
            } else {
                File(storageDir, key).exists()
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    /**
     * Get the total size of all cached files in bytes
     * Only applicable when using file-based storage
     */
    fun getCacheSize(): Long {
        if (useSharedPreferences) return 0L
        return try {
            storageDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    /**
     * Get the last modification date of a file
     * Only applicable when using file-based storage
     */
    fun getModificationDate(key: String): Date? {
        if (useSharedPreferences) return null
        return try {
            val file = File(storageDir, key)
            if (file.exists()) Date(file.lastModified()) else null
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    /**
     * Get the number of items stored
     */
    fun getItemCount(): Int {
        return try {
            if (useSharedPreferences) {
                prefs?.all?.size ?: 0
            } else {
                storageDir.listFiles()?.size ?: 0
            }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }
} 