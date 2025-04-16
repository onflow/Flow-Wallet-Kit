package io.outblock.wallet.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreManagerTest {
    private lateinit var keyStoreManager: AndroidKeyStoreManager
    private val testAlias = "test_key_${System.currentTimeMillis()}"

    @Before
    fun setup() {
        keyStoreManager = AndroidKeyStoreManager()
    }

    @After
    fun cleanup() {
        try {
            keyStoreManager.removeKey(testAlias)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun testGenerateKeyPair() {
        val keyPair = keyStoreManager.generateKeyPair(testAlias)
        
        assertNotNull(keyPair)
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)
        assertTrue(keyStoreManager.containsAlias(testAlias))
    }

    @Test
    fun testGetKeyPair() {
        // Generate a key pair first
        keyStoreManager.generateKeyPair(testAlias)
        
        // Retrieve the key pair
        val retrievedKeyPair = keyStoreManager.getKeyPair(testAlias)
        
        assertNotNull(retrievedKeyPair)
        assertNotNull(retrievedKeyPair.private)
        assertNotNull(retrievedKeyPair.public)
    }

    @Test
    fun testRemoveKey() {
        // Generate a key pair first
        keyStoreManager.generateKeyPair(testAlias)
        assertTrue(keyStoreManager.containsAlias(testAlias))
        
        // Remove the key
        keyStoreManager.removeKey(testAlias)
        
        // Verify key is removed
        assertFalse(keyStoreManager.containsAlias(testAlias))
    }

    @Test
    fun testGetAllAliases() {
        // Generate multiple keys
        val aliases = listOf(
            "${testAlias}_1",
            "${testAlias}_2",
            "${testAlias}_3"
        )
        
        aliases.forEach { keyStoreManager.generateKeyPair(it) }
        
        // Get all aliases
        val allAliases = keyStoreManager.getAllAliases()
        
        // Verify all test aliases are present
        aliases.forEach { assertTrue(allAliases.contains(it)) }
        
        // Clean up
        aliases.forEach { keyStoreManager.removeKey(it) }
    }

    @Test
    fun testContainsAlias() {
        assertFalse(keyStoreManager.containsAlias(testAlias))
        
        keyStoreManager.generateKeyPair(testAlias)
        assertTrue(keyStoreManager.containsAlias(testAlias))
        
        keyStoreManager.removeKey(testAlias)
        assertFalse(keyStoreManager.containsAlias(testAlias))
    }
} 