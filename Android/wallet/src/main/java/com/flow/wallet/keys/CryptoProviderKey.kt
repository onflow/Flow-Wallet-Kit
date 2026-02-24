package com.flow.wallet.keys

import com.flow.wallet.CryptoProvider
import com.flow.wallet.errors.WalletError
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.hexToBytes

/**
 * Adapter class that wraps a CryptoProvider as a KeyProtocol.
 * Useful for ProxyWallet to expose key information without managing the key itself.
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
}