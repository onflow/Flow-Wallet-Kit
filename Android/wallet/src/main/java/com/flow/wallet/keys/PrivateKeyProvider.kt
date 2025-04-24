package com.flow.wallet.keys

/**
 * Interface for private key providers
 * Provides functionality for managing raw private keys
 */
interface PrivateKeyProvider : KeyProtocol {
    /**
     * Export private key in specified format
     * @param format Export format
     * @return Exported key data
     */
    fun exportPrivateKey(format: KeyFormat): ByteArray

    /**
     * Import private key from data
     * @param data Key data
     * @param format Import format
     */
    fun importPrivateKey(data: ByteArray, format: KeyFormat)
} 