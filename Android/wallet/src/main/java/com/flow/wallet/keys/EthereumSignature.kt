package com.flow.wallet.keys

import com.flow.wallet.errors.WalletError

/**
 * Utilities for normalizing Ethereum signatures and validating message digests.
 * WalletCore returns secp256k1 signatures as [r(32) | s(32) | v(1)] but the recovery
 * identifier `v` may be 0/1. Ethereum RPCs expect 27/28, so we adjust the value here.
 */
object EthereumSignatureUtils {

    fun normalize(signature: ByteArray): ByteArray {
        if (signature.size != 65) {
            throw WalletError.InvalidEthereumSignature
        }
        val normalized = signature.copyOf()
        val vIndex = normalized.lastIndex
        if (normalized[vIndex] < 27) {
            normalized[vIndex] = (normalized[vIndex] + 27).toByte()
        }
        return normalized
    }

    fun validateDigest(digest: ByteArray) {
        if (digest.size != 32) {
            throw WalletError.InvalidEthereumMessage
        }
    }
}
