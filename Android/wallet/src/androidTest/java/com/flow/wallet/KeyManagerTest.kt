package com.flow.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

@RunWith(AndroidJUnit4::class)
class KeyManagerTest {
    private val testPrefix = "test_key_manager"
    private val nonExistentPrefix = "nonexistent_prefix"

    @Before
    fun setup() {
        // Clean up any existing test keys
        KeyManager.deleteEntry(testPrefix)
    }

    @After
    fun cleanup() {
        // Clean up after tests
        KeyManager.deleteEntry(testPrefix)
    }

    @Test
    fun testGenerateKeyWithPrefix() {
        val keyPair = KeyManager.generateKeyWithPrefix(testPrefix)
        assertNotNull("Generated key pair should not be null", keyPair)
        assertTrue("Public key should be an EC key", keyPair.public is ECPublicKey)
        assertEquals("Current prefix should be updated", testPrefix, KeyManager.getCurrentPrefix())
        assertTrue("Key should exist in keystore", KeyManager.containsAlias(testPrefix))
    }

    @Test
    fun testGetCurrentPrefix() {
        // Should be test_key_manager since deleteEntry doesn't reset currentPrefix
        assertEquals(testPrefix, KeyManager.getCurrentPrefix())
        
        // Should update after key generation with new prefix
        val newPrefix = "new_prefix"
        KeyManager.generateKeyWithPrefix(newPrefix)
        assertEquals(newPrefix, KeyManager.getCurrentPrefix())
        
        // Clean up the new key
        KeyManager.deleteEntry(newPrefix)
    }

    @Test
    fun testGetPrivateKeyByPrefix() {
        // Generate a key first
        val keyPair = KeyManager.generateKeyWithPrefix(testPrefix)
        
        // Get private key
        val privateKey = KeyManager.getPrivateKeyByPrefix(testPrefix)
        assertNotNull("Private key should not be null", privateKey)
        assertEquals("Private key should match the generated one", 
            keyPair.private.algorithm, privateKey?.algorithm)
        
        // Test with non-existent prefix
        val nonExistentKey = KeyManager.getPrivateKeyByPrefix(nonExistentPrefix)
        assertNull("Private key should be null for non-existent prefix", nonExistentKey)
    }

    @Test
    fun testGetPublicKeyByPrefix() {
        // Generate a key first
        val keyPair = KeyManager.generateKeyWithPrefix(testPrefix)
        
        // Get public key
        val publicKey = KeyManager.getPublicKeyByPrefix(testPrefix)
        assertNotNull("Public key should not be null", publicKey)
        assertTrue("Public key should be an EC key", publicKey is ECPublicKey)
        assertEquals("Public key should match the generated one",
            (keyPair.public as ECPublicKey).w, (publicKey as ECPublicKey).w)
        
        // Test with non-existent prefix
        val nonExistentKey = KeyManager.getPublicKeyByPrefix(nonExistentPrefix)
        assertNull("Public key should be null for non-existent prefix", nonExistentKey)
    }

    @Test
    fun testContainsAlias() {
        // Initially should not exist
        assertFalse("Key should not exist before generation", 
            KeyManager.containsAlias(testPrefix))
        
        // Generate key
        KeyManager.generateKeyWithPrefix(testPrefix)
        
        // Should exist after generation
        assertTrue("Key should exist after generation", 
            KeyManager.containsAlias(testPrefix))
        
        // Non-existent key should return false
        assertFalse("Non-existent key should return false", 
            KeyManager.containsAlias(nonExistentPrefix))
    }

    @Test
    fun testGetAllAliases() {
        // Generate a key
        KeyManager.generateKeyWithPrefix(testPrefix)
        
        // Get all aliases
        val aliases = KeyManager.getAllAliases()
        assertNotNull("Aliases list should not be null", aliases)
        assertTrue("Aliases list should contain the test key", 
            aliases.contains(KeyManager.KEYSTORE_ALIAS_PREFIX + testPrefix))
    }

    @Test
    fun testDeleteEntry() {
        // Generate a key
        KeyManager.generateKeyWithPrefix(testPrefix)
        
        // Delete existing key
        assertTrue("Deleting existing key should return true", 
            KeyManager.deleteEntry(testPrefix))
        assertFalse("Key should not exist after deletion", 
            KeyManager.containsAlias(testPrefix))
        
        // Delete non-existent key
        assertFalse("Deleting non-existent key should return false", 
            KeyManager.deleteEntry(nonExistentPrefix))
    }

    @Test
    fun testPublicKeyToFormatString() {
        // Generate a key
        val keyPair = KeyManager.generateKeyWithPrefix(testPrefix)
        val publicKey = keyPair.public
        
        // Convert to format string
        val formatString = publicKey.toFormatString()
        assertNotNull("Format string should not be null", formatString)
        assertTrue("Format string should not be empty", formatString.isNotEmpty())
        assertTrue("Format string should be a valid hex string", 
            formatString.matches(Regex("[0-9a-f]+")))
        
        // Test with null public key
        val nullFormatString = (null as PublicKey?).toFormatString()
        assertEquals("Null public key should return empty string", "", nullFormatString)
    }

    @Test
    fun testKeyGenerationWithMultiplePrefixes() {
        val prefix1 = "${testPrefix}_1"
        val prefix2 = "${testPrefix}_2"
        
        // Generate two different keys
        val keyPair1 = KeyManager.generateKeyWithPrefix(prefix1)
        val keyPair2 = KeyManager.generateKeyWithPrefix(prefix2)
        
        // Verify both keys exist and are different
        assertTrue("First key should exist", KeyManager.containsAlias(prefix1))
        assertTrue("Second key should exist", KeyManager.containsAlias(prefix2))
        assertNotSame("Keys should be different", keyPair1.public, keyPair2.public)
        
        // Clean up
        KeyManager.deleteEntry(prefix1)
        KeyManager.deleteEntry(prefix2)
    }

    @Test
    fun testKeyRegeneration() {
        // Generate initial key
        val keyPair1 = KeyManager.generateKeyWithPrefix(testPrefix)
        val publicKey1 = keyPair1.public.toFormatString()
        
        // Delete and regenerate key with same prefix
        KeyManager.deleteEntry(testPrefix)
        val keyPair2 = KeyManager.generateKeyWithPrefix(testPrefix)
        val publicKey2 = keyPair2.public.toFormatString()
        
        // Keys should be different
        assertNotEquals("Regenerated key should be different", publicKey1, publicKey2)
    }
}