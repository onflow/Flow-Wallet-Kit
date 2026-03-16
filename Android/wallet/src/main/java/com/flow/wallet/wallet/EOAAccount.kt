package com.flow.wallet.wallet

import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.EthereumKeyProtocol
import com.flow.wallet.keys.EthereumSignatureUtils
import com.google.protobuf.ByteString
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.EthereumAbi
import wallet.core.jni.proto.Ethereum

/**
 * Represents a single Ethereum EOA derived from a BIP44 path.
 * Bundles the address, derivation index, public key, and signing capabilities.
 *
 * @property address EIP-55 checksummed Ethereum address
 * @property index BIP44 derivation index (m/44'/60'/0'/0/{index})
 * @property publicKey Uncompressed secp256k1 public key bytes
 */
class EOAAccount(
    val address: String,
    val index: Int,
    val publicKey: ByteArray,
    private val key: EthereumKeyProtocol
) {
    /** Sign a pre-hashed 32-byte digest. Returns [r(32)|s(32)|v(1)]. */
    fun signDigest(digest: ByteArray): ByteArray {
        return key.ethSignDigest(digest, index)
    }

    /** EIP-191 personal_sign. */
    fun signPersonalMessage(message: ByteArray): ByteArray {
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray(Charsets.UTF_8)
        val payload = prefix + message
        val digest = HasherImpl.keccak256(payload)
        return signDigest(digest)
    }

    /** EIP-712 signTypedData. */
    fun signTypedData(json: String): ByteArray {
        val digest = EthereumAbi.encodeTyped(json)
        if (digest.size != 32) {
            throw WalletError.InvalidEthereumTypedData
        }
        return signDigest(digest)
    }

    /** Sign an Ethereum transaction via WalletCore AnySigner. */
    fun signTransaction(input: Ethereum.SigningInput): Ethereum.SigningOutput {
        val privateKey = key.ethPrivateKey(index)
        val builder = input.toBuilder()
        builder.privateKey = ByteString.copyFrom(privateKey)
        return try {
            AnySigner.sign(builder.build(), CoinType.ETHEREUM, Ethereum.SigningOutput.parser())
        } finally {
            builder.clearPrivateKey()
            privateKey.fill(0)
        }
    }

    /** Raw 32-byte secp256k1 private key. Caller is responsible for secure handling. */
    fun privateKeyData(): ByteArray = key.ethPrivateKey(index)
}
