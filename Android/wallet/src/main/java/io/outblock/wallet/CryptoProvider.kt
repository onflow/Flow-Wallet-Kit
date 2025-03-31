package io.outblock.wallet

import com.nftco.flow.sdk.Signer
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm

interface CryptoProvider {
    fun getPublicKey(): String
    fun getUserSignature(jwt: String): String
    fun signData(data: ByteArray): String
    fun getSigner(): Signer
    fun getHashAlgorithm(): HashingAlgorithm
    fun getSignatureAlgorithm(): SigningAlgorithm
    fun getKeyWeight(): Int
}