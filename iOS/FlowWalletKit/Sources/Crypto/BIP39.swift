//
//  File.swift
//
//
//  Created by Hao Fu on 27/8/2024.
//

import Foundation
import WalletCore

public enum BIP39 {
    public enum SeedPhraseLength: Int, Codable, Sendable {
        case twelve = 12
        case fifteen = 15
        case twentyFour = 24

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

    static func generate(_ length: SeedPhraseLength = .twelve, passphrase: String = "") -> String? {
        let hdWallet = HDWallet(strength: length.strength, passphrase: passphrase)
        return hdWallet?.mnemonic
    }

    static func isValid(mnemonic: String) -> Bool {
        return Mnemonic.isValid(mnemonic: mnemonic)
    }

    static func isValidWord(word: String) -> Bool {
        return Mnemonic.isValidWord(word: word)
    }

    static func search(prefix: String) -> [String] {
        return Mnemonic.search(prefix: prefix)
    }

    static func suggest(prefix: String) -> String {
        return Mnemonic.suggest(prefix: prefix)
    }
}
