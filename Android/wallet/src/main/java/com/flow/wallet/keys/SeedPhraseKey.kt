package com.flow.wallet.keys

import android.util.Log
import com.google.common.io.BaseEncoding
import com.flow.wallet.crypto.ChaChaPolyCipher
import com.flow.wallet.crypto.HasherImpl
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import wallet.core.jni.PrivateKey as TWPrivateKey

@Serializable
private data class KeyData(
    @SerialName("mnemonic")
    val mnemonic: String,
    @SerialName("passphrase")
    val passphrase: String,
    @SerialName("path")
    val path: String
)

/**
 * Concrete implementation of SeedPhraseKeyProvider
 * Manages BIP39 seed phrase based keys using Trust WalletCore
 */
class SeedPhraseKey(
    private val mnemonicString: String,
    private val passphrase: String,
    override val derivationPath: String,
    private var keyPair: KeyPair?,
    override var storage: StorageProtocol
) : SeedPhraseKeyProvider {
    companion object {
        private const val TAG = "SeedPhraseKey"
        private const val DEFAULT_DERIVATION_PATH = "m/44'/539'/0'/0/0"

        fun parseDerivationPath(path: String): IntArray {
            return path.split("/")
                .filter { it.isNotBlank() && it != "m" }
                .map {
                    when {
                        it.endsWith("'") -> it.dropLast(1).toInt() or 0x80000000.toInt()
                        else -> it.toInt()
                    }
                }
                .toIntArray()
        }
    }

    private val hdWallet: HDWallet = try {
        HDWallet(mnemonicString, passphrase)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize HD wallet", e)
        throw WalletError.InitHDWalletFailed
    }

    private val privateKey: PrivateKey = try {
        val twPriv = hdWallet.getKeyByCurve(Curve.SECP256K1, derivationPath)
        PrivateKey(twPriv, storage)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to derive private key", e)
        throw WalletError.InitHDWalletFailed
    }

    override val key: KeyPair
        get() = keyPair ?: throw WalletError.EmptyKey

    override val secret: ByteArray = mnemonicString.toByteArray()
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.SEED_PHRASE
    override val isHardwareBacked: Boolean = false

    override val id: String
        get() = keyPair?.public?.encoded?.let { 
            BaseEncoding.base64().encode(it)
        } ?: java.util.UUID.randomUUID().toString()

    override val mnemonic: List<String> = mnemonicString.split(" ")

    override fun deriveKey(index: Int): KeyProtocol {
        val newPath = derivationPath.replaceAfterLast("/", index.toString())
        val derivedKeyPair = deriveKeyPair(newPath)
        val twPrivateKey = TWPrivateKey(derivedKeyPair.private.encoded)
        return PrivateKey(twPrivateKey, storage)
    }

    private fun deriveKeyPair(path: String): KeyPair {
        try {
            val derivedPrivateKey = hdWallet.getKeyByCurve(Curve.SECP256K1, path)
            val publicKey = derivedPrivateKey.getPublicKeySecp256k1(false)
            
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKeySpec = PKCS8EncodedKeySpec(derivedPrivateKey.data())
            val publicKeySpec = X509EncodedKeySpec(publicKey.data())
            
            return KeyPair(
                keyFactory.generatePublic(publicKeySpec),
                keyFactory.generatePrivate(privateKeySpec)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Key derivation failed", e)
            throw WalletError.InitPrivateKeyFailed
        }
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        val hdWallet = HDWallet(128, "")
        val mnemonic = hdWallet.mnemonic()
        val keyPair = deriveKeyPair(DEFAULT_DERIVATION_PATH)
        return SeedPhraseKey(mnemonic, "", DEFAULT_DERIVATION_PATH, keyPair, storage)
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
        
        val (mnemonic, passphrase, path) = try {
            parseKeyData(decryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse key data", e)
            throw WalletError.InvalidKeyStoreJSON
        }
        
        val keyPair = deriveKeyPair(path)
        return SeedPhraseKey(mnemonic, passphrase, path, keyPair, storage)
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        val mnemonic = String(secret, Charsets.UTF_8)
        val keyPair = deriveKeyPair(DEFAULT_DERIVATION_PATH)
        return SeedPhraseKey(mnemonic, "", DEFAULT_DERIVATION_PATH, keyPair, storage)
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        return keyPair?.public?.encoded
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return keyPair?.private?.encoded
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        val currentKeyPair = keyPair ?: throw WalletError.EmptySignKey
        
        return try {
            val twPrivateKey = TWPrivateKey(currentKeyPair.private.encoded)
            val privateKey = PrivateKey(twPrivateKey, storage)

            privateKey.sign(data, signAlgo, hashAlgo)
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        }
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        if (keyPair == null) return false
        
        return try {
            val publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> privateKey.pk.publicKeyNist256p1
                SigningAlgorithm.ECDSA_secp256k1 -> privateKey.pk.getPublicKeySecp256k1(false)
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
        val keyData = createKeyData()
        val encryptedData = try {
            encryptData(keyData, password)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw WalletError.InitChaChaPolyFailed
        }
        storage.set(id, encryptedData)
    }

    override suspend fun remove(id: String) {
        storage.remove(id)
    }

    override fun allKeys(): List<String> {
        return storage.allKeys
    }

    private fun createKeyData(): ByteArray {
        val data = mapOf(
            "mnemonic" to mnemonicString,
            "passphrase" to passphrase,
            "path" to derivationPath
        )
        return Json.encodeToString(data).toByteArray()
    }

    private fun parseKeyData(data: ByteArray): Triple<String, String, String> {
        val json = String(data, Charsets.UTF_8)
        val keyData = Json.decodeFromString<KeyData>(json)
        return Triple(keyData.mnemonic, keyData.passphrase, keyData.path)
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