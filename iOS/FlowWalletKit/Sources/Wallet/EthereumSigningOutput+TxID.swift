//
//  EthereumSigningOutput+TxID.swift
//  FlowWalletKit
//

import Foundation
import WalletCore

public extension EthereumSigningOutput {
    /// Returns the transaction hash (txid) for the signed payload.
    func txId() -> Data {
        if !preHash.isEmpty {
            return preHash
        }
        return Hash.keccak256(data: encoded)
    }

    /// Returns the transaction hash (txid) as a hex string with 0x prefix.
    func txIdHex() -> String {
        let hash = txId()
        guard hash.isEmpty == false else { return "0x" }
        return "0x" + hash.hexString
    }
}

