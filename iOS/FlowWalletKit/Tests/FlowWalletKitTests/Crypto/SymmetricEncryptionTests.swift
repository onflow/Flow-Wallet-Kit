import Foundation
import Testing
@testable import FlowWalletKit

struct SymmetricEncryptionTests {
    
    @Test
    func testChaChaPolyEncryptDecrypt() throws {
        // Given
        let password = "TestPassword123"
        
        guard let cipher = ChaChaPolyCipher(key: password) else {
            #expect(false, "Failed to initialize ChaChaPolyCipher")
            return
        }
        
        let originalData = "Secret information to encrypt".data(using: .utf8)!
        
        // When
        let encryptedData = try cipher.encrypt(data: originalData)
        let decryptedData = try cipher.decrypt(combinedData: encryptedData)
        
        // Then
        #expect(decryptedData == originalData)
    }
    
    @Test
    func testEncryptionWithDifferentPasswords() throws {
        // Given
        let password1 = "Password1"
        let password2 = "Password2"
        
        guard let cipher1 = ChaChaPolyCipher(key: password1),
              let cipher2 = ChaChaPolyCipher(key: password2) else {
            #expect(false, "Failed to initialize ChaChaPolyCipher")
            return
        }
        
        let originalData = "Secret information to encrypt".data(using: .utf8)!
        
        // When
        let encryptedData = try cipher1.encrypt(data: originalData)
        
        // Then - Attempting to decrypt with wrong password should fail
        #expect(throws: Error.self) {
            try cipher2.decrypt(combinedData: encryptedData)
        }
    }
    
    @Test
    func testEncryptionWithInvalidData() throws {
        // Given
        let password = "TestPassword123"
        guard let cipher = ChaChaPolyCipher(key: password) else {
            #expect(false, "Failed to initialize ChaChaPolyCipher")
            return
        }
        
        let invalidData = Data(repeating: 0, count: 10) // Too small to be valid combined data
        
        // Then - Attempting to decrypt invalid data should fail
        #expect(throws: Error.self) {
            try cipher.decrypt(combinedData: invalidData)
        }
    }
} 
