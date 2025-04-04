package io.outblock.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nftco.flow.sdk.HashAlgorithm
import com.nftco.flow.sdk.SignatureAlgorithm
import junit.framework.TestCase.*
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyStoreCryptoProviderTest {
    private lateinit var cryptoProvider: KeyStoreCryptoProvider
    private val testPrefix = "test_crypto_provider"

    @Before
    fun setup() {
        // Clean up any existing keys and generate new ones
        KeyManager.deleteEntry(testPrefix)
        KeyManager.generateKeyWithPrefix(testPrefix)
        cryptoProvider = KeyStoreCryptoProvider(testPrefix)
    }

    @Test
    fun testGetPublicKey() {
        val publicKey = cryptoProvider.getPublicKey()
        assertNotNull("Public key should not be null", publicKey)
        assertTrue("Public key should not be empty", publicKey.isNotEmpty())
    }

    @Test
    fun testGetUserSignature() {
        val testJwt = "test.jwt.token"
        val signature = cryptoProvider.getUserSignature(testJwt)
        assertNotNull("Signature should not be null", signature)
        assertTrue("Signature should not be empty", signature.isNotEmpty())
    }

    @Test
    fun testSignData() {
        val testData = "Hello, Flow!".encodeToByteArray()
        val signature1 = cryptoProvider.signData(testData)
        val signature2 = cryptoProvider.signData(testData)
        
        // Verify that signatures are not null and have expected format
        assertNotNull("First signature should not be null", signature1)
        assertNotNull("Second signature should not be null", signature2)
        assertTrue("First signature should not be empty", signature1.isNotEmpty())
        assertTrue("Second signature should not be empty", signature2.isNotEmpty())
        
        // Verify signatures are valid hex strings of appropriate length for ECDSA P-256
        assertTrue("Signature should be valid hex string",
            signature1.matches(Regex("[0-9a-f]+")))
        assertEquals("ECDSA P-256 signature should be 128 characters long", 
            128, signature1.length)
            
        // Different valid signatures can be generated for the same input
        assertNotEquals("Signatures should be different due to ECDSA non-determinism", 
            signature1, signature2)
    }

    @Test
    fun testGetSigner() {
        val signer = cryptoProvider.getSigner()
        assertNotNull("Signer should not be null", signer)
        assertTrue("Signer should be instance of WalletCoreSigner", signer is WalletCoreSigner)
    }

    @Test
    fun testGetHashAlgorithm() {
        assertEquals("Hash algorithm should be SHA2_256", 
            HashAlgorithm.SHA2_256, 
            cryptoProvider.getHashAlgorithm())
    }

    @Test
    fun testGetSignatureAlgorithm() {
        assertEquals("Signature algorithm should be ECDSA_P256", 
            SignatureAlgorithm.ECDSA_P256, 
            cryptoProvider.getSignatureAlgorithm())
    }

    @Test
    fun testGetKeyWeight() {
        assertEquals("Key weight should be 1000", 
            1000, 
            cryptoProvider.getKeyWeight())
    }

    @Test
    fun testSignerWithDifferentData() {
        val data1 = "Test data 1".encodeToByteArray()
        val data2 = "Test data 2".encodeToByteArray()
        
        val signature1 = cryptoProvider.signData(data1)
        val signature2 = cryptoProvider.signData(data2)
        
        assertNotNull(signature1)
        assertNotNull(signature2)
        assertNotEquals("Signatures for different data should be different", 
            signature1, 
            signature2)
    }
} 