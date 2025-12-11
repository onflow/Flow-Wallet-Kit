package com.flow.wallet.wallet

import com.flow.wallet.crypto.HasherImpl
import com.google.common.io.BaseEncoding
import wallet.core.jni.proto.Ethereum

/**
 * Utilities for extracting transaction hash information from Ethereum signing outputs.
 */
fun Ethereum.SigningOutput.txId(): ByteArray {
    // Transaction hash should be keccak256 of the signed/encoded transaction.
    return HasherImpl.keccak256(encoded.toByteArray())
}
