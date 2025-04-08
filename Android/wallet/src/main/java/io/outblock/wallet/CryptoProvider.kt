package io.outblock.wallet

import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

interface CryptoProvider {
    fun getPublicKey(): String
    suspend fun getUserSignature(jwt: String): String
    suspend fun signData(data: ByteArray): String
    fun getSigner(): Signer
    fun getHashAlgorithm(): HashingAlgorithm
    fun getSignatureAlgorithm(): SigningAlgorithm
    fun getKeyWeight(): Int
}