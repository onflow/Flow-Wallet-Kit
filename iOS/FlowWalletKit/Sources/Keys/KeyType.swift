//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 7/4/2025.
//

import Foundation

/// Types of cryptographic keys supported by FlowWalletKit
public enum KeyType: Codable {
    /// Key stored in device's Secure Enclave
    case secureEnclave
    /// Key derived from a BIP39 seed phrase
    case seedPhrase
    /// Raw private key
    case privateKey
    /// Key stored in encrypted JSON keystore format
    case keyStore
}
