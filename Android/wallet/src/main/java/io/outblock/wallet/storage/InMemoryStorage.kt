package io.outblock.wallet.storage

/**
 * In-memory storage implementation
 * Stores data in memory, cleared when app is terminated
 */
class InMemoryStorage : StorageProtocol {
    private val storage = mutableMapOf<String, ByteArray>()

    override val allKeys: List<String>
        get() = storage.keys.toList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? = storage[key]

    override fun set(key: String, data: ByteArray) {
        storage[key] = data
    }

    override fun remove(key: String) {
        storage.remove(key)
    }

    override fun removeAll() {
        storage.clear()
    }
} 