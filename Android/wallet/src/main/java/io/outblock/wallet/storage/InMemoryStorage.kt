package io.outblock.wallet.storage

import io.outblock.wallet.errors.WalletError

/**
 * In-memory storage implementation for temporary sensitive data
 * Data is lost when the app is closed or the object is garbage collected
 */
class InMemoryStorage : StorageProtocol {
    private val storage = mutableMapOf<String, ByteArray>()
    private val keys = mutableSetOf<String>()

    override val securityLevel: SecurityLevel = SecurityLevel.IN_MEMORY

    override val allKeys: List<String>
        get() = try {
            keys.toList()
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }

    override fun findKey(keyword: String): List<String> {
        return try {
            keys.filter { it.contains(keyword, ignoreCase = true) }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun get(key: String): ByteArray {
        return try {
            storage[key] ?: throw WalletError.EmptyKeychain
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun set(key: String, value: ByteArray) {
        try {
            storage[key] = value
            keys.add(key)
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun remove(key: String) {
        try {
            storage.remove(key)
            keys.remove(key)
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun removeAll() {
        try {
            storage.clear()
            keys.clear()
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun exists(key: String): Boolean {
        return try {
            keys.contains(key)
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }
} 