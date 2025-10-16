//
//  File.swift
//
//
//  Created by Hao Fu on 16/1/2024.
//

/// FlowWalletKit - Error Definitions
///
/// This module defines all possible errors that can occur within the FlowWalletKit.
/// Each error case represents a specific failure scenario in the wallet operations.
///
/// The errors are categorized into several groups:
/// - General Errors: Basic operational failures
/// - Cryptographic Errors: Issues with cryptographic operations
/// - Authentication Errors: Problems with keys and signatures
/// - Network Errors: Communication failures with external services
/// - Storage Errors: Issues with data persistence
///
/// Usage Example:
/// ```swift
/// do {
///     try wallet.sign(transaction)
/// } catch WalletError.emptySignKey {
///     // Handle missing signing key
/// } catch WalletError.signError {
///     // Handle signing failure
/// }
/// ```

import Flow
import Foundation

/// Represents all possible errors that can occur in FlowWalletKit operations
///
/// This enum implements several protocols:
/// - `Error`: For Swift error handling
/// - `CaseIterable`: To enumerate all possible errors
/// - `CustomStringConvertible`: For human-readable error messages
public enum FWKError: String, Error, CaseIterable, CustomStringConvertible {
    // MARK: - General Errors
    
    /// Operation or feature not implemented
    /// Thrown when attempting to use functionality that is planned but not yet available
    case noImplement
    /// No keys found in keychain
    /// Thrown when trying to access a key that should exist in the keychain but doesn't
    case emptyKeychain
    /// Key data is empty or invalid
    /// Thrown when key data is missing or corrupted
    case emptyKey
    /// Signing key is empty or not available
    /// Thrown when attempting to sign without a valid signing key
    case emptySignKey
    
    // MARK: - Cryptographic Errors
    
    /// Hash algorithm not supported
    /// Thrown when trying to use an unsupported hashing algorithm
    case unsupportHashAlgorithm
    /// Signature algorithm not supported
    /// Thrown when trying to use an unsupported signing algorithm
    case unsupportSignatureAlgorithm
    /// Failed to initialize ChaCha20-Poly1305
    /// Thrown when encryption setup fails
    case initChaChapolyFailed
    /// Failed to initialize HD wallet
    /// Thrown when HD wallet creation fails (e.g., invalid entropy)
    case initHDWalletFailed
    /// Failed to initialize private key
    /// Thrown when private key creation fails
    case initPrivateKeyFailed
    /// Failed to restore wallet from backup
    /// Thrown when wallet restoration fails (e.g., corrupted backup)
    case restoreWalletFailed
    /// Invalid signature algorithm specified
    /// Thrown when an unknown or invalid signature algorithm is provided
    case invaildSignatureAlgorithm
    /// Invalid EVM address
    /// Thrown when an unknown or invalid EVM address is provided
    case invaildEVMAddress
    
    // MARK: - Authentication Errors
    
    /// Invalid password provided
    /// Thrown when decryption fails due to wrong password
    case invaildPassword
    /// Invalid private key format
    /// Thrown when private key data is malformed
    case invaildPrivateKey
    /// Invalid KeyStore JSON format
    /// Thrown when KeyStore JSON is malformed or missing required fields
    case invaildKeyStoreJSON
    /// Invalid KeyStore password
    /// Thrown when KeyStore decryption fails due to wrong password
    case invaildKeyStorePassword
    /// Error during signing operation
    /// Thrown when transaction or message signing fails
    case signError
    /// Failed to initialize public key
    /// Thrown when public key derivation or loading fails
    case initPublicKeyFailed
    /// Ethereum capability is unavailable for the selected key
    case unsupportedEthereumKey
    /// Unsupported or out-of-range Ethereum derivation index
    case unsupportedEthereumDerivation
    /// Invalid EVM typed data structure
    case invalidEthereumTypedData
    /// Ethereum message/digest must be 32 bytes
    case invalidEthereumMessage
    /// Malformed Ethereum signature blob
    case invalidEthereumSignature
    /// Invalid or malformed Ethereum derivation path definition
    case invalidEthereumDerivationPath
    
    case failedPassSecurityCheck
    
    // MARK: - Network Errors
    
    /// Invalid key indexer URL
    /// Thrown when the key indexer service URL is malformed
    case incorrectKeyIndexerURL
    /// Key indexer request failed
    /// Thrown when communication with key indexer service fails
    case keyIndexerRequestFailed
    /// Failed to decode key indexer response
    /// Thrown when key indexer response is invalid or unexpected
    case decodeKeyIndexerFailed
    
    // MARK: - Storage Errors
    
    /// Failed to load data from cache
    /// Thrown when cached data cannot be retrieved or is corrupted
    case loadCacheFailed
    /// Invalid wallet type for operation
    /// Thrown when attempting an operation not supported by the wallet type
    case invaildWalletType
    
    case cacheDecodeFailed
    
    case emptyCreatedAddress
    
    case noImplementError

    /// Returns the numeric error code for the error case
    /// This can be used for error tracking and analytics
    var errorCode: Int {
        FWKError.allCases.firstIndex(of: self) ?? -1
    }

    /// Returns a human-readable description of the error
    /// Format: "WalletError Code: {code}-{rawValue}"
    public var description: String {
        "\(type(of: self)) Code: \(errorCode)-\(self.rawValue)"
    }
}
