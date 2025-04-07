package io.outblock.wallet.storage

/**
 * In-memory storage implementation for temporary sensitive data
 * Data is lost when the app is closed or the object is garbage collected
 */
class InMemoryStorage : StorageProtocol {
    private val storage = mutableMapOf<String, ByteArray>()
    private val keys = mutableSetOf<String>()

    override val securityLevel: SecurityLevel = SecurityLevel.IN_MEMORY

    override val allKeys: List<String>
        get() = keys.toList()

    override fun findKey(keyword: String): List<String> {
        return keys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? {
        return storage[key]
    }

    override fun set(key: String, value: ByteArray) {
        storage[key] = value
        keys.add(key)
    }

    override fun remove(key: String) {
        storage.remove(key)
        keys.remove(key)
    }

    override fun removeAll() {
        storage.clear()
        keys.clear()
    }

    override fun exists(key: String): Boolean {
        return keys.contains(key)
    }
} 