package io.outblock.wallet.keys

import android.util.Log
import com.google.common.io.BaseEncoding
import com.trustwallet.wallet.core.CoinType
import com.trustwallet.wallet.core.HDWallet
import com.trustwallet.wallet.core.PrivateKey
import io.outblock.wallet.errors.WalletError
import io.outblock.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey as JavaPrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Concrete implementation of SeedPhraseKeyProvider
 * Manages BIP39 seed phrase based keys using WalletCore
 */
class SeedPhraseKey(
    private val mnemonic: String,
    private val passphrase: String,
    private val derivationPath: String,
    private var keyPair: KeyPair?,
    override var storage: StorageProtocol
) : SeedPhraseKeyProvider {
    companion object {
        private const val TAG = "SeedPhraseKey"
        private const val DEFAULT_DERIVATION_PATH = "m/44'/539'/0'/0/0"
        private const val COIN_TYPE = 539L // Flow coin type
    }

    private val hdWallet: HDWallet = try {
        HDWallet(mnemonic, passphrase)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize HD wallet", e)
        throw WalletError.InitHDWalletFailed
    }

    override val key: KeyPair
        get() = keyPair ?: throw WalletError.EmptyKey

    override val secret: ByteArray = mnemonic.toByteArray()
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.SEED_PHRASE
    override val isHardwareBacked: Boolean = false

    override val id: String
        get() = keyPair?.public?.encoded?.let { 
            BaseEncoding.base64().encode(it)
        } ?: java.util.UUID.randomUUID().toString()

    override val mnemonic: List<String> = mnemonic.split(" ")

    override val derivationPath: String = derivationPath

    override fun deriveKey(index: Int): KeyProtocol {
        val newPath = derivationPath.replaceAfterLast("/", index.toString())
        val derivedKeyPair = deriveKeyPair(newPath)
        return PrivateKey(derivedKeyPair, storage)
    }

    private fun deriveKeyPair(path: String): KeyPair {
        try {
            val privateKey = hdWallet.getKeyForCoin(CoinType.FLOW)
            val publicKey = privateKey.getPublicKeySecp256k1(true)
            
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKeySpec = PKCS8EncodedKeySpec(privateKey.data())
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
        val mnemonic = generateMnemonic()
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
        val mnemonic = secret.toString(Charsets.UTF_8)
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
        if (keyPair?.private == null) {
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
        if (keyPair?.public == null) {
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

    private fun generateMnemonic(): String {
        return hdWallet.mnemonic
    }

    private fun createKeyData(): ByteArray {
        val data = mapOf(
            "mnemonic" to mnemonic,
            "passphrase" to passphrase,
            "derivationPath" to derivationPath
        )
        return com.google.gson.Gson().toJson(data).toByteArray()
    }

    private fun parseKeyData(data: ByteArray): Triple<String, String, String> {
        val json = String(data, Charsets.UTF_8)
        val map = com.google.gson.Gson().fromJson(json, Map::class.java)
        return Triple(
            map["mnemonic"] as String,
            map["passphrase"] as String,
            map["derivationPath"] as String
        )
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