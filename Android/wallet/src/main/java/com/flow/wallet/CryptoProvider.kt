package com.flow.wallet

import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm

interface CryptoProvider {
    fun getPublicKey(): String
    suspend fun getUserSignature(jwt: String): String
    suspend fun signData(data: ByteArray): String
    fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer
    fun getHashAlgorithm(): HashingAlgorithm
    fun getSignatureAlgorithm(): SigningAlgorithm
    fun getKeyWeight(): Int

    interface Signer {
        suspend fun sign(bytes: ByteArray): ByteArray
    }
}