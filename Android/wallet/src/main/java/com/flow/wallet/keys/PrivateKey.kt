package com.flow.wallet.keys

import android.util.Log
import com.flow.wallet.crypto.ChaChaPolyCipher
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import wallet.core.jni.Curve
import wallet.core.jni.PrivateKey as TWPrivateKey
/**
 * Implementation of KeyProtocol using raw private keys
 * Uses Trust WalletCore for cryptographic operations with enhanced security
 */
class PrivateKey(
    internal var pk: TWPrivateKey,
    override var storage: StorageProtocol,
    private val keyProperties: Map<String, Any> = emptyMap()
) : KeyProtocol, PrivateKeyProvider {
    companion object {
        private const val TAG = "PrivateKey"
        private const val KEY_SIZE = 256
        private const val KEY_ALGORITHM = "EC"

        /**
         * Create a new private key with storage
         */
        fun create(storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey()
            return PrivateKey(pk, storage, mapOf(
                "createdAt" to System.currentTimeMillis(),
                "keySize" to KEY_SIZE,
                "algorithm" to KEY_ALGORITHM
            ))
        }

        /**
         * Create and store a new private key
         */
        fun createAndStore(id: String, password: String, storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey()
            val cipher = ChaChaPolyCipher(password)
            val encrypted = cipher.encrypt(pk.data())
            storage.set(id, encrypted)
            storage.set("${id}_metadata", mapOf(
                "createdAt" to System.currentTimeMillis(),
                "keySize" to KEY_SIZE,
                "algorithm" to KEY_ALGORITHM
            ).toString().toByteArray())
            return PrivateKey(pk, storage)
        }

        /**
         * Retrieve a stored private key
         */
        fun get(id: String, password: String, storage: StorageProtocol): PrivateKey {
            val encryptedData = storage.get(id) ?: throw WalletError.EmptyKeychain
            val cipher = ChaChaPolyCipher(password)
            val pkData = cipher.decrypt(encryptedData)
            val pk = TWPrivateKey(pkData)
            return PrivateKey(pk, storage, mapOf(
                "retrievedAt" to System.currentTimeMillis()
            ))
        }

        /**
         * Restore a private key from raw data
         */
        fun restore(secret: ByteArray, storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey(secret)
            return PrivateKey(pk, storage, mapOf(
                "restoredAt" to System.currentTimeMillis()
            ))
        }
    }

    override val keyType: KeyType = KeyType.PRIVATE_KEY
    override val isHardwareBacked: Boolean = false
    override val advance: Any = Unit

    override val key: Any
        get() = pk

    override val secret: ByteArray
        get() = pk.data()

    override val id: String
        get() = publicKey(SigningAlgorithm.ECDSA_P256)?.let {
            com.google.common.io.BaseEncoding.base64().encode(it)
        } ?: throw WalletError.InitPrivateKeyFailed

    /**
     * Get key properties
     */
    fun getKeyProperties(): Map<String, Any> {
        return buildMap {
            putAll(keyProperties)
            put("keySize", KEY_SIZE)
            put("algorithm", KEY_ALGORITHM)
            put("isHardwareBacked", isHardwareBacked)
        }
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        return create(storage)
    }

    override suspend fun create(storage: StorageProtocol): KeyProtocol {
        return create(storage)
    }

    override suspend fun createAndStore(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        return createAndStore(id, password, storage)
    }

    override suspend fun get(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        return get(id, password, storage)
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        return restore(secret, storage)
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        val publicKey: wallet.core.jni.PublicKey?
        try {
            publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.getPublicKeyNist256p1().uncompressed()
                SigningAlgorithm.ECDSA_secp256k1 -> pk.getPublicKeySecp256k1(false)
                else -> null
            }
            return publicKey?.data()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            return null
        } finally {
        }
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return pk.data()
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        return try {
            val hashed = HasherImpl.hash(data, hashAlgo)
            val curve = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256      -> Curve.NIST256P1
                SigningAlgorithm.ECDSA_secp256k1 -> Curve.SECP256K1
                else -> throw WalletError.UnsupportedSignatureAlgorithm
            }

            val fullSignature = pk.sign(hashed, curve)

            // Flow blockchain expects 64-byte signatures for all ECDSA algorithms
            // TrustWallet Core sometimes includes a recovery ID byte, making signatures 65 bytes
            // We need to trim this for both ECDSA_P256 and ECDSA_secp256k1
            val sig = if (fullSignature.size == 65) {
                Log.d(TAG, "Trimming recovery ID from 65-byte signature for $signAlgo")
                fullSignature.copyOfRange(0, 64)
            } else {
                Log.d(TAG, "Using signature as-is (${fullSignature.size} bytes) for $signAlgo")
                fullSignature
            }

            sig
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        }
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        val publicKey: wallet.core.jni.PublicKey?
        return try {
            publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.publicKeyNist256p1.uncompressed()
                SigningAlgorithm.ECDSA_secp256k1 -> pk.getPublicKeySecp256k1(false)
                else -> return false
            }
            val hashed = HasherImpl.hash(message, hashAlgo)
            publicKey.verify(hashed, signature)

        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            false
        } finally {
        }
    }

    override suspend fun store(id: String, password: String) {
        val cipher = ChaChaPolyCipher(password)
        val encrypted = cipher.encrypt(pk.data())
        storage.set(id, encrypted)
        storage.set("${id}_metadata", getKeyProperties().toString().toByteArray())
    }

    override suspend fun remove(id: String) {
        storage.remove(id)
        storage.remove("${id}_metadata")
    }

    override fun allKeys(): List<String> {
        return storage.allKeys
    }

    /**
     * Export private key in specified format
     * @param format Export format
     * @return Exported key data
     */
    override fun exportPrivateKey(format: KeyFormat): ByteArray {
        return when (format) {
            KeyFormat.PKCS8 -> pk.data()
            KeyFormat.RAW -> pk.data()
            else -> throw WalletError.UnsupportedKeyFormat
        }
    }

    /**
     * Import private key from data
     * @param data Key data
     * @param format Import format
     */
    override fun importPrivateKey(data: ByteArray, format: KeyFormat) {
        when (format) {
            KeyFormat.PKCS8, KeyFormat.RAW -> {
                if (data.isEmpty()) {
                    throw WalletError.EmptyKey
                }
                try {
                    pk = TWPrivateKey(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import private key", e)
                    throw WalletError.InvalidPrivateKey
                }
            }
            else -> throw WalletError.UnsupportedKeyFormat
        }
    }

    /**
     * Clean up sensitive data
     */
    fun cleanup() {
        pk = TWPrivateKey()
    }
} 