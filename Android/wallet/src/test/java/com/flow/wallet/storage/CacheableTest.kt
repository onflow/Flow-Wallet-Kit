package com.flow.wallet.storage

import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertFailsWith
import java.util.concurrent.TimeUnit

class CacheableTest {
    @Serializable
    private class TestCacheable(
        override val storage: StorageProtocol,
        override val cacheId: String,
        override val cachedData: String?,
        override val cacheExpiration: Long?
    ) : Cacheable<String>

    @Test
    fun testCacheWrapper() {
        val data = "test data"
        val wrapper = CacheWrapper(data)
        
        assertNotNull(wrapper.timestamp)
        assertNull(wrapper.expiresIn)
        assertFalse(wrapper.isExpired)
    }

    @Test
    fun testCacheWrapperWithExpiration() {
        val data = "test data"
        val expiration = 1000L // 1 second
        val wrapper = CacheWrapper(data, expiresIn = expiration)
        
        assertNotNull(wrapper.timestamp)
        assertEquals(expiration, wrapper.expiresIn)
        assertFalse(wrapper.isExpired)
        
        // Wait for expiration
        Thread.sleep(expiration + 100)
        assertTrue(wrapper.isExpired)
    }

    @Test
    fun testCacheWrapperSerialization() {
        val data = "test data"
        val wrapper = CacheWrapper(data, expiresIn = 1000L)
        
        // Test serialization
        val json = Json.encodeToString(CacheWrapper.serializer(String.serializer()), wrapper)
        assertTrue(json.contains("test data"))
        assertTrue(json.contains("1000"))
        
        // Test deserialization
        val deserialized = Json.decodeFromString(CacheWrapper.serializer(String.serializer()), json)
        assertEquals(data, deserialized.data)
        assertEquals(1000L, deserialized.expiresIn)
    }

    @Test
    fun testCacheWrapperDeserializationError() {
        val invalidJson = """{"invalid": "json"}"""
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString(CacheWrapper.serializer(String.serializer()), invalidJson)
        }
    }

    @Test
    fun testCacheOperations() {
        val storage = InMemoryStorage()
        val cacheable = TestCacheable(storage, "test-cache", "test data", null)
        
        // Test caching
        cacheable.cache()
        val retrieved = cacheable.loadCache()
        assertNotNull(retrieved)
        assertEquals("test data", retrieved)
        
        // Test cache deletion
        cacheable.deleteCache()
        assertNull(cacheable.loadCache())
    }

    @Test
    fun testCacheExpiration() {
        val storage = InMemoryStorage()
        val expiration = 1000L // 1 second
        val cacheable = TestCacheable(storage, "test-cache", "test data", expiration)
        
        // Cache with expiration
        cacheable.cache()
        assertNotNull(cacheable.loadCache())
        
        // Wait for expiration
        Thread.sleep(expiration + 100)
        assertNull(cacheable.loadCache())
        
        // Test ignore expiration
        assertNotNull(cacheable.loadCache(ignoreExpiration = true))
    }

    @Test
    fun testCacheWithCustomExpiration() {
        val storage = InMemoryStorage()
        val cacheable = TestCacheable(storage, "test-cache", "test data", null)
        val customExpiration = 1000L // 1 second
        
        // Cache with custom expiration
        cacheable.cache(customExpiration)
        assertNotNull(cacheable.loadCache())
        
        // Wait for expiration
        Thread.sleep(customExpiration + 100)
        assertNull(cacheable.loadCache())
    }

    @Test
    fun testCacheWithNullData() {
        val storage = InMemoryStorage()
        val cacheable = TestCacheable(storage, "test-cache", null, null)
        
        // Should not throw when caching null data
        cacheable.cache()
        assertNull(cacheable.loadCache())
    }

    @Test
    fun testCacheErrorHandling() {
        val storage = object : StorageProtocol {
            override val securityLevel: SecurityLevel = SecurityLevel.STANDARD
            override val allKeys: List<String> = emptyList()
            override fun findKey(keyword: String): List<String> = emptyList()
            override fun get(key: String): ByteArray = throw WalletError(0, "Test error")
            override fun set(key: String, data: ByteArray) = throw WalletError(0, "Test error")
            override fun remove(key: String) = throw WalletError(0, "Test error")
            override fun removeAll() = throw WalletError(0, "Test error")
        }
        
        val cacheable = TestCacheable(storage, "test-cache", "test data", null)
        
        assertFailsWith<WalletError> {
            cacheable.cache()
        }
        
        assertFailsWith<WalletError> {
            cacheable.loadCache()
        }
        
        assertFailsWith<WalletError> {
            cacheable.deleteCache()
        }
    }

    @Test
    fun testTimeUnitExtensions() {
        val cacheable = TestCacheable(InMemoryStorage(), "test", "data", null)
        
        assertEquals(TimeUnit.DAYS.toMillis(1), cacheable.expiresInDays(1))
        assertEquals(TimeUnit.HOURS.toMillis(1), cacheable.expiresInHours(1))
        assertEquals(TimeUnit.MINUTES.toMillis(1), cacheable.expiresInMinutes(1))
        assertEquals(TimeUnit.SECONDS.toMillis(1), cacheable.expiresInSeconds(1))
    }

    @Test
    fun testConcurrentCacheAccess() {
        val storage = InMemoryStorage()
        val cacheable = TestCacheable(storage, "test-cache", "test data", null)
        
        // Simulate concurrent access
        val threads = List(10) {
            Thread {
                cacheable.cache()
                cacheable.loadCache()
                cacheable.deleteCache()
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify final state
        assertNull(cacheable.loadCache())
    }
} 