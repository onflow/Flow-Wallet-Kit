package com.flow.wallet.wallet

import com.flow.wallet.NativeLibraryManager
import com.flow.wallet.crypto.HasherImpl
import android.util.Log
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
import kotlin.test.assertFailsWith
import org.junit.Assert
import com.flow.wallet.errors.WalletError
import com.google.protobuf.ByteString
import wallet.core.jni.EthereumAbi
import java.math.BigInteger

class EthereumWalletTests {

    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val privateKeyHex = "1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727"
    private val expectedAddress = "0x90f8bf6a479f320ead074411a4b0e7944ea8c9c1"

    @Before
    fun setup() {
        Assert.assertTrue(NativeLibraryManager.ensureLibraryLoaded())
    }

    @Test
    fun walletPersonalSignMatchesDirectSignature() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val message = "Flow Wallet".toByteArray()
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray()
        val payload = prefix + message
        val digest = HasherImpl.keccak256(payload)

        val walletSignature = wallet.ethSignPersonalMessage(message)
        val directSignature = key.ethSignDigest(digest)

        Log.d("EthereumWalletTests", "digest: ${digest.toHexString()}")
        Log.d("EthereumWalletTests", "direct: ${directSignature.toHexString()}")
        Log.d("EthereumWalletTests", "wallet: ${walletSignature.toHexString()}")

        Assert.assertEquals(65, walletSignature.size)
        org.junit.Assert.assertTrue(walletSignature.last() == 27.toByte() || walletSignature.last() == 28.toByte())
        assertEquals(directSignature.toHexString(), walletSignature.toHexString())
    }

    @Test
    fun walletPersonalDataAliasMatchesMessage() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val message = "Hello Flow".toByteArray()
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray()
        val digest = HasherImpl.keccak256(prefix + message)

        val signatureFromAlias = wallet.ethSignPersonalData(message)
        val signatureFromMessage = wallet.ethSignPersonalMessage(message)
        val directSignature = key.ethSignDigest(digest)

        assertEquals(signatureFromMessage.toHexString(), signatureFromAlias.toHexString())
        assertEquals(directSignature.toHexString(), signatureFromAlias.toHexString())
    }

    @Test
    fun walletTypedDataSigningMatchesDirectSignature() = runBlocking {
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
    fun walletTransactionSigningMatchesWalletCoreExample() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey("0x4646464646464646464646464646464646464646464646464646464646464646".hexToByteArray())
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
        val expectedHash = HasherImpl.keccak256(output.encoded.toByteArray()).toHexString()
        assertEquals(expectedHash, output.preHash.toByteArray().toHexString())
        assertEquals(expectedHash, output.txId().toHexString())
    }

    @Test
    fun walletEcRecoverReturnsExpectedAddress() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val signature = "a77836f00d36b5cd16c17bb26f23cdc78db7928b8d1d1341bd3f11cc279b60a508b80e01992cb0ad9a6c2212177dd84a43535e3bf29794c1dc13d17a59c2d98c1b".hexToByteArray()
        val message = "Hello, Flow EVM!".toByteArray()

        val recovered = wallet.ethRecoverAddress(signature, message)

        assertEquals("0xe513e4f52f76c9bd3db2474e885b8e7e814ea516", recovered.lowercase())
    }

    @Test
    fun walletRejectsInvalidDigestLength() = runBlocking {
        val storage = InMemoryStorage()
        val privateKey = TWPrivateKey(privateKeyHex.hexToByteArray())
        val key = PrivateKey(privateKey, storage)
        val wallet = TestWallet(key, storage)

        val error = assertFailsWith<WalletError> {
            wallet.ethSignDigest(byteArrayOf(0x01))
        }
        assertEquals(WalletError.InvalidEthereumMessage.code, error.code)
    }

    @Test
    fun walletTypedDataProducesExpectedHexSignature() = runBlocking {
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

        val walletSignature = wallet.ethSignTypedData(typedData)
        val digest = EthereumAbi.encodeTyped(typedData)
        val directSignature = key.ethSignDigest(digest)

        assertEquals("a85c2e2b118698e88db68a8105b794a8cc7cec074e89ef991cb4f5f533819cc2", digest.toHexString())
        assertEquals(directSignature.toHexString(), walletSignature.toHexString())
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
        var clean = removePrefix("0x")
        if (clean.isEmpty()) return byteArrayOf()
        if (clean.length % 2 != 0) {
            clean = "0$clean"
        }
        val lower = clean.lowercase()
        return ByteArray(lower.length / 2) { index ->
            lower.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.hexToMinimalByteArray(): ByteArray {
        val clean = removePrefix("0x")
        if (clean.isEmpty()) return byteArrayOf()
        val value = BigInteger(clean, 16)
        if (value == BigInteger.ZERO) return byteArrayOf()
        val bytes = value.toByteArray()
        return if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
