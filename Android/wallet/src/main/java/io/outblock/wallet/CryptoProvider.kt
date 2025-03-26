package io.outblock.wallet

import org.onflow.flow.sdk.HashAlgorithm
import org.onflow.flow.sdk.SignatureAlgorithm
import org.onflow.flow.sdk.Signer


interface CryptoProvider {
    fun getPublicKey(): String
    fun getUserSignature(jwt: String): String
    fun signData(data: ByteArray): String
    fun getSigner(): Signer
    fun getHashAlgorithm(): HashAlgorithm
    fun getSignatureAlgorithm(): SignatureAlgorithm
    fun getKeyWeight(): Int
}