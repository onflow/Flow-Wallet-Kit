package io.outblock.wallet.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage implementation
 * Uses Android's Keystore for secure storage
 */
class HardwareBackedStorage(context: Context) : StorageProtocol {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val prefs = context.getSharedPreferences("encrypted_data", Context.MODE_PRIVATE)

    override val allKeys: List<String>
        get() = prefs.all.keys.toList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    private fun getOrCreateKey(keyAlias: String): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    override fun get(key: String): ByteArray? {
        val keyAlias = "key_$key"
        if (!keyStore.containsAlias(keyAlias)) {
            return null
        }
        val secretKey = getOrCreateKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val encryptedData = prefs.getString(key, null)?.toByteArray() ?: return null
        val iv = encryptedData.copyOfRange(0, 12)
        val encryptedContent = encryptedData.copyOfRange(12, encryptedData.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encryptedContent)
    }

    override fun set(key: String, data: ByteArray) {
        val keyAlias = "key_$key"
        val secretKey = getOrCreateKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedContent = cipher.doFinal(data)
        val encryptedData = iv + encryptedContent
        prefs.edit()
            .putString(key, encryptedData.toString(Charsets.ISO_8859_1))
            .apply()
    }

    override fun remove(key: String) {
        val keyAlias = "key_$key"
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
        prefs.edit()
            .remove(key)
            .apply()
    }

    override fun removeAll() {
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            keyStore.deleteEntry(aliases.nextElement())
        }
        prefs.edit()
            .clear()
            .apply()
    }

    /**
     * Check if biometric authentication is available on the device
     */
    fun isBiometricAuthAvailable(): Boolean {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "test_biometric_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setUserAuthenticationRequired(true)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the device has a hardware security module
     */
    fun isHardwareSecurityModuleAvailable(): Boolean {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "test_hsm_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setIsStrongBoxBacked(true)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            true
        } catch (e: Exception) {
            false
        }
    }
} 