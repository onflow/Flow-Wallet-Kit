package io.outblock.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onflow.flow.models.HashingAlgorithm
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class WalletCoreSignerTest {
    private lateinit var keyPair: KeyPair
    private lateinit var signer: WalletCoreSigner
    private val testPrefix = "test_signer"

    @Before
    fun setup() {
        // Clean up any existing keys and generate new ones
        KeyManager.deleteEntry(testPrefix)
        keyPair = KeyManager.generateKeyWithPrefix(testPrefix)
        signer = WalletCoreSigner(keyPair.private)
    }

    @Test
    fun testSignerInitialization() {
        // Test that signer can be created with custom hasher
        val customSigner = WalletCoreSigner(
            keyPair.private,
            HashingAlgorithm.SHA2_256,
        )
        assertNotNull("Custom signer should be created successfully", customSigner)
    }

    @Test
    fun testSignDataFormat() = runBlocking {
        val testData = "Test message".encodeToByteArray()
        val signature = signer.sign(testData)

        // ECDSA P-256 signatures should be exactly 64 bytes (32 bytes for R + 32 bytes for S)
        assertEquals("Signature should be exactly 64 bytes", 64, signature.size)
    }

    @Test
    fun testSignatureVerification() = runBlocking {
        val testData = "Test message".encodeToByteArray()
        val signature = signer.sign(testData)

        // Verify the signature using standard Java security APIs
        val verifySignature = Signature.getInstance("SHA256withECDSA")
        verifySignature.initVerify(keyPair.public)
        verifySignature.update(testData)

        // Convert our R+S format back to DER for Java verification
        val r = signature.slice(0..31).toByteArray()
        val s = signature.slice(32..63).toByteArray()
        
        // Create DER sequence manually
        val derSignature = buildDerSignature(r, s)

        assertTrue("Signature should be valid", verifySignature.verify(derSignature))
    }

    private fun encodeIntegerToDer(value: ByteArray): ByteArray {
        // Remove leading zeros if any
        var valueBytes = value.dropWhile { it == 0.toByte() }.toByteArray()
        // Ensure the value is positive: if the first byte is >= 0x80, prepend a 0x00.
        if (valueBytes.isNotEmpty() && valueBytes[0].toInt() and 0x80 != 0) {
            valueBytes = byteArrayOf(0x00) + valueBytes
        }
        val length = valueBytes.size
        return byteArrayOf(0x02, length.toByte()) + valueBytes
    }

    private fun buildDerSignature(r: ByteArray, s: ByteArray): ByteArray {
        // Use the last 32 bytes for each value, then DER-encode them
        val rValue = r.takeLast(32).toByteArray()
        val sValue = s.takeLast(32).toByteArray()

        val rDer = encodeIntegerToDer(rValue)
        val sDer = encodeIntegerToDer(sValue)

        val totalLength = rDer.size + sDer.size
        return byteArrayOf(0x30, totalLength.toByte()) + rDer + sDer
    }

    @Test
    fun testSignWithNullPrivateKey() = runBlocking {
        val nullSigner = WalletCoreSigner(null)
        val testData = "Test message".encodeToByteArray()
        
        try {
            nullSigner.sign(testData)
            fail("Should throw WalletCoreException when private key is null")
        } catch (e: WalletCoreException) {
            assertEquals("Error signing data", e.message)
            assertNotNull("Cause should not be null", e.cause)
            assertTrue("Cause should be WalletCoreException", e.cause is WalletCoreException)
            assertEquals("Error getting private key", (e.cause as WalletCoreException).message)
        }
    }

    @Test
    fun testSignMultipleMessages() = runBlocking {
        val message1 = "First message".encodeToByteArray()
        val message2 = "Second message".encodeToByteArray()
        
        val signature1 = signer.sign(message1)
        val signature2 = signer.sign(message2)
        
        // Signatures should be different for different messages
        assertFalse("Different messages should produce different signatures",
            signature1.contentEquals(signature2))
            
        // Each signature should still be 64 bytes
        assertEquals(64, signature1.size)
        assertEquals(64, signature2.size)
    }

    @Test
    fun testHasherImplementation() {
        val hasher = HasherImpl(HashingAlgorithm.SHA2_256)
        val testData = "Test data".encodeToByteArray()
        
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val expectedHash = messageDigest.digest(testData)
        val actualHash = hasher.hash(testData)
        
        assertTrue("Hasher should produce correct SHA-256 hash",
            expectedHash.contentEquals(actualHash))
    }
} 
