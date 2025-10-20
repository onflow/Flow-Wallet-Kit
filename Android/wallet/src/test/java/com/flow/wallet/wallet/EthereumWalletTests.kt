package com.flow.wallet.wallet

import com.flow.wallet.NativeLibraryManager
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.InMemoryStorage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.onflow.flow.ChainId
import wallet.core.jni.proto.Ethereum
import wallet.core.jni.PrivateKey as TWPrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import com.flow.wallet.errors.WalletError
import com.google.protobuf.ByteString
import wallet.core.jni.EthereumAbi

class EthereumWalletTests {

    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val privateKeyHex = "4c0883a69102937d6231471b5dbb6204fe512961708279a0135b52deeadb26a9"
    private val expectedAddress = "0x90f8bf6a479f320ead074411a4b0e7944ea8c9c1"

    @Before
    fun setup() {
        assertTrue(NativeLibraryManager.ensureLibraryLoaded(), "TrustWalletCore native library must load for tests")
    }

    @Test
    fun `wallet personal sign matches direct signature`() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        assertEquals(expectedAddress.lowercase(), wallet.ethAddress().lowercase())

        val message = "Flow Wallet".toByteArray()
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray()
        val payload = prefix + message
        val digest = HasherImpl.keccak256(payload)

        val walletSignature = wallet.ethSignPersonalMessage(message)
        val directSignature = key.ethSignDigest(digest)

        assertEquals(directSignature.toHexString(), walletSignature.toHexString())
    }

    @Test
    fun `wallet typed data signing matches direct signature`() = runBlocking {
        val storage = InMemoryStorage()
        val key = SeedPhraseKey(
            mnemonic,
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = storage
        )
        val wallet = TestWallet(key, storage)

        val typedData = """
        {
            "types": {
                "EIP712Domain": [
                    {"name": "name", "type": "string"},
                    {"name": "version", "type": "string"},
                    {"name": "chainId", "type": "uint256"},
                    {"name": "verifyingContract", "type": "address"}
                ],
                "Person": [
                    {"name": "name", "type": "string"},
                    {"name": "wallets", "type": "address[]"}
                ],
                "Mail": [
                    {"name": "from", "type": "Person"},
                    {"name": "to", "type": "Person[]"},
                    {"name": "contents", "type": "string"}
                ]
            },
            "primaryType": "Mail",
            "domain": {
                "name": "Ether Mail",
                "version": "1",
                "chainId": 1,
                "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
            },
            "message": {
                "from": {
                    "name": "Cow",
                    "wallets": [
                        "CD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                        "DeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
                    ]
                },
                "to": [
                    {
                        "name": "Bob",
                        "wallets": [
                            "bBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                            "B0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                            "B0B0b0b0b0b0B000000000000000000000000000"
                        ]
                    }
                ],
                "contents": "Hello, Bob!"
            }
        }
        """.trimIndent()

        val digest = EthereumAbi.encodeTyped(typedData)
        val walletSignature = wallet.ethSignTypedData(typedData)
        val directSignature = key.ethSignDigest(digest)

        assertEquals("a85c2e2b118698e88db68a8105b794a8cc7cec074e89ef991cb4f5f533819cc2", digest.toHexString())
        assertEquals(directSignature.toHexString(), walletSignature.toHexString())
    }

    @Test
    fun `wallet transaction signing matches wallet core example`() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val input = Ethereum.SigningInput.newBuilder().apply {
            chainId = ByteString.copyFrom("01".hexToByteArray())
            nonce = ByteString.copyFrom("09".hexToByteArray())
            gasPrice = ByteString.copyFrom("04a817c800".hexToByteArray())
            gasLimit = ByteString.copyFrom("5208".hexToByteArray())
            toAddress = "0x3535353535353535353535353535353535353535"
            transaction = Ethereum.Transaction.newBuilder().apply {
                transfer = Ethereum.Transaction.Transfer.newBuilder()
                    .setAmount(ByteString.copyFrom("0de0b6b3a7640000".hexToByteArray()))
                    .build()
            }.build()
        }.build()

        val output = wallet.ethSignTransaction(input)
        assertEquals(
            "f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83",
            output.encoded.toByteArray().toHexString()
        )
    }

    @Test
    fun `wallet rejects invalid digest length`() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val error = assertFailsWith<WalletError> {
            wallet.ethSignDigest(byteArrayOf(0x01))
        }
        assertEquals(WalletError.InvalidEthereumMessage.code, error.code)
    }

    private class TestWallet(
        private val testKey: com.flow.wallet.keys.KeyProtocol,
        storage: InMemoryStorage
    ) : BaseWallet(WalletType.KEY, mutableSetOf(ChainId.Mainnet), storage, null) {
        override suspend fun fetchAccountsForNetwork(network: ChainId) = emptyList<org.onflow.flow.models.Account>()
        override suspend fun fetchAccountByAddress(address: String, network: ChainId) {}
        override suspend fun addAccount(account: com.flow.wallet.account.Account) {}
        override suspend fun removeAccount(address: String) {}
        override suspend fun fetchAccounts() {}
        override fun getKeyForAccount(): com.flow.wallet.keys.KeyProtocol? = testKey
    }

    private fun String.hexToByteArray(): ByteArray {
        val clean = removePrefix("0x")
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
