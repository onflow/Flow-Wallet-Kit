package com.flow.wallet.keys

import com.google.common.io.BaseEncoding

/**
 * Extension functions for key formatting and conversion
 */

/**
 * Convert public key data to Flow indexer hex format
 * Drops the first byte (format indicator '04')
 * @return Formatted public key string
 */
fun ByteArray.toFlowIndexerHex(): String {
    // Drop the first byte (format indicator '04')
    val raw = copyOfRange(1, size)
    return BaseEncoding.base16().lowerCase().encode(raw)
} 