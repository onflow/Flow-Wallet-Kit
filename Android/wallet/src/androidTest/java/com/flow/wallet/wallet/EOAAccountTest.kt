package com.flow.wallet.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.wallet.NativeLibraryManager
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.InMemoryStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EOAAccountTest {

    private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    // Standard BIP44 m/44'/60'/0'/0/0 address for this mnemonic
    private val expectedIndex0Address = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

    private lateinit var storage: InMemoryStorage
    private lateinit var seedPhraseKey: SeedPhraseKey

    @Before
    fun setup() {
        NativeLibraryManager.ensureLibraryLoaded()
        storage = InMemoryStorage()
        seedPhraseKey = SeedPhraseKey(
            testMnemonic, "", SeedPhraseKey.DEFAULT_DERIVATION_PATH, storage
        )
    }

    // MARK: - Multi EOA Derivation

    @Test
    fun testDeriveMultipleEOAAccounts() {
        val accounts = listOf(0, 1, 2).map { index ->
            EOAAccount(
                address = seedPhraseKey.ethAddress(index),
                index = index,
                publicKey = seedPhraseKey.ethPublicKey(index),
                key = seedPhraseKey
            )
        }

        assertEquals(3, accounts.size)
        // Each account should have a unique address
        val addresses = accounts.map { it.address }.toSet()
        assertEquals("Each derivation index should produce a unique address", 3, addresses.size)

        // Verify indexes match
        assertEquals(0, accounts[0].index)
        assertEquals(1, accounts[1].index)
        assertEquals(2, accounts[2].index)

        // Index 0 should match known address
        assertEquals(expectedIndex0Address, accounts[0].address)
    }

    @Test
    fun testEOAAccountSignDigest() {
        val account = EOAAccount(
            address = seedPhraseKey.ethAddress(0),
            index = 0,
            publicKey = seedPhraseKey.ethPublicKey(0),
            key = seedPhraseKey
        )

        val digest = HasherImpl.keccak256("hello world".toByteArray())
        val signature = account.signDigest(digest)

        assertEquals(65, signature.size)
        val v = signature[64].toInt() and 0xFF
        assertTrue("v should be 27 or 28", v == 27 || v == 28)

        // Should match direct key signing
        val directSignature = seedPhraseKey.ethSignDigest(digest, 0)
        assertArrayEquals(signature, directSignature)
    }

    @Test
    fun testEOAAccountPersonalSign() {
        val account = EOAAccount(
            address = seedPhraseKey.ethAddress(0),
            index = 0,
            publicKey = seedPhraseKey.ethPublicKey(0),
            key = seedPhraseKey
        )

        val message = "Flow Wallet".toByteArray()
        val signature = account.signPersonalMessage(message)

        assertEquals(65, signature.size)

        // Manually compute expected: prefix + keccak256 + sign
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray(Charsets.UTF_8)
        val payload = prefix + message
        val digest = HasherImpl.keccak256(payload)
        val directSignature = seedPhraseKey.ethSignDigest(digest, 0)
        assertArrayEquals(signature, directSignature)
    }

    @Test
    fun testDifferentIndexesProduceDifferentSignatures() {
        val account0 = EOAAccount(
            address = seedPhraseKey.ethAddress(0), index = 0,
            publicKey = seedPhraseKey.ethPublicKey(0), key = seedPhraseKey
        )
        val account1 = EOAAccount(
            address = seedPhraseKey.ethAddress(1), index = 1,
            publicKey = seedPhraseKey.ethPublicKey(1), key = seedPhraseKey
        )

        val digest = HasherImpl.keccak256("test message".toByteArray())
        val sig0 = account0.signDigest(digest)
        val sig1 = account1.signDigest(digest)

        assertFalse("Different derivation indexes should produce different signatures", sig0.contentEquals(sig1))
    }

    @Test
    fun testEOAAccountPublicKey() {
        val account = EOAAccount(
            address = seedPhraseKey.ethAddress(0), index = 0,
            publicKey = seedPhraseKey.ethPublicKey(0), key = seedPhraseKey
        )

        // Uncompressed secp256k1 public key is 65 bytes (04 prefix + 32x + 32y)
        assertEquals(65, account.publicKey.size)
        assertEquals(0x04.toByte(), account.publicKey[0])
    }

    @Test
    fun testEOAAccountPrivateKey() {
        val account0 = EOAAccount(
            address = seedPhraseKey.ethAddress(0), index = 0,
            publicKey = seedPhraseKey.ethPublicKey(0), key = seedPhraseKey
        )
        val account1 = EOAAccount(
            address = seedPhraseKey.ethAddress(1), index = 1,
            publicKey = seedPhraseKey.ethPublicKey(1), key = seedPhraseKey
        )

        val pk0 = account0.privateKeyData()
        val pk1 = account1.privateKeyData()

        assertEquals(32, pk0.size)
        assertEquals(32, pk1.size)
        assertFalse(pk0.contentEquals(pk1))
    }

    @Test
    fun testLargeDerivationIndex() {
        val account = EOAAccount(
            address = seedPhraseKey.ethAddress(100), index = 100,
            publicKey = seedPhraseKey.ethPublicKey(100), key = seedPhraseKey
        )

        assertEquals(100, account.index)
        assertTrue(account.address.startsWith("0x"))
        assertEquals(42, account.address.length)
    }

    // MARK: - Wallet integration (via KeyWallet)

    @Test
    fun testKeyWalletGetEOAAccounts() = runBlocking {
        val wallet = KeyWallet(
            key = seedPhraseKey,
            networks = mutableSetOf(),
            storage = storage
        )

        val accounts = wallet.getEOAAccounts(listOf(0, 1, 2))

        assertEquals(3, accounts.size)
        assertEquals(expectedIndex0Address, accounts[0].address)
        assertEquals(0, accounts[0].index)
        assertEquals(1, accounts[1].index)
        assertEquals(2, accounts[2].index)
    }

    @Test
    fun testKeyWalletGetEOAAccountsDefaultIndex() = runBlocking {
        val wallet = KeyWallet(
            key = seedPhraseKey,
            networks = mutableSetOf(),
            storage = storage
        )

        val accounts = wallet.getEOAAccounts()

        assertEquals(1, accounts.size)
        assertEquals(0, accounts[0].index)
    }
}
