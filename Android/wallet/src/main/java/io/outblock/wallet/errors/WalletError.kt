package io.outblock.wallet.errors

import java.lang.RuntimeException

/**
 * Represents all possible errors that can occur in FlowWalletKit operations
 */
class WalletError(
    val code: Int,
    override val message: String
) : RuntimeException(message) {

    override fun toString(): String {
        return "WalletError Code: $code-$message"
    }

    companion object {
        // MARK: - General Errors
        val NoImplement = WalletError(0, "Operation or feature not implemented")
        val EmptyKeychain = WalletError(1, "No keys found in keychain")
        val EmptyKey = WalletError(2, "Key data is empty or invalid")
        val EmptySignKey = WalletError(3, "Signing key is empty or not available")

        // MARK: - Cryptographic Errors
        val UnsupportedHashAlgorithm = WalletError(4, "Hash algorithm not supported")
        val UnsupportedSignatureAlgorithm = WalletError(5, "Signature algorithm not supported")
        val InitChaChaPolyFailed = WalletError(6, "Failed to initialize ChaCha20-Poly1305")
        val InitHDWalletFailed = WalletError(7, "Failed to initialize HD wallet")
        val InitPrivateKeyFailed = WalletError(8, "Failed to initialize private key")
        val RestoreWalletFailed = WalletError(9, "Failed to restore wallet from backup")
        val InvalidSignatureAlgorithm = WalletError(10, "Invalid signature algorithm specified")
        val InvalidEVMAddress = WalletError(11, "Invalid EVM address")

        // MARK: - Authentication Errors
        val InvalidPassword = WalletError(12, "Invalid password provided")
        val InvalidPrivateKey = WalletError(13, "Invalid private key format")
        val InvalidKeyStoreJSON = WalletError(14, "Invalid KeyStore JSON format")
        val InvalidKeyStorePassword = WalletError(15, "Invalid KeyStore password")
        val SignError = WalletError(16, "Error during signing operation")
        val InitPublicKeyFailed = WalletError(17, "Failed to initialize public key")

        // MARK: - Network Errors
        val IncorrectKeyIndexerURL = WalletError(18, "Invalid key indexer URL")
        val KeyIndexerRequestFailed = WalletError(19, "Key indexer request failed")
        val DecodeKeyIndexerFailed = WalletError(20, "Failed to decode key indexer response")

        // MARK: - Storage Errors
        val LoadCacheFailed = WalletError(21, "Failed to load data from cache")
        val InvalidWalletType = WalletError(22, "Invalid wallet type for operation")

        // MARK: - Connection Errors
        val InvalidConnectionType = WalletError(23, "Invalid connection type")
        val ConnectionFailed = WalletError(24, "Failed to establish connection")
        val DisconnectionFailed = WalletError(25, "Failed to disconnect")
        val InvalidDeepLink = WalletError(26, "Invalid deep link format")
        val SessionExpired = WalletError(27, "Connection session expired")
        val InvalidSession = WalletError(28, "Invalid connection session")
        val NetworkNotSupported = WalletError(29, "Network not supported")
        val ConnectionTimeout = WalletError(30, "Connection attempt timed out")

        fun fromCode(code: Int): WalletError {
            return when (code) {
                0 -> NoImplement
                1 -> EmptyKeychain
                2 -> EmptyKey
                3 -> EmptySignKey
                4 -> UnsupportedHashAlgorithm
                5 -> UnsupportedSignatureAlgorithm
                6 -> InitChaChaPolyFailed
                7 -> InitHDWalletFailed
                8 -> InitPrivateKeyFailed
                9 -> RestoreWalletFailed
                10 -> InvalidSignatureAlgorithm
                11 -> InvalidEVMAddress
                12 -> InvalidPassword
                13 -> InvalidPrivateKey
                14 -> InvalidKeyStoreJSON
                15 -> InvalidKeyStorePassword
                16 -> SignError
                17 -> InitPublicKeyFailed
                18 -> IncorrectKeyIndexerURL
                19 -> KeyIndexerRequestFailed
                20 -> DecodeKeyIndexerFailed
                21 -> LoadCacheFailed
                22 -> InvalidWalletType
                23 -> InvalidConnectionType
                24 -> ConnectionFailed
                25 -> DisconnectionFailed
                26 -> InvalidDeepLink
                27 -> SessionExpired
                28 -> InvalidSession
                29 -> NetworkNotSupported
                30 -> ConnectionTimeout
                else -> throw IllegalArgumentException("Unknown error code: $code")
            }
        }
    }
} 