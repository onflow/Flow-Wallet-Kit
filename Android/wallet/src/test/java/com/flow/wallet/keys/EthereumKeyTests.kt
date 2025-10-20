package com.flow.wallet.keys

import com.flow.wallet.NativeLibraryManager
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.InMemoryStorage
import kotlin.test.*
import org.junit.Before
import org.junit.Test
import wallet.core.jni.PrivateKey as TWPrivateKey

class EthereumKeyTests {

    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val mnemonicEthAddress = "0x27Ef5cDBe01777D62438AfFeb695e33fC2335979"
    private val privateKeyHex = "4c0883a69102937d6231471b5dbb6204fe512961708279a0135b52deeadb26a9"
    private val privateKeyEthAddress = "0x90f8bf6a479f320ead074411a4b0e7944ea8c9c1"

    @Before
    fun setup() {
        assertTrue(NativeLibraryManager.ensureLibraryLoaded(), "TrustWalletCore native library must load for tests")
    }

    @Test
    fun `seed phrase key derives ethereum address and signatures`() {
        val storage = InMemoryStorage()
        val key = SeedPhraseKey(
            mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = storage
        )

        assertEquals(mnemonicEthAddress, key.ethAddress())
        assertEquals(65, key.ethPublicKey().size)
        assertEquals(32, key.ethPrivateKey().size)

        val digest = HasherImpl.keccak256("hello world".toByteArray())
        val signature = key.ethSignDigest(digest)
        assertEquals(65, signature.size)
        assertTrue(signature.last() == 27.toByte() || signature.last() == 28.toByte())
    }

    @Test
    fun `private key derives ethereum address and signatures`() {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)

        assertEquals(privateKeyEthAddress.lowercase(), key.ethAddress().lowercase())
        val digest = HasherImpl.keccak256("flow".toByteArray())
        val signature = key.ethSignDigest(digest)
        assertEquals(65, signature.size)
        assertTrue(signature.last() == 27.toByte() || signature.last() == 28.toByte())
    }

    @Test
    fun `invalid digest length throws`() {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)

        val error = assertFailsWith<WalletError> {
            key.ethSignDigest(byteArrayOf(0x01))
        }
        assertEquals(WalletError.InvalidEthereumMessage.code, error.code)
    }

    private fun String.hexToByteArray(): ByteArray {
        val clean = removePrefix("0x")
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
