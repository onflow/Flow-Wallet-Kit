package com.flow.wallet.wallet

import com.flow.wallet.crypto.HasherImpl
import wallet.core.jni.HexCoding
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

fun Ethereum.SigningOutput.txIdHex(): String {
    val bytes = txId()
    if (bytes.isEmpty()) return "0x"
    return "0x${HexCoding.encode(bytes)}"
}

