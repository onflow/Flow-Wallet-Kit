/// FlowWalletKit - BIP39 Implementation
///
/// This module provides functionality for working with BIP39 mnemonic phrases.
/// BIP39 is a standard for generating deterministic wallets using mnemonic phrases
/// (sequences of words that can be used to derive cryptographic keys).
///
/// Features:
/// - Mnemonic phrase generation
/// - Phrase validation
/// - Word validation and suggestions
/// - Support for different phrase lengths
/// - Optional passphrase support

import Foundation
import WalletCore

/// Implementation of BIP39 mnemonic functionality
public enum BIP39 {
    /// Supported lengths for BIP39 mnemonic phrases
    public enum SeedPhraseLength: Int, Codable {
        /// 12-word mnemonic (128 bits of entropy)
        case twelve = 12
        /// 15-word mnemonic (160 bits of entropy)
        case fifteen = 15
        /// 24-word mnemonic (256 bits of entropy)
        case twentyFour = 24

        /// The entropy strength in bits for each phrase length
        public var strength: Int32 {
            switch self {
            case .twelve:
                return 128
            case .fifteen:
                return 160
            case .twentyFour:
                return 256
            }
        }
    }

    /// Generate a new BIP39 mnemonic phrase
    /// - Parameters:
    ///   - length: Length of the mnemonic phrase (default: 12 words)
    ///   - passphrase: Optional passphrase for additional security
    /// - Returns: Generated mnemonic phrase, or nil if generation fails
    static func generate(_ length: SeedPhraseLength = .twelve, passphrase: String = "") -> String? {
        let hdWallet = HDWallet(strength: length.strength, passphrase: passphrase)
        return hdWallet?.mnemonic
    }

    /// Validate a complete mnemonic phrase
    /// - Parameter mnemonic: The mnemonic phrase to validate
    /// - Returns: Whether the phrase is valid according to BIP39
    static func isValid(mnemonic: String) -> Bool {
        return Mnemonic.isValid(mnemonic: mnemonic)
    }

    /// Validate a single word from the BIP39 wordlist
    /// - Parameter word: The word to validate
    /// - Returns: Whether the word is in the BIP39 wordlist
    static func isValidWord(word: String) -> Bool {
        return Mnemonic.isValidWord(word: word)
    }

    /// Search for words in the BIP39 wordlist matching a prefix
    /// - Parameter prefix: The prefix to search for
    /// - Returns: Array of matching words from the wordlist
    static func search(prefix: String) -> [String] {
        return Mnemonic.search(prefix: prefix)
    }

    /// Get a suggested completion for a partial word
    /// - Parameter prefix: The partial word to complete
    /// - Returns: A suggested complete word from the BIP39 wordlist
    static func suggest(prefix: String) -> String {
        return Mnemonic.suggest(prefix: prefix)
    }
}
