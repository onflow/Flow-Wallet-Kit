package com.flow.wallet.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.flow.wallet.errors.WalletError
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage implementation
 * Uses Android's Keystore for secure storage with fallback for devices without hardware support
 */
class HardwareBackedStorage(context: Context) : StorageProtocol {
    companion object {
        private const val TAG = "HardwareBackedStorage"
    }
    
    private val keyStore: KeyStore
    private val prefs = context.getSharedPreferences("encrypted_data", Context.MODE_PRIVATE)
    private val fallbackStorage: StorageProtocol
    private val isHardwareSupported: Boolean

    init {
        // Check if hardware-backed storage is available
        val (hardwareSupported, keyStoreInstance) = initializeKeyStore()
        isHardwareSupported = hardwareSupported
        keyStore = keyStoreInstance
        
        // Create fallback storage for devices without hardware support
        fallbackStorage = FileSystemStorage(context.filesDir)
        
        if (!isHardwareSupported) {
            Log.w(TAG, "Hardware-backed storage not available, using fallback storage")
        } else {
            Log.d(TAG, "Hardware-backed storage initialized successfully")
        }
    }
    
    private fun initializeKeyStore(): Pair<Boolean, KeyStore> {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            // Test if we can actually create a key to verify hardware support
            testKeyGeneration()
            
            true to keyStore
        } catch (e: Exception) {
            Log.w(TAG, "Hardware keystore not available: ${e.message}")
            // Return a dummy keystore that won't be used
            false to KeyStore.getInstance("AndroidKeyStore").apply { 
                try { load(null) } catch (ignored: Exception) { }
            }
        }
    }
    
    private fun testKeyGeneration() {
        val testAlias = "test_key_${System.currentTimeMillis()}"
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                testAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            
            // Clean up test key
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(testAlias)) {
                    deleteEntry(testAlias)
                }
            }
        } catch (e: Exception) {
            throw WalletError(WalletError.LoadCacheFailed.code, "Hardware key generation test failed: ${e.message}")
        }
    }

    override val allKeys: List<String>
        get() = if (isHardwareSupported) {
            prefs.all.keys.toList()
        } else {
            fallbackStorage.allKeys
        }

    override fun findKey(keyword: String): List<String> {
        return allKeys.filter { it.contains(keyword, ignoreCase = true) }
    }

    private fun getOrCreateKey(keyAlias: String): SecretKey? {
        if (!isHardwareSupported) return null
        
        return try {
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
            keyStore.getKey(keyAlias, null) as SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create key: ${e.message}")
            null
        }
    }

    override fun get(key: String): ByteArray? {
        if (!isHardwareSupported) {
            return fallbackStorage.get(key)
        }
        
        return try {
            val keyAlias = "key_$key"
            if (!keyStore.containsAlias(keyAlias)) {
                return null
            }
            val secretKey = getOrCreateKey(keyAlias) ?: return fallbackStorage.get(key)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val encryptedData = prefs.getString(key, null)?.toByteArray() ?: return null
            val iv = encryptedData.copyOfRange(0, 12)
            val encryptedContent = encryptedData.copyOfRange(12, encryptedData.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encryptedContent)
        } catch (e: Exception) {
            Log.w(TAG, "Hardware decryption failed, trying fallback: ${e.message}")
            fallbackStorage.get(key)
        }
    }

    override fun set(key: String, data: ByteArray) {
        if (!isHardwareSupported) {
            fallbackStorage.set(key, data)
            return
        }
        
        try {
            val keyAlias = "key_$key"
            val secretKey = getOrCreateKey(keyAlias)
            if (secretKey == null) {
                Log.w(TAG, "Cannot create hardware key, using fallback storage")
                fallbackStorage.set(key, data)
                return
            }
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedContent = cipher.doFinal(data)
            val encryptedData = iv + encryptedContent
            prefs.edit()
                .putString(key, encryptedData.toString(Charsets.ISO_8859_1))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Hardware encryption failed, using fallback: ${e.message}")
            fallbackStorage.set(key, data)
        }
    }

    override fun remove(key: String) {
        if (!isHardwareSupported) {
            fallbackStorage.remove(key)
            return
        }
        
        try {
            val keyAlias = "key_$key"
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
            prefs.edit()
                .remove(key)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Hardware key removal failed: ${e.message}")
        }
        
        // Also try fallback storage
        fallbackStorage.remove(key)
    }

    override fun removeAll() {
        if (isHardwareSupported) {
            try {
                val aliases = keyStore.aliases()
                while (aliases.hasMoreElements()) {
                    keyStore.deleteEntry(aliases.nextElement())
                }
                prefs.edit()
                    .clear()
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Hardware removeAll failed: ${e.message}")
            }
        }
        
        fallbackStorage.removeAll()
    }

    /**
     * Check if biometric authentication is available on the device
     */
    fun isBiometricAuthenticationAvailable(): Boolean {
        if (!isHardwareSupported) return false
        
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
            Log.d(TAG, "Biometric authentication not available: ${e.message}")
            false
        }
    }

    /**
     * Check if the device has a hardware security module
     */
    fun isHardwareSecurityModuleAvailable(): Boolean {
        if (!isHardwareSupported) return false
        
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
            Log.d(TAG, "Hardware security module not available: ${e.message}")
            false
        }
    }

    override val securityLevel: SecurityLevel
        get() = if (isHardwareSupported) SecurityLevel.HARDWARE_BACKED else SecurityLevel.STANDARD
} 