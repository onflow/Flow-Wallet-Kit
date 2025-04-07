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
 * Hardware-backed storage implementation using Android Keystore
 * Provides the highest level of security by using the device's hardware security module
 */
class HardwareBackedStorage(
    private val context: Context,
    private val keyAlias: String = "hardware_backed_storage_key"
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
                .setUserAuthenticationRequired(false)
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
        get() = context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
            .all.keys.toList()

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    override fun get(key: String): ByteArray? {
        val encryptedData = context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
            .getString(key, null) ?: return null

        return try {
            val (iv, encrypted) = encryptedData.split(":").let {
                it[0].toByteArray() to it[1].toByteArray()
            }
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            null
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
            throw RuntimeException("Failed to encrypt and store data", e)
        }
    }

    override fun remove(key: String) {
        context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
        
        context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    override fun removeAll() {
        context.getSharedPreferences("hardware_backed_data", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    override fun exists(key: String): Boolean {
        return context.getSharedPreferences("hardware_backed_keys", Context.MODE_PRIVATE)
            .contains(key)
    }
} 