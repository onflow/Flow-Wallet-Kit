package com.flow.wallet.keys

import com.flow.wallet.NativeLibraryManager
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.InMemoryStorage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import wallet.core.jni.PrivateKey as TWPrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EthereumKeyTests {

    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val mnemonicEthAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    private val privateKeyHex = "9a983cb3d832fbde5ab49d692b7a8bf5b5d232479c99333d0fc8e1d21f1b55b6"
    private val privateKeyEthAddress = "0x6Fac4D18c912343BF86fa7049364Dd4E424Ab9C0"

    @Before
    fun setup() {
        Assert.assertTrue(NativeLibraryManager.ensureLibraryLoaded())
    }

    @Test
    fun seedPhraseKeyDerivesEthereumAddressAndSignatures() {
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
        Assert.assertTrue(signature.last() == 27.toByte() || signature.last() == 28.toByte())
    }

    @Test
    fun privateKeyDerivesEthereumAddressAndSignatures() {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)

        assertEquals(privateKeyEthAddress.lowercase(), key.ethAddress().lowercase())
        val digest = HasherImpl.keccak256("flow".toByteArray())
        val signature = key.ethSignDigest(digest)
        assertEquals(65, signature.size)
        Assert.assertTrue(signature.last() == 27.toByte() || signature.last() == 28.toByte())
    }

    @Test
    fun invalidDigestLengthThrows() {
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
