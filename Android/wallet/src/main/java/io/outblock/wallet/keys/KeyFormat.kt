package io.outblock.wallet.keys

/**
 * Enum representing key export/import formats
 */
enum class KeyFormat {
    /**
     * Raw bytes
     */
    RAW,

    /**
     * Base64 encoded
     */
    BASE64,

    /**
     * Hex string
     */
    HEX,

    /**
     * JSON keystore
     */
    KEYSTORE
} 