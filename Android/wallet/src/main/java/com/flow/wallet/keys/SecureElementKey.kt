package com.flow.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.flow.wallet.KeyManager
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyPair
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator

/**
 * Concrete implementation of SecureElementKeyProvider
 * Manages keys stored in the Android Keystore with enhanced security features
 */
class SecureElementKey(
    private val keyPair: KeyPair,
    override var storage: StorageProtocol,
    private val keyProperties: Map<String, Any> = emptyMap()
) : SecureElementKeyProvider {
    companion object {
        private const val TAG = "SecureElementKey"
        private const val KEY_ALGORITHM = "EC"
        private const val KEY_SIZE = 256
        private const val DIGEST = "SHA-256"
        private const val PADDING = "PKCS1"
    }

    override val key: KeyPair = keyPair
    override val secret: ByteArray = ByteArray(0) // No secret material for hardware-backed keys
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.SECURE_ELEMENT
    override val isHardwareBacked: Boolean = true

    override val id: String
        get() = keyPair.public?.encoded?.let {
            java.util.Base64.getEncoder().encodeToString(it)
        } ?: java.util.UUID.randomUUID().toString()

    override fun isSecureElementAvailable(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Secure element not available", e)
            false
        }
    }

    override fun getKeyProperties(): Map<String, Any> = buildMap {
        putAll(keyProperties)
        put("algorithm", keyPair.private.algorithm)
        put("format", keyPair.private.format)
        put("isHardwareBacked", isHardwareBacked)
        put("keySize", KEY_SIZE)
        put("digest", DIGEST)
        put("padding", PADDING)
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        try {
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "secure_element_${System.currentTimeMillis()}",
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(DIGEST)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1) // Require authentication for every use
                .setKeySize(KEY_SIZE)
                .build()

            val keyGenerator = KeyGenerator.getInstance(
                KEY_ALGORITHM,
                "AndroidKeyStore"
            )
            keyGenerator.init(keyGenParameterSpec)
            val keyPair = KeyManager.generateKeyWithPrefix("secure_element")
            return SecureElementKey(keyPair, storage, mapOf(
                "createdAt" to System.currentTimeMillis(),
                "keySize" to KEY_SIZE,
                "algorithm" to KEY_ALGORITHM
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure element key", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override suspend fun create(storage: StorageProtocol): KeyProtocol {
        return create(Unit, storage)
    }

    override suspend fun createAndStore(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        val key = create(storage)
        key.store(id, password)
        return key
    }

    override suspend fun get(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        try {
            val privateKey = KeyManager.getPrivateKeyByPrefix(id) ?: throw WalletError.EmptyKeychain
            val publicKey = KeyManager.getPublicKeyByPrefix(id) ?: throw WalletError.InitPublicKeyFailed
            return SecureElementKey(KeyPair(publicKey, privateKey), storage, mapOf(
                "retrievedAt" to System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get secure element key", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        return try {
            keyPair.public.encoded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            null
        }
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return null // Never expose private key material
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        if (keyPair.private == null) {
            throw WalletError.EmptySignKey
        }
        
        return try {
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initSign(keyPair.private)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        }
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        if (keyPair.public == null) {
            return false
        }
        
        return try {
            val sig = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(keyPair.public)
            sig.update(message)
            sig.verify(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            false
        }
    }

    override suspend fun store(id: String, password: String) {
        // Keys are already stored in Android Keystore
        // Add metadata to storage
        storage.set("${id}_metadata", getKeyProperties().toString().toByteArray())
    }

    override suspend fun remove(id: String) {
        try {
            KeyManager.deleteEntry(id)
            storage.remove("${id}_metadata")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove secure element key", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override fun allKeys(): List<String> {
        return try {
            KeyManager.getAllAliases()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all key aliases", e)
            emptyList()
        }
    }
} 