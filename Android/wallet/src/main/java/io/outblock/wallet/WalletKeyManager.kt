package io.outblock.wallet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.google.common.io.BaseEncoding
import org.bouncycastle.util.BigIntegers
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Enumeration
import javax.security.auth.x500.X500Principal

object KeyManager {
    const val KEYSTORE_ALIAS_PREFIX = "user_keystore_"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")

    private var currentPrefix: String = ""

    init {
        try {
            keyStore.load(null)
        } catch (e: KeyStoreException) {
            Log.e(WALLET_TAG, "Error initializing keystore: $e")
            throw WalletCoreException("Error initializing keystore", e)
        }
    }

    fun getCurrentPrefix(): String {
        return currentPrefix
    }

    fun generateKeyWithPrefix(prefix: String): KeyPair {
        currentPrefix = prefix
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )

            val keyGenSpec = KeyGenParameterSpec.Builder(
                generateAlias(prefix),
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("P-256"))
                .setCertificateSubject(X500Principal("CN=$prefix"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()

            keyPairGenerator.initialize(keyGenSpec)
            return keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            Log.e(WALLET_TAG, "Error generating key pair: $e")
            throw WalletCoreException("Error generating key pair", e)
        }
    }

    fun getPrivateKeyByPrefix(prefix: String): PrivateKey? {
        try {
            val keyEntry = keyStore.getEntry(generateAlias(prefix), null) as? PrivateKeyEntry
            return keyEntry?.privateKey
        } catch (e: Exception) {
            Log.e(WALLET_TAG, "Error getting private key: $e")
            throw WalletCoreException("Error getting private key", e)
        }
    }

    fun getPublicKeyByPrefix(prefix: String): PublicKey? {
        try {
            val keyEntry = keyStore.getEntry(generateAlias(prefix), null) as? PrivateKeyEntry
            return keyEntry?.certificate?.publicKey
        } catch (e: Exception) {
            Log.e(WALLET_TAG, "Error getting public key: $e")
            throw WalletCoreException("Error getting public key", e)
        }
    }

    private fun generateAlias(prefix: String): String {
        return KEYSTORE_ALIAS_PREFIX + prefix
    }

    fun containsAlias(prefix: String): Boolean {
        return try {
            keyStore.containsAlias(generateAlias(prefix))
        } catch (e: KeyStoreException) {
            Log.e(WALLET_TAG, "Error checking alias existence: $e")
            false
        }
    }

    fun getAllAliases(): List<String> {
        return try {
            val aliases: MutableList<String> = ArrayList()
            val enumeration: Enumeration<String> = keyStore.aliases()
            while (enumeration.hasMoreElements()) {
                aliases.add(enumeration.nextElement())
            }
            aliases
        } catch (e: KeyStoreException) {
            Log.e(WALLET_TAG, "Error getting all aliases: $e")
            emptyList()
        }
    }

    fun deleteEntry(prefix: String): Boolean {
        try {
            val alias = generateAlias(prefix)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                return true
            }
            return false
        } catch (e: KeyStoreException) {
            Log.e(WALLET_TAG, "Error deleting entry: $e")
            return false
        }
    }

    fun clearAllEntries() {
        try {
            val aliases = getAllAliases()
            for (alias in aliases) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: KeyStoreException) {
            Log.e(WALLET_TAG, "Error clearing all entries: $e")
            throw WalletCoreException("Error clearing all entries", e)
        }
    }
}

fun PublicKey?.toFormatString(): String {
    return (this as? ECPublicKey)?.w?.let {
        val bytes =
            BigIntegers.asUnsignedByteArray(it.affineX) + BigIntegers.asUnsignedByteArray(it.affineY)
        BaseEncoding.base16().lowerCase().encode(bytes)
    } ?: ""
}

