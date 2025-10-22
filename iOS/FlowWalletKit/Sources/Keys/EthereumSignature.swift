//
//  EthereumSignature.swift
//  FlowWalletKit
//
//  Created by Auto on 2025/10/17.
//

import Foundation

/// Normalizes a WalletCore secp256k1 signature into `[r(32) | s(32) | v(1)]` form.
/// WalletCore returns a 65-byte blob where `v` may be `0` or `1`. Ethereum RPCs expect
/// `v` to be `27` or `28` (Yellow Paper / EIP-155). This helper adjusts the recovery id
/// and validates the signature layout.
/// - Parameter signature: Raw signature returned by WalletCore.
/// - Returns: Signature with canonical `v` value.
/// - Throws: `FWKError.invalidEthereumSignature` if the signature is not 65 bytes.
func normalizeEthereumSignature(_ signature: Data) throws -> Data {
    guard signature.count == 65 else {
        throw FWKError.invalidEthereumSignature
    }
    
    var normalized = Data(signature.prefix(64))
    var v = signature[64]
    if v < 27 {
        v &+= 27
    }
    normalized.append(v)
    return normalized
}

/// Validates that a digest conforms to Ethereum's expected 32-byte Keccak hash size.
/// - Parameter digest: The message digest to check.
/// - Throws: `FWKError.invalidEthereumMessage` if the digest is not 32 bytes.
func validateEthereumDigest(_ digest: Data) throws {
    guard digest.count == 32 else {
        throw FWKError.invalidEthereumMessage
    }
}
