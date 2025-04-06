/// Implementation of ChaCha20-Poly1305 authenticated encryption
///
/// ChaCha20-Poly1305 provides:
/// - High performance on mobile devices
/// - Strong security guarantees
/// - Protection against tampering

import Foundation
import CryptoKit

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

