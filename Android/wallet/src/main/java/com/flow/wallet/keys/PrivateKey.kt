package com.flow.wallet.keys

import android.util.Log
import com.flow.wallet.crypto.ChaChaPolyCipher
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import com.trustwallet.wallet.core.CoinType
import com.trustwallet.wallet.core.PrivateKey as TWPrivateKey
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Implementation of KeyProtocol using raw private keys
 * Uses Trust WalletCore for cryptographic operations
 */
class PrivateKey(
    private val pk: TWPrivateKey,
    override var storage: StorageProtocol
) : KeyProtocol {
    companion object {
        private const val TAG = "PrivateKey"

        /**
         * Create a new private key with storage
         */
        fun create(storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey()
            return PrivateKey(pk, storage)
        }

        /**
         * Create and store a new private key
         */
        fun createAndStore(id: String, password: String, storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey()
            val cipher = ChaChaPolyCipher(password)
            val encrypted = cipher.encrypt(pk.data())
            storage.set(id, encrypted)
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
            return PrivateKey(pk, storage)
        }

        /**
         * Restore a private key from raw data
         */
        fun restore(secret: ByteArray, storage: StorageProtocol): PrivateKey {
            val pk = TWPrivateKey(secret)
            return PrivateKey(pk, storage)
        }
    }

    override val keyType: KeyType = KeyType.PRIVATE_KEY
    override val isHardwareBacked: Boolean = false
    override val advance: Any = Unit

    override val key: KeyPair
        get() = try {
            val publicKey = pk.getPublicKeySecp256k1(false)
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKeySpec = PKCS8EncodedKeySpec(pk.data())
            val publicKeySpec = X509EncodedKeySpec(publicKey.data())
            KeyPair(
                keyFactory.generatePublic(publicKeySpec),
                keyFactory.generatePrivate(privateKeySpec)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create KeyPair", e)
            throw WalletError.InitPrivateKeyFailed
        }

    override val secret: ByteArray
        get() = pk.data()

    override val id: String
        get() = key.public.encoded.let { 
            com.google.common.io.BaseEncoding.base64().encode(it)
        }

    override fun deriveKey(index: Int): KeyProtocol {
        throw WalletError.UnsupportedOperation
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
        return try {
            when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.getPublicKeyNist256p1().data()
                SigningAlgorithm.ECDSA_secp256k1 -> pk.getPublicKeySecp256k1(false).data()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            null
        }
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return pk.data()
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        return try {
            val hashed = HasherImpl.hash(data, hashAlgo)
            when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.sign(hashed, CoinType.FLOW)
                SigningAlgorithm.ECDSA_secp256k1 -> pk.sign(hashed, CoinType.FLOW)
                else -> throw WalletError.UnsupportedSignatureAlgorithm
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        }
    }

    fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        return try {
            val publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> pk.getPublicKeyNist256p1()
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
        }
    }

    override suspend fun store(id: String, password: String) {
        val cipher = ChaChaPolyCipher(password)
        val encrypted = cipher.encrypt(pk.data())
        storage.set(id, encrypted)
    }

    override suspend fun remove(id: String) {
        storage.remove(id)
    }

    override fun allKeys(): List<String> {
        return storage.allKeys
    }
} 