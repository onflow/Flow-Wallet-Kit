package io.outblock.wallet.keys

import android.util.Log
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import io.outblock.wallet.crypto.ChaChaPolyCipher
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Concrete implementation of PrivateKeyProvider
 * Manages raw private keys
 */
class PrivateKey(
    private var keyPair: KeyPair,
    override var storage: StorageProtocol
) : PrivateKeyProvider {
    companion object {
        private const val TAG = "PrivateKey"
        private const val KEY_ALGORITHM = "EC"
        private const val CURVE_NAME = "secp256k1"
    }

    override val key: KeyPair = keyPair
    override val secret: ByteArray = keyPair.private.encoded
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.PRIVATE_KEY
    override val isHardwareBacked: Boolean = false

    override val id: String
        get() = keyPair.public.encoded.let { 
            java.util.Base64.getEncoder().encodeToString(it)
        }

    override fun exportPrivateKey(format: KeyFormat): ByteArray {
        return try {
            when (format) {
                KeyFormat.PKCS8 -> keyPair.private.encoded
                KeyFormat.RAW -> {
                    val spec = keyPair.private.encoded
                    val pkcs8Spec = PKCS8EncodedKeySpec(spec)
                    val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
                    val privateKey = keyFactory.generatePrivate(pkcs8Spec)
                    privateKey.encoded
                }
                KeyFormat.BASE64 -> keyPair.private.encoded.let { 
                    java.util.Base64.getEncoder().encodeToString(it).toByteArray(Charsets.UTF_8)
                }
                KeyFormat.HEX -> keyPair.private.encoded.let {
                    it.joinToString("") { byte -> "%02x".format(byte) }.toByteArray(Charsets.UTF_8)
                }
                KeyFormat.KEYSTORE -> throw WalletError.InvalidPrivateKey
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export private key", e)
            throw WalletError.InvalidPrivateKey
        }
    }

    override fun importPrivateKey(data: ByteArray, format: KeyFormat) {
        try {
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val privateKey = when (format) {
                KeyFormat.PKCS8 -> keyFactory.generatePrivate(PKCS8EncodedKeySpec(data))
                KeyFormat.RAW -> {
                    val pkcs8Spec = PKCS8EncodedKeySpec(data)
                    keyFactory.generatePrivate(pkcs8Spec)
                }
                KeyFormat.BASE64 -> {
                    val decoded = java.util.Base64.getDecoder().decode(String(data, Charsets.UTF_8))
                    keyFactory.generatePrivate(PKCS8EncodedKeySpec(decoded))
                }
                KeyFormat.HEX -> {
                    val hexString = String(data, Charsets.UTF_8)
                    val bytes = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    keyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
                }
                KeyFormat.KEYSTORE -> throw WalletError.InvalidPrivateKey
            }
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(keyPair.public.encoded))
            keyPair = KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import private key", e)
            throw WalletError.InvalidPrivateKey
        }
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
            val ecSpec = ECGenParameterSpec(CURVE_NAME)
            keyPairGenerator.initialize(ecSpec)
            return PrivateKey(keyPairGenerator.generateKeyPair(), storage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create key pair", e)
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
        val encryptedData = storage.get(id) ?: throw WalletError.EmptyKeychain
        val decryptedData = try {
            decryptData(encryptedData, password)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw WalletError.InvalidPassword
        }
        
        try {
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(decryptedData))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decryptedData))
            return PrivateKey(KeyPair(publicKey, privateKey), storage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize key pair", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        try {
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(secret))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(secret))
            return PrivateKey(KeyPair(publicKey, privateKey), storage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore key pair", e)
            throw WalletError.RestoreWalletFailed
        }
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        return keyPair.public.encoded
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return keyPair.private.encoded
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
        try {
            val encryptedData = encryptData(keyPair.private.encoded, password)
            storage.set(id, encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw WalletError.InitChaChaPolyFailed
        }
    }

    override suspend fun remove(id: String) {
        storage.remove(id)
    }

    override fun allKeys(): List<String> {
        return storage.allKeys
    }

    private fun encryptData(data: ByteArray, password: String): ByteArray {
        val cipher = ChaChaPolyCipher(password)
        return cipher.encrypt(data)
    }

    private fun decryptData(encryptedData: ByteArray, password: String): ByteArray {
        val cipher = ChaChaPolyCipher(password)
        return cipher.decrypt(encryptedData)
    }
} 