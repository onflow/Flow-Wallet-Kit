/// FlowWalletKit - Symmetric Encryption Implementation
///
/// This module provides symmetric encryption functionality using modern algorithms:
/// - ChaCha20-Poly1305: High-performance authenticated encryption
/// - AES-GCM: Industry standard authenticated encryption
///
/// Features:
/// - Password-based key derivation
/// - Authenticated encryption and decryption
/// - Secure key handling
/// - Support for multiple algorithms

import Foundation
import CryptoKit

/// Protocol defining symmetric encryption operations
public protocol SymmetricEncryption {
    /// Symmetric key used for encryption/decryption
    var key: SymmetricKey { get }
    /// Size of the symmetric key
    var keySize: SymmetricKeySize { get }
    
    /// Encrypt data using the symmetric key
    /// - Parameter data: Data to encrypt
    /// - Returns: Encrypted data with authentication tag
    /// - Throws: Encryption errors
    func encrypt(data: Data) throws -> Data
    
    /// Decrypt data using the symmetric key
    /// - Parameter combinedData: Encrypted data with authentication tag
    /// - Returns: Original decrypted data
    /// - Throws: Decryption or authentication errors
    func decrypt(combinedData: Data) throws -> Data
}

/// Errors that can occur during encryption operations
public enum EncryptionError: Error {
    /// Encryption operation failed
    case encryptFailed
    /// Initialization of encryption components failed
    case initFailed
}
