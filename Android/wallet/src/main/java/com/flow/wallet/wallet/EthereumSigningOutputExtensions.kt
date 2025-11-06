package com.flow.wallet.wallet

import com.flow.wallet.crypto.HasherImpl
import wallet.core.jni.proto.Ethereum

/**
 * Utilities for extracting transaction hash information from Ethereum signing outputs.
 */
fun Ethereum.SigningOutput.txId(): ByteArray {
    val existing = preHash.toByteArray()
    if (existing.isNotEmpty()) {
        return existing
    }
    val computed = HasherImpl.keccak256(encoded.toByteArray())
    return computed
}
