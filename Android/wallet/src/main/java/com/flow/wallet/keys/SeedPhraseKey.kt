package com.flow.wallet.keys

import android.util.Log
import com.google.common.io.BaseEncoding
import com.flow.wallet.crypto.BIP39
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
    val path: String,
    @SerialName("length")
    val length: BIP39.SeedPhraseLength
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
    override var storage: StorageProtocol,
    private val seedPhraseLength: BIP39.SeedPhraseLength = BIP39.SeedPhraseLength.TWELVE
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

    private fun getCurveForAlgorithm(signAlgo: SigningAlgorithm): Curve {
        return when (signAlgo) {
            SigningAlgorithm.ECDSA_P256 -> Curve.NIST256P1
            SigningAlgorithm.ECDSA_secp256k1 -> Curve.SECP256K1
            else -> throw WalletError.UnsupportedSignatureAlgorithm
        }
    }

    override val key: KeyPair
        get() = throw UnsupportedOperationException("Use publicKey() and privateKey() methods instead")

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

    private fun deriveKeyPair(path: String, signAlgo: SigningAlgorithm = SigningAlgorithm.ECDSA_secp256k1): KeyPair {
        var derivedPrivateKey: wallet.core.jni.PrivateKey? = null
        var publicKey: wallet.core.jni.PublicKey? = null
        try {
            val curve = getCurveForAlgorithm(signAlgo)
            derivedPrivateKey = hdWallet.getKeyByCurve(curve, path)
            publicKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> derivedPrivateKey.getPublicKeyNist256p1()
                SigningAlgorithm.ECDSA_secp256k1 -> derivedPrivateKey.getPublicKeySecp256k1(false)
                else -> throw WalletError.UnsupportedSignatureAlgorithm
            }

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
        } finally {
            // Secure cleanup
            derivedPrivateKey = null
            publicKey = null
        }
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        val length = if (advance is BIP39.SeedPhraseLength) advance else BIP39.SeedPhraseLength.TWELVE
        val mnemonic = BIP39.generate(length)
        // Default to SECP256K1 for backward compatibility
        val keyPair = deriveKeyPair(DEFAULT_DERIVATION_PATH, SigningAlgorithm.ECDSA_secp256k1)
        return SeedPhraseKey(mnemonic, "", DEFAULT_DERIVATION_PATH, keyPair, storage, length)
    }

    override suspend fun create(storage: StorageProtocol): KeyProtocol {
        return create(BIP39.SeedPhraseLength.TWELVE, storage)
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

        val (mnemonic, passphrase, path, length) = try {
            parseKeyData(decryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse key data", e)
            throw WalletError.InvalidKeyStoreJSON
        }

        val keyPair = deriveKeyPair(path)
        return SeedPhraseKey(mnemonic, passphrase, path, keyPair, storage, length)
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        val mnemonic = String(secret, Charsets.UTF_8)
        if (!BIP39.isValid(mnemonic)) {
            throw WalletError.InvalidMnemonic
        }
        val keyPair = deriveKeyPair(DEFAULT_DERIVATION_PATH)
        return SeedPhraseKey(mnemonic, "", DEFAULT_DERIVATION_PATH, keyPair, storage)
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        var twPriv: wallet.core.jni.PrivateKey? = null
        var pubKey: wallet.core.jni.PublicKey? = null
        try {
            val curve = getCurveForAlgorithm(signAlgo)
            twPriv = hdWallet.getKeyByCurve(curve, derivationPath)
            pubKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> twPriv.getPublicKeyNist256p1()
                SigningAlgorithm.ECDSA_secp256k1 -> twPriv.getPublicKeySecp256k1(false)
                else -> null
            }
            return pubKey?.data()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            return null
        } finally {
            // Secure cleanup
            twPriv = null
            pubKey = null
        }
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        var twPriv: wallet.core.jni.PrivateKey? = null
        try {
            val curve = getCurveForAlgorithm(signAlgo)
            twPriv = hdWallet.getKeyByCurve(curve, derivationPath)
            return twPriv.data()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get private key", e)
            return null
        } finally {
            // Secure cleanup
            twPriv = null
        }
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        if (keyPair == null) throw WalletError.EmptySignKey
        
        var twPriv: wallet.core.jni.PrivateKey? = null
        try {
            val curve = getCurveForAlgorithm(signAlgo)
            twPriv = hdWallet.getKeyByCurve(curve, derivationPath)
            val hashed = HasherImpl.hash(data, hashAlgo)
            val fullSignature = twPriv.sign(hashed, curve)

            // WalletCore returns 65-byte signature for SECP256K1 (r||s||v). Drop the recovery byte to
            // comply with Flow's 64-byte requirement.
            return if (signAlgo == SigningAlgorithm.ECDSA_secp256k1 && fullSignature.size == 65) {
                fullSignature.copyOfRange(0, 64)
            } else {
                fullSignature
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed", e)
            throw WalletError.SignError
        } finally {
            // Secure cleanup
            twPriv = null
        }
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        if (keyPair == null) return false
        
        var twPriv: wallet.core.jni.PrivateKey? = null
        var pubKey: wallet.core.jni.PublicKey? = null
        try {
            val curve = getCurveForAlgorithm(signAlgo)
            twPriv = hdWallet.getKeyByCurve(curve, derivationPath)
            pubKey = when (signAlgo) {
                SigningAlgorithm.ECDSA_P256 -> twPriv.getPublicKeyNist256p1()
                SigningAlgorithm.ECDSA_secp256k1 -> twPriv.getPublicKeySecp256k1(false)
                else -> return false
            }
            val hashed = HasherImpl.hash(message, hashAlgo)
            return pubKey.verify(hashed, signature)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            return false
        } finally {
            // Secure cleanup
            twPriv = null
            pubKey = null
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
            "path" to derivationPath,
            "length" to seedPhraseLength
        )
        return Json.encodeToString(data).toByteArray()
    }

    private fun parseKeyData(data: ByteArray): Quadruple<String, String, String, BIP39.SeedPhraseLength> {
        val json = String(data, Charsets.UTF_8)
        val keyData = Json.decodeFromString<KeyData>(json)
        return Quadruple(keyData.mnemonic, keyData.passphrase, keyData.path, keyData.length)
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

// Helper class for returning four values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
) 