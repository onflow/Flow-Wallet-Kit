package com.flow.wallet.storage

import android.content.SharedPreferences
import com.flow.wallet.errors.WalletError
import java.io.File
import java.util.Date

/**
 * File system-based storage implementation for caching and non-sensitive data
 * Provides flexible storage options:
 * - File-based storage in cache directory
 * - SharedPreferences storage for simple key-value pairs
 * - Configurable security level
 */
class FileSystemStorage(private val baseDir: File) : StorageProtocol {
    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    private val storageDir: File = baseDir

    private val prefs: SharedPreferences? = null

    override val allKeys: List<String>
        get() = storageDir.listFiles()?.map { it.name } ?: emptyList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? {
        val file = File(storageDir, key)
        return if (file.exists()) {
            file.readBytes()
        } else {
            null
        }
    }

    override fun set(key: String, data: ByteArray) {
        val file = File(storageDir, key)
        file.writeBytes(data)
    }

    override fun remove(key: String) {
        val file = File(storageDir, key)
        if (file.exists()) {
            file.delete()
        }
    }

    override fun removeAll() {
        baseDir.listFiles()?.forEach { it.delete() }
    }

    override val securityLevel: SecurityLevel
        get() = SecurityLevel.STANDARD

    /**
     * Get the total size of all cached files in bytes
     * Only applicable when using file-based storage
     */
    fun getCacheSize(): Long {
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
            storageDir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }
} 