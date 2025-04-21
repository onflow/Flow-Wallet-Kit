package com.flow.wallet.crypto

import com.flow.wallet.errors.WalletError
import com.trustwallet.wallet.core.Hash
import com.trustwallet.wallet.core.PrivateKey
import org.onflow.flow.models.HashingAlgorithm

/**
 * Implementation of hashing functionality using Trust Wallet's core library
 * Provides a unified interface for hashing operations throughout the project
 */
class HasherImpl {
    companion object {
        /**
         * Hash data using the specified algorithm
         * @param data Data to hash
         * @param algorithm Hashing algorithm to use
         * @return Hashed data
         * @throws WalletError if hashing fails or algorithm is not supported
         */
        fun hash(data: ByteArray, algorithm: HashingAlgorithm): ByteArray {
            return try {
                // Create a temporary private key to use Trust Wallet's hashing
                val tempKey = PrivateKey()
                when (algorithm) {
                    HashingAlgorithm.SHA2_256 -> tempKey.hash(data, Hash.SHA256)
                    HashingAlgorithm.SHA3_256 -> tempKey.hash(data, Hash.SHA3_256)
                    else -> throw WalletError.UnsupportedHashAlgorithm
                }
            } catch (e: Exception) {
                throw WalletError(WalletError.SignError.code, "Hashing failed: ${e.message}")
            }
        }

        /**
         * Hash data using SHA2-256
         * @param data Data to hash
         * @return SHA2-256 hash of the data
         */
        fun sha256(data: ByteArray): ByteArray {
            return hash(data, HashingAlgorithm.SHA2_256)
        }

        /**
         * Hash data using SHA3-256
         * @param data Data to hash
         * @return SHA3-256 hash of the data
         */
        fun sha3_256(data: ByteArray): ByteArray {
            return hash(data, HashingAlgorithm.SHA3_256)
        }
    }
} 