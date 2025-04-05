package io.outblock.wallet

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm
import java.security.Security


class KeyStoreCryptoProvider(private val prefix: String): CryptoProvider {

    override fun getPublicKey(): String {
        return KeyManager.getPublicKeyByPrefix(prefix).toFormatString()
    }

    override suspend fun getUserSignature(jwt: String): String {
        return getSigner().signAsUser(
            jwt.encodeToByteArray()
        ).bytesToHex()
    }

    override suspend fun signData(data: ByteArray): String {
        return getSigner().sign(data).bytesToHex()
    }

    override fun getSigner(): Signer {
        val privateKey = KeyManager.getPrivateKeyByPrefix(prefix)
        // fix: java.security.InvalidKeyException: no encoding for EC private key
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        return WalletCoreSigner(privateKey)
    }

    override fun getHashAlgorithm(): HashingAlgorithm {
        return HashingAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return SigningAlgorithm.ECDSA_P256
    }

    override fun getKeyWeight(): Int {
        return 1000
    }
}