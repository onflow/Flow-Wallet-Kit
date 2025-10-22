package com.flow.wallet.crypto

import com.flow.wallet.errors.WalletError
import org.onflow.flow.models.HashingAlgorithm
import wallet.core.jni.Hash

/**
 * Implementation of hashing functionality using Trust Wallet's core library
 * Provides a unified interface for hashing operations throughout the project
 */
object HasherImpl {

    /**
     * Hash data with the requested algorithm.
     * @throws WalletError.UnsupportedHashAlgorithm if the algo isn’t SHA-256 or SHA3-256
     */
    fun hash(data: ByteArray, algorithm: HashingAlgorithm): ByteArray = try {
        when (algorithm) {
            HashingAlgorithm.SHA2_256 -> Hash.sha256(data)
            HashingAlgorithm.SHA3_256 -> Hash.sha3256(data)
            else -> throw WalletError.UnsupportedHashAlgorithm
        }
    } catch (e: Exception) {
        throw WalletError(WalletError.SignError.code, "Hashing failed: ${e.message}")
    }

    /** Convenience wrappers */
    fun sha256(data: ByteArray)  = hash(data, HashingAlgorithm.SHA2_256)
    fun sha3_256(data: ByteArray) = hash(data, HashingAlgorithm.SHA3_256)
    fun keccak256(data: ByteArray): ByteArray = Hash.keccak256(data)
}
