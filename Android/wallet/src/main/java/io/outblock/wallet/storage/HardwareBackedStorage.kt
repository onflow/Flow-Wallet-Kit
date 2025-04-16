package io.outblock.wallet.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.outblock.wallet.errors.WalletError
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage implementation using Android Keystore
 * Provides the highest level of security by using the device's hardware security module
 * with support for:
 * - Biometric authentication
 * - Device-specific restrictions
 * - Access group sharing between apps
 */
class HardwareBackedStorage(
    private val context: Context,
    private val keyAlias: String = "hardware_backed_storage_key",
    private val requireBiometricAuth: Boolean = false,
    private val deviceOnly: Boolean = true,
    private val accessGroup: String? = null
) : StorageProtocol {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val secretKey: SecretKey by lazy {
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
                .setUserAuthenticationRequired(requireBiometricAuth)
                .setUserAuthenticationValidityDurationSeconds(if (requireBiometricAuth) 30 else -1)
                .setIsStrongBoxBacked(deviceOnly)
                .setUnlockedDeviceRequired(deviceOnly)
                .apply {
                    if (accessGroup != null) {
                        setAttestationChallenge(accessGroup.toByteArray())
                    }
                }
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } else {
            (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }
    }

    private val cipher: Cipher by lazy {
        Cipher.getInstance("AES/GCM/NoPadding")
    }

    override val securityLevel: SecurityLevel = SecurityLevel.HARDWARE_BACKED

    override val allKeys: List<String>
        get() = try {
            context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
                .all.keys.toList()
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }

    override fun findKey(keyword: String): List<String> {
        return try {
            allKeys.filter { it.contains(keyword, ignoreCase = true) }
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun get(key: String): ByteArray {
        val encryptedData = try {
            context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
                .getString(key, null) ?: throw WalletError.EmptyKeychain
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }

        return try {
            val (iv, encrypted) = encryptedData.split(":").let {
                it[0].toByteArray() to it[1].toByteArray()
            }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override fun set(key: String, value: ByteArray) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value)
            val encryptedData = "${iv.joinToString(":")}:${encrypted.joinToString(":")}"
            
            context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
                .edit()
                .putString(key, encryptedData)
                .apply()
            
            context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
                .edit()
                .putString(key, "")
                .apply()
        } catch (e: Exception) {
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override fun remove(key: String) {
        try {
            context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
            
            context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun removeAll() {
        try {
            context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            
            context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
    }

    override fun exists(key: String): Boolean {
        return try {
            context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
                .contains(key)
        } catch (e: Exception) {
            throw WalletError.LoadCacheFailed
        }
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