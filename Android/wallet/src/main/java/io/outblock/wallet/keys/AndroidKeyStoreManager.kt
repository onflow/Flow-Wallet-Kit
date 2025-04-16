package io.outblock.wallet.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.outblock.wallet.errors.WalletError
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * Manages keys in the Android Keystore directly
 */
class AndroidKeyStoreManager {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    fun generateKeyPair(alias: String): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_ECDSA_P256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(keyGenParameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    fun getKeyPair(alias: String): KeyPair? {
        if (!keyStore.containsAlias(alias)) {
            return null
        }

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
        val publicKey = keyStore.getCertificate(alias)?.publicKey as? PublicKey

        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    fun removeKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    fun getAllAliases(): List<String> {
        return keyStore.aliases().toList()
    }

    fun containsAlias(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }
} 