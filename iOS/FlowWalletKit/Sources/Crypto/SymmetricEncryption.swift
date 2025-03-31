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

/// Implementation of ChaCha20-Poly1305 authenticated encryption
///
/// ChaCha20-Poly1305 provides:
/// - High performance on mobile devices
/// - Strong security guarantees
/// - Protection against tampering
public class ChaChaPolyCipher: SymmetricEncryption {
    /// Symmetric key derived from password
    public var key: SymmetricKey
    /// Key size (256 bits for ChaCha20-Poly1305)
    public var keySize: SymmetricKeySize = .bits256

    /// Encrypt data using ChaCha20-Poly1305
    /// - Parameter data: Data to encrypt
    /// - Returns: Encrypted data with Poly1305 authentication tag
    /// - Throws: CryptoKit encryption errors
    public func encrypt(data: Data) throws -> Data {
        let sealedBox = try ChaChaPoly.seal(data, using: key)
        return sealedBox.combined
    }

    /// Decrypt and authenticate data using ChaCha20-Poly1305
    /// - Parameter combinedData: Encrypted data with authentication tag
    /// - Returns: Original decrypted data
    /// - Throws: CryptoKit decryption or authentication errors
    public func decrypt(combinedData: Data) throws -> Data {
        let sealedBox = try ChaChaPoly.SealedBox(combined: combinedData)
        let decryptedData = try ChaChaPoly.open(sealedBox, using: key)
        return decryptedData
    }

    /// Initialize cipher with a password
    /// - Parameter key: Password to derive key from
    /// - Returns: nil if key derivation fails
    public init?(key: String) {
        guard let keyData = key.data(using: .utf8) else {
            return nil
        }
        let hashedKey = SHA256.hash(data: keyData)
        let bitKey = Data(hashedKey.prefix(keySize.bitCount))
        self.key = SymmetricKey(data: bitKey)
    }
}

/// Implementation of AES-GCM authenticated encryption
///
/// AES-GCM provides:
/// - Industry standard encryption
/// - Hardware acceleration on modern devices
/// - Protection against tampering
public class AESGCMCipher: SymmetricEncryption {
    /// Symmetric key derived from password
    public var key: SymmetricKey
    /// Key size (256 bits for AES-256-GCM)
    public var keySize: SymmetricKeySize = .bits256

    /// Encrypt data using AES-GCM
    /// - Parameter data: Data to encrypt
    /// - Returns: Encrypted data with authentication tag
    /// - Throws: CryptoKit encryption errors or EncryptionError
    public func encrypt(data: Data) throws -> Data {
        let sealedBox = try AES.GCM.seal(data, using: key)
        guard let encryptedData = sealedBox.combined else {
            throw EncryptionError.encryptFailed
        }
        return encryptedData
    }

    /// Decrypt and authenticate data using AES-GCM
    /// - Parameter combinedData: Encrypted data with authentication tag
    /// - Returns: Original decrypted data
    /// - Throws: CryptoKit decryption or authentication errors
    public func decrypt(combinedData: Data) throws -> Data {
        let sealedBox = try AES.GCM.SealedBox(combined: combinedData)
        let decryptedData = try AES.GCM.open(sealedBox, using: key)
        return decryptedData
    }

    /// Initialize cipher with a password
    /// - Parameter key: Password to derive key from
    /// - Returns: nil if key derivation fails
    public init?(key: String) {
        guard let keyData = key.data(using: .utf8) else {
            return nil
        }
        let hashedKey = SHA256.hash(data: keyData)
        let bitKey = Data(hashedKey.prefix(keySize.bitCount))
        self.key = SymmetricKey(data: bitKey)
    }
}
