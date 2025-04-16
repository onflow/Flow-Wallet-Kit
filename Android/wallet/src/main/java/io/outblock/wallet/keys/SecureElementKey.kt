package io.outblock.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.outblock.wallet.WalletKeyManager
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * Concrete implementation of SecureElementKeyProvider
 * Manages keys stored in the Android Keystore
 */
class SecureElementKey(
    private val keyPair: KeyPair,
    override var storage: StorageProtocol
) : SecureElementKeyProvider {
    companion object {
        private const val TAG = "SecureElementKey"
    }

    override val key: KeyPair = keyPair
    override val secret: ByteArray = ByteArray(0) // No secret material for hardware-backed keys
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.SECURE_ELEMENT
    override val isHardwareBacked: Boolean = true

    override val id: String
        get() = (keyPair.public as? PublicKey)?.encoded?.let { 
            java.util.Base64.getEncoder().encodeToString(it)
        } ?: java.util.UUID.randomUUID().toString()

    override fun isSecureElementAvailable(): Boolean = true

    override fun getKeyProperties(): Map<String, Any> = mapOf(
        "algorithm" to keyPair.private.algorithm,
        "format" to keyPair.private.format,
        "isHardwareBacked" to isHardwareBacked
    )

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        try {
            val keyManager = WalletKeyManager()
            val keyPair = keyManager.generateKeyWithPrefix("secure_element")
            return SecureElementKey(keyPair, storage)
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
            val keyManager = WalletKeyManager()
            val privateKey = keyManager.getPrivateKeyByPrefix(id) ?: throw WalletError.EmptyKeychain
            val publicKey = keyManager.getPublicKeyByPrefix(id) ?: throw WalletError.InitPublicKeyFailed
            return SecureElementKey(KeyPair(publicKey, privateKey), storage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get secure element key", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        return keyPair.public.encoded
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

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm): Boolean {
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
    }

    override suspend fun remove(id: String) {
        try {
            val keyManager = WalletKeyManager()
            keyManager.removeKey(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove secure element key", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override fun allKeys(): List<String> {
        try {
            val keyManager = WalletKeyManager()
            return keyManager.getAllKeyAliases()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all key aliases", e)
            return emptyList()
        }
    }
} 