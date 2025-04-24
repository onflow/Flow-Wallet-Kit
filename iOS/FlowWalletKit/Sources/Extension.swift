//
//  File.swift
//
//
//  Created by Hao Fu on 16/1/2024.
//

/// FlowWalletKit - Extensions
///
/// This module provides extensions to Flow and other types to add functionality
/// specific to FlowWalletKit operations. It includes:
/// - Hash algorithm implementations for Flow
/// - Signature algorithm conversions
/// - Public key formatting utilities
/// - String manipulation helpers
/// - Chain-specific service endpoints
///
/// These extensions bridge the gap between Flow's types and WalletKit's requirements,
/// providing seamless integration with the Flow blockchain.

import CryptoKit
import Flow
import WalletCore

// MARK: - Flow.HashAlgorithm Extensions

extension Flow.HashAlgorithm {
    /// Compute hash of data using the specified algorithm
    ///
    /// Supported algorithms:
    /// - SHA2-256: Standard SHA-2 with 256-bit output
    /// - SHA3-256: Keccak SHA-3 with 256-bit output
    ///
    /// Example:
    /// ```swift
    /// let hash = try Flow.HashAlgorithm.SHA2_256.hash(data: messageData)
    /// ```
    ///
    /// - Parameter data: Data to hash
    /// - Returns: Hashed data (32 bytes)
    /// - Throws: WalletError.unsupportHashAlgorithm if algorithm is not supported
    func hash(data: Data) throws -> Data {
        switch self {
        case .SHA2_256:
            return Hash.sha256(data: data)
        case .SHA3_256:
            return Hash.sha3_256(data: data)
        default:
            throw FWKError.unsupportHashAlgorithm
        }
    }
}

// MARK: - Flow.SignatureAlgorithm Extensions

extension Flow.SignatureAlgorithm {
    /// Convert Flow signature algorithm to WalletCore curve
    ///
    /// Supported conversions:
    /// - ECDSA_P256 → NIST P-256 (secp256r1)
    /// - ECDSA_SECP256k1 → secp256k1
    ///
    /// This property is used internally to map Flow's signature algorithms
    /// to their corresponding elliptic curves in WalletCore.
    var WCCurve: Curve? {
        switch self {
        case .ECDSA_P256:
            return Curve.nist256p1
        case .ECDSA_SECP256k1:
            return Curve.secp256k1
        case .unknown:
            return nil
        }
    }
}

// MARK: - PublicKey Extensions

extension PublicKey {
    /// Format public key by removing the '04' prefix from uncompressed format
    ///
    /// In elliptic curve cryptography, uncompressed public keys typically start
    /// with '04' to indicate the format. Flow expects the key without this prefix.
    ///
    /// Example:
    /// ```swift
    /// let formattedKey = publicKey.fromat() // Note: typo preserved for compatibility
    /// // Input:  "04a1b2c3..."
    /// // Output: "a1b2c3..."
    /// ```
    ///
    /// - Returns: Formatted public key string without the '04' prefix
    func fromat() -> String {
        uncompressed.data.hexValue.dropPrefix("04")
    }
}

// MARK: - String Extensions

extension String {
    /// Remove prefix from string if it exists
    ///
    /// This is a safe operation - if the prefix doesn't exist,
    /// the original string is returned unchanged.
    ///
    /// Example:
    /// ```swift
    /// let result = "04abcdef".dropPrefix("04") // Returns "abcdef"
    /// let unchanged = "abcdef".dropPrefix("04") // Returns "abcdef"
    /// ```
    ///
    /// - Parameter prefix: The prefix to remove
    /// - Returns: String with prefix removed if it existed, original string otherwise
    func dropPrefix(_ prefix: String) -> Self {
        if hasPrefix(prefix) {
            return String(dropFirst(prefix.count))
        }
        return self
    }
}

// MARK: - Flow.ChainID Extensions

public extension Flow.ChainID {
    /// Get the key indexer URL for a specific public key
    ///
    /// The key indexer service helps locate accounts associated with a public key.
    /// Different URLs are used for mainnet and testnet environments.
    ///
    /// Example:
    /// ```swift
    /// let url = Flow.ChainID.mainnet.keyIndexer(with: publicKey)
    /// // Returns: "https://production.key-indexer.flow.com/key/{publicKey}"
    /// ```
    ///
    /// - Parameter publicKey: The public key to query
    /// - Returns: URL for the key indexer service, or nil if not supported for this chain
    func keyIndexer(with publicKey: String) -> URL? {
        switch self {
        case .mainnet:
            return URL(string: "https://production.key-indexer.flow.com/key/\(publicKey)")
        case .testnet:
            return URL(string: "https://staging.key-indexer.flow.com/key/\(publicKey)")
        default:
            return nil
        }
    }
}
