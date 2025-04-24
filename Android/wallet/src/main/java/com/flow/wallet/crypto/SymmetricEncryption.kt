package com.flow.wallet.crypto

/**
 * Protocol defining symmetric encryption operations
 * Provides a unified interface for different symmetric encryption algorithms
 */
interface SymmetricEncryption {
    /**
     * Symmetric key used for encryption/decryption
     */
    val key: ByteArray

    /**
     * Size of the symmetric key in bits
     */
    val keySize: Int

    /**
     * Encrypt data using the symmetric key
     * @param data Data to encrypt
     * @return Encrypted data with authentication tag
     */
    fun encrypt(data: ByteArray): ByteArray

    /**
     * Decrypt data using the symmetric key
     * @param combinedData Encrypted data with authentication tag
     * @return Original decrypted data
     */
    fun decrypt(combinedData: ByteArray): ByteArray
} 