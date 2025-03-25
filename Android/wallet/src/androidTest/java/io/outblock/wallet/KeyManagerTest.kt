package io.outblock.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyManagerTest {

    @Test
    fun testGenerateKeyWithPrefix() {
        val keyPair = KeyManager.generateKeyWithPrefix("test_prefix")
        assertNotNull(keyPair)
    }

    @Test
    fun testGetPrivateKeyByPrefix() {
        val privateKey = KeyManager.getPrivateKeyByPrefix("test_prefix")
        assertNotNull(privateKey)
    }

    @Test
    fun testGetPublicKeyByPrefix() {
        val publicKey = KeyManager.getPublicKeyByPrefix("test_prefix")
        assertNotNull(publicKey)
    }

    @Test
    fun testContainsAlias() {
        assertTrue(KeyManager.containsAlias("test_prefix"))
        assertFalse(KeyManager.containsAlias("nonexistent"))
    }

    @Test
    fun testDeleteEntry() {
        assertTrue(KeyManager.deleteEntry("test_prefix"))
        assertFalse(KeyManager.deleteEntry("nonexistent"))
    }
}
