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

        private val SECP256K1_N = java.math.BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        private val P256_N       = java.math.BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

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

        /**
         * Convert signature to canonical low-s form if required by Flow.
         */
        private fun ensureLowS(sig: ByteArray, algo: SigningAlgorithm): ByteArray {
            if (sig.size != 64) return sig

            val r = java.math.BigInteger(1, sig.copyOfRange(0, 32))
            var s = java.math.BigInteger(1, sig.copyOfRange(32, 64))

            val n = when (algo) {
                SigningAlgorithm.ECDSA_secp256k1 -> SECP256K1_N
                SigningAlgorithm.ECDSA_P256      -> P256_N
                else -> return sig
            }

            // if s > n/2, use n - s
            if (s > n.shiftRight(1)) {
                s = n.subtract(s)
                // rebuild signature bytes
                val rBytes = r.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
                val sBytes = s.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
                val out = ByteArray(64)
                System.arraycopy(ByteArray(32 - rBytes.size), 0, out, 0, 32 - rBytes.size)
                System.arraycopy(rBytes, 0, out, 32 - rBytes.size, rBytes.size)
                System.arraycopy(ByteArray(32 - sBytes.size), 0, out, 32, 32 - sBytes.size)
                System.arraycopy(sBytes, 0, out, 64 - sBytes.size, sBytes.size)
                return out
            }
            return sig
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

            // 1) Trim recovery-id if present for SECP256K1
            var sig = if (signAlgo == SigningAlgorithm.ECDSA_secp256k1 && fullSignature.size == 65) {
                fullSignature.copyOfRange(0, 64)
            } else {
                fullSignature
            }

            // 2) Enforce canonical (low-s) form – Flow rejects high-s signatures
            sig = ensureLowS(sig, signAlgo)
            sig
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        }
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        var publicKey: wallet.core.jni.PublicKey?
        return try {
            publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.getPublicKeyNist256p1().uncompressed()
                SigningAlgorithm.ECDSA_secp256k1 -> pk.getPublicKeySecp256k1(false)
                else -> return false
            }
            val hashed = HasherImpl.hash(message, hashAlgo)
            when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> publicKey.verify(hashed, signature)
                SigningAlgorithm.ECDSA_secp256k1 -> publicKey.verify(hashed, signature)
                else -> false
            }
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
                    val newPk = TWPrivateKey(data)
                    pk.data().copyInto(newPk.data())
                    pk = newPk
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