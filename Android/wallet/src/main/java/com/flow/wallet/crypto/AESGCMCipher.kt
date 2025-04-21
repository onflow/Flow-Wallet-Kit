package com.flow.wallet.crypto

import android.util.Log
import io.outblock.wallet.errors.WalletError
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of AES-GCM authenticated encryption
 * Provides industry-standard encryption with strong security guarantees
 */
class AESGCMCipher(password: String) : SymmetricEncryption {
    companion object {
        private const val TAG = "AESGCMCipher"
        private const val KEY_SIZE = 32 // 256 bits
        private const val NONCE_SIZE = 12 // 96 bits
        private const val TAG_SIZE = 128 // 128 bits
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val secretKey: SecretKeySpec

    override val key: ByteArray
        get() = secretKey.encoded

    override val keySize: Int = KEY_SIZE * 8 // Convert bytes to bits

    init {
        val keyBytes = deriveKey(password)
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Encrypt data using AES-GCM
     * @param data Data to encrypt
     * @return Encrypted data with authentication tag
     * @throws WalletError if encryption fails
     */
    override fun encrypt(data: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val nonce = generateNonce()
            val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val encrypted = cipher.doFinal(data)
            val combined = ByteBuffer.allocate(NONCE_SIZE + encrypted.size)
                .put(nonce)
                .put(encrypted)
                .array()

            return combined
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw WalletError(WalletError.SignError.code, "Encryption failed: ${e.message}")
        }
    }

    /**
     * Decrypt and authenticate data using AES-GCM
     * @param combinedData Encrypted data with authentication tag
     * @return Original decrypted data
     * @throws WalletError if decryption or authentication fails
     */
    override fun decrypt(combinedData: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(combinedData)
            val nonce = ByteArray(NONCE_SIZE)
            buffer.get(nonce)
            val encrypted = ByteArray(buffer.remaining())
            buffer.get(encrypted)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            return cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw WalletError(WalletError.SignError.code, "Decryption failed: ${e.message}")
        }
    }

    private fun deriveKey(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        java.security.SecureRandom().nextBytes(nonce)
        return nonce
    }
} 