package io.outblock.wallet

import org.onflow.flow.sdk.HashAlgorithm
import org.onflow.flow.sdk.SignatureAlgorithm
import org.onflow.flow.sdk.Signer
import org.onflow.flow.sdk.bytesToHex
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


class KeyStoreCryptoProvider(private val prefix: String): CryptoProvider {

    override fun getPublicKey(): String {
        return KeyManager.getPublicKeyByPrefix(prefix).toFormatString()
    }

    override fun getUserSignature(jwt: String): String {
        return getSigner().signAsUser(
            jwt.encodeToByteArray()
        ).bytesToHex()
    }

    override fun signData(data: ByteArray): String {
        return getSigner().sign(data).bytesToHex()
    }

    override fun getSigner(): Signer {
        val privateKey = KeyManager.getPrivateKeyByPrefix(prefix)
        // fix: java.security.InvalidKeyException: no encoding for EC private key
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        return WalletCoreSigner(privateKey)
    }

    override fun getHashAlgorithm(): HashAlgorithm {
        return HashAlgorithm.SHA2_256
    }

    override fun getSignatureAlgorithm(): SignatureAlgorithm {
        return SignatureAlgorithm.ECDSA_P256
    }

    override fun getKeyWeight(): Int {
        return 1000
    }
}