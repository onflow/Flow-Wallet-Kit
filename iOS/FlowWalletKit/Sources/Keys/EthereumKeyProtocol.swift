//
//  EthereumKeyProtocol.swift
//  FlowWalletKit
//
//  Created by Auto on 2025/10/17.
//

import Foundation
import WalletCore

/// Capabilities required for keys that derive Ethereum-compatible (EOA) credentials.
/// Conforming types must be able to derive secp256k1 key material and produce
/// EIP-55 formatted addresses as well as signatures in `[r(32) | s(32) | v(1)]` form.
public protocol EthereumKeyProtocol {
    /// Derives the EIP-55 checksummed address for a derivation index.
    /// - Parameter index: BIP-44 address index (defaults to 0).
    func ethAddress(index: UInt32) throws -> String
    
    /// Returns the uncompressed secp256k1 public key (65 bytes, 0x04-prefixed).
    func ethPublicKey(index: UInt32) throws -> Data
    
    /// Returns the raw 32-byte secp256k1 private key.
    func ethPrivateKey(index: UInt32) throws -> Data
    
    /// Signs a 32-byte digest using Ethereum's secp256k1 scheme and returns `[r|s|v]`.
    /// - Parameter digest: Pre-hashed message (must be exactly 32 bytes).
    func ethSign(digest: Data, index: UInt32) throws -> Data
}

public extension EthereumKeyProtocol {
    /// Convenience overloads that default to the first account index.
    func ethAddress() throws -> String {
        try ethAddress(index: 0)
    }
    
    func ethPublicKey() throws -> Data {
        try ethPublicKey(index: 0)
    }
    
    func ethPrivateKey() throws -> Data {
        try ethPrivateKey(index: 0)
    }
    
    func ethSign(digest: Data) throws -> Data {
        try ethSign(digest: digest, index: 0)
    }
}
