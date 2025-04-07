package io.outblock.wallet.keys

/**
 * Interface for secure element key providers
 * Provides functionality for managing keys stored in hardware security modules
 */
interface SecureElementKeyProvider : KeyProtocol {
    /**
     * Check if secure element is available
     * @return Whether secure element is available
     */
    fun isSecureElementAvailable(): Boolean

    /**
     * Get secure element key properties
     * @return Map of key properties
     */
    fun getKeyProperties(): Map<String, Any>
} 