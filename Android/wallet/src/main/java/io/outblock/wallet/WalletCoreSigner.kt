package io.outblock.wallet

import android.util.Log
import com.nftco.flow.sdk.Hasher
import com.nftco.flow.sdk.Signer
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.onflow.flow.models.HashingAlgorithm
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature


class WalletCoreSigner(
    private val privateKey: PrivateKey?,
    private val hashAlgo: HashingAlgorithm = HashingAlgorithm.SHA2_256,
    override val hasher: Hasher = HasherImpl(hashAlgo)
) : Signer {
    override fun sign(bytes: ByteArray): ByteArray {
        try {
            if (privateKey == null) {
                throw WalletCoreException("Error getting private key", null)
            }
            val signature = Signature.getInstance(hashAlgo.value)
            signature.initSign(privateKey)
            signature.update(bytes)
            val asn1Signature = signature.sign()
            val seq = ASN1Sequence.getInstance(asn1Signature)
            val r = (seq.getObjectAt(0) as ASN1Integer).value.toByteArray()
            val s = (seq.getObjectAt(1) as ASN1Integer).value.toByteArray()
            return (r.takeLast(32) + s.takeLast(32)).toByteArray()
        } catch (e: Exception) {
            Log.e(WALLET_TAG, "Error while signing data: $e")
            throw WalletCoreException("Error signing data", e)
        }
    }
}

internal class HasherImpl(
    private val hashAlgo: HashingAlgorithm
) : Hasher {

    override fun hash(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(hashAlgo.value)
        return digest.digest(bytes)
    }
}
