/// Implementation of AES-GCM authenticated encryption
///
/// AES-GCM provides:
/// - Industry standard encryption
/// - Hardware acceleration on modern devices
/// - Protection against tampering

import Foundation
import CryptoKit

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
