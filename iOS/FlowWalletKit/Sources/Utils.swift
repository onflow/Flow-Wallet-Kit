//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 29/3/2025.
//

/// FlowWalletKit - Utility Extensions for WalletCore Integration
///
/// This module provides utility extensions for WalletCore's HDWallet class,
/// enabling seamless integration with Flow blockchain's cryptographic requirements.
/// It supports key derivation and conversion for different signature algorithms.

import WalletCore
import Flow

/// Extension to HDWallet for Flow-specific key operations
public extension HDWallet {
    
    /// Derives a private key for a specific Flow signature algorithm
    /// - Parameter signAlgo: The Flow signature algorithm (ECDSA_P256 or ECDSA_SECP256k1)
    /// - Returns: A WalletCore.PrivateKey instance if the algorithm is supported, nil otherwise
    ///
    /// This method safely handles private key derivation by:
    /// - Using the appropriate curve based on the signature algorithm
    /// - Applying the standard derivation path
    /// - Ensuring proper cleanup of sensitive data using defer
    ///
    /// Example:
    /// ```swift
    /// let wallet = HDWallet(...)
    /// if let privateKey = wallet.getPrivateKey(signAlgo: .ECDSA_P256) {
    ///     // Use private key for signing
    /// }
    /// ```
    func getPrivateKey(signAlgo: Flow.SignatureAlgorithm) -> WalletCore.PrivateKey? {
        switch signAlgo {
        case .ECDSA_P256:
            var pk = getKeyByCurve(curve: .nist256p1, derivationPath: SeedPhraseKey.derivationPath)
            defer { pk = WalletCore.PrivateKey() }  // Secure cleanup
            return pk
        case .ECDSA_SECP256k1:
            var pk = getKeyByCurve(curve: .secp256k1, derivationPath: SeedPhraseKey.derivationPath)
            defer { pk = WalletCore.PrivateKey() }  // Secure cleanup
            return pk
        default:
            return nil
        }
    }
    
    /// Derives a public key for a specific Flow signature algorithm
    /// - Parameter signAlgo: The Flow signature algorithm (ECDSA_P256 or ECDSA_SECP256k1)
    /// - Returns: A WalletCore.PublicKey instance if the algorithm is supported, nil otherwise
    ///
    /// This method:
    /// - Derives the private key for the specified algorithm
    /// - Converts it to the corresponding public key format
    /// - Handles cleanup of sensitive private key data
    /// - For SECP256k1, returns uncompressed public keys as required by Flow
    ///
    /// Example:
    /// ```swift
    /// let wallet = HDWallet(...)
    /// if let publicKey = wallet.getPublicKey(signAlgo: .ECDSA_P256) {
    ///     // Use public key for account creation or verification
    /// }
    /// ```
    func getPublicKey(signAlgo: Flow.SignatureAlgorithm) -> WalletCore.PublicKey? {
        guard var pk = getPrivateKey(signAlgo: signAlgo) else { return nil }
        defer { pk = WalletCore.PrivateKey() }  // Secure cleanup
        switch signAlgo {
        case .ECDSA_P256:
            return pk.getPublicKeyNist256p1().uncompressed
        case .ECDSA_SECP256k1:
            return pk.getPublicKeySecp256k1(compressed: false)  // Flow requires uncompressed keys
        default:
            return nil
        }
    }
}

// MARK: - PublicKey Extensions

public extension WalletCore.PublicKey {
    /// Format public key by removing the '04' prefix from uncompressed format
    ///
    /// In elliptic curve cryptography, uncompressed public keys typically start
    /// with '04' to indicate the format. Flow expects the key without this prefix.
    ///
    /// Example:
    /// ```swift
    /// let formattedKey = publicKey.format() // Note: typo preserved for compatibility
    /// // Input:  "04a1b2c3..."
    /// // Output: "a1b2c3..."
    /// ```
    ///
    /// - Returns: Formatted public key string without the '04' prefix
    func format() -> String {
        uncompressed.data.hexValue.dropPrefix("04")
    }
}

extension Array where Element == Flow.AccountKey {
    var hasFullWeightKey: Bool {
        filter{ !$0.revoked }.compactMap{ $0.weight }.reduce(0, +) > Wallet.fullWeightThreshold
    }
    
    var hasSignleFullWeightKey: Bool {
        filter{ !$0.revoked && $0.weight >= Wallet.fullWeightThreshold }.count > 0
    }
}
