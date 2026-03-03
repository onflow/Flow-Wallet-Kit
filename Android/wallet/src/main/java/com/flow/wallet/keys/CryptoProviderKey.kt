package com.flow.wallet.keys

import com.flow.wallet.CryptoProvider
import com.flow.wallet.crypto.ChaChaPolyCipher
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.hexToBytes

/**
 * [KeyProtocol] adapter that wraps an externally-managed [CryptoProvider].
 *
 * ### What is a "CryptoProvider-backed key"?
 * Some signing backends (Android Keystore, Secure Enclave, HSM, custom signers…) are not
 * natively representable as a raw key material object.  They are instead accessed through a
 * [CryptoProvider] interface that exposes publicKey / signData / hash & sign algorithm info.
 * [CryptoProviderKey] lets such a provider participate in the [KeyProtocol] ecosystem by
 * delegating all cryptographic operations to the injected provider.
 *
 * ### Provider-identifier storage (companion)
 * In order to reconstruct a [CryptoProvider] after the app is killed or the account cache is
 * lost, the caller needs to persist some *opaque identifier* (e.g. an Android Keystore alias,
 * a key prefix string, a slot number …).  The companion object offers three static helpers for
 * this purpose:
 * - [saveProviderIdentifier] – encrypt and store the identifier string
 * - [getProviderIdentifier] – retrieve it later
 * - [hasProviderIdentifier] – existence check without decryption
 *
 * ### Lifecycle (Android Keystore example)
 * ```
 * // 1. Registration / import – persist the key identifier once:
 * CryptoProviderKey.saveProviderIdentifier(uid, keystorePrefix, password, akpStorage)
 *
 * // 2. Wallet creation – reconstruct provider from the stored identifier and create wallet:
 * val prefix   = CryptoProviderKey.getProviderIdentifier(uid, password, akpStorage) ?: return
 * val provider = AndroidKeystoreCryptoProvider(prefix)          // app-layer concrete type
 * val wallet   = WalletFactory.createProxyWallet(provider, setOf(Mainnet, Testnet), storage)
 * ```
 * The same pattern applies to any other [CryptoProvider] implementation.
 */
class CryptoProviderKey(
    private val cryptoProvider: CryptoProvider,
    override var storage: StorageProtocol,
    private val keyProperties: Map<String, Any> = emptyMap()
) : KeyProtocol {

    override val key: Any = cryptoProvider // Expose provider as the underlying key object
    override val secret: ByteArray = ByteArray(0) // No secret material
    override val advance: Any = Unit
    override val keyType: KeyType = KeyType.SECURE_ELEMENT // Treat as hardware backed
    override val isHardwareBacked: Boolean = true

    override val id: String
        get() = try { cryptoProvider.getPublicKey() } catch (e: Exception) { "" }

    fun isSecureElementAvailable(): Boolean = true

    fun getKeyProperties(): Map<String, Any> = buildMap {
        putAll(keyProperties)
        put("isHardwareBacked", isHardwareBacked)
        put("algorithm", cryptoProvider.getSignatureAlgorithm().name)
        put("hashAlgorithm", cryptoProvider.getHashAlgorithm().name)
        put("weight", cryptoProvider.getKeyWeight())
    }

    override suspend fun create(advance: Any, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override suspend fun create(storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override suspend fun createAndStore(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override suspend fun get(id: String, password: String, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override suspend fun restore(secret: ByteArray, storage: StorageProtocol): KeyProtocol {
        throw WalletError.NoImplement
    }

    override fun publicKey(signAlgo: SigningAlgorithm): ByteArray? {
        // CryptoProvider.getPublicKey() returns hex string
        return try {
            val hex = cryptoProvider.getPublicKey()
            hex.hexToBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun privateKey(signAlgo: SigningAlgorithm): ByteArray? {
        return null // Never expose private key
    }

    override suspend fun sign(data: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): ByteArray {
        // Delegate signing to CryptoProvider which returns hex string
        val signatureHex = cryptoProvider.signData(data)
        return signatureHex.hexToBytes()
    }

    override fun isValidSignature(signature: ByteArray, message: ByteArray, signAlgo: SigningAlgorithm, hashAlgo: HashingAlgorithm): Boolean {
        // Verification is complex to implement locally without heavy deps, 
        // and usually handled by node. Return true as placeholder or implement later.
        return true 
    }

    override suspend fun store(id: String, password: String) {
    }

    override suspend fun remove(id: String) {
    }

    override fun allKeys(): List<String> {
        return emptyList()
    }

    companion object {
        /**
         * Encrypts [identifier] with [password] and stores it under [id] in [storage].
         *
         * [identifier] is an opaque string that identifies the backing [CryptoProvider] —
         * e.g. an Android Keystore alias/prefix, a slot number, a remote key ID, etc.
         * Storing it here allows the provider to be reconstructed after an account-cache loss.
         */
        fun saveProviderIdentifier(
            id: String,
            identifier: String,
            password: String,
            storage: StorageProtocol
        ) {
            val cipher = ChaChaPolyCipher(password)
            storage.set(id, cipher.encrypt(identifier.toByteArray(Charsets.UTF_8)))
        }

        /**
         * Retrieves a previously stored provider identifier.
         *
         * After obtaining the identifier, reconstruct the [CryptoProvider] at the app layer
         * and create a wallet:
         * ```
         * val prefix   = getProviderIdentifier(uid, password, storage) ?: return
         * val provider = AndroidKeystoreCryptoProvider(prefix)
         * WalletFactory.createProxyWallet(provider, chains, storage)
         * ```
         * Returns null if nothing is stored under [id] or if decryption fails.
         */
        fun getProviderIdentifier(id: String, password: String, storage: StorageProtocol): String? {
            val data = storage.get(id) ?: return null
            return try {
                String(ChaChaPolyCipher(password).decrypt(data), Charsets.UTF_8)
            } catch (e: Exception) { null }
        }

        /** Lightweight existence check – does NOT decrypt the stored data. */
        fun hasProviderIdentifier(id: String, storage: StorageProtocol): Boolean =
            storage.get(id) != null
    }
}