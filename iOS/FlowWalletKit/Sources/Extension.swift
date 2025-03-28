//
//  File.swift
//
//
//  Created by Hao Fu on 16/1/2024.
//

import CryptoKit
import Flow
import WalletCore

extension Flow.HashAlgorithm {
    func hash(data: Data) throws -> Data {
        switch self {
        case .SHA2_256:
            return Hash.sha256(data: data)
        case .SHA3_256:
            return Hash.sha3_256(data: data)
        default:
            throw WalletError.unsupportHashAlgorithm
        }
    }
}

extension Flow.SignatureAlgorithm {
    var WCCurve: Curve? {
        switch self {
        case .ECDSA_P256:
            return Curve.nist256p1
        case .ECDSA_SECP256k1:
            return Curve.secp256k1
        case .unknown:
            return nil
        }
    }
}

extension PublicKey {
    func fromat() -> String {
        uncompressed.data.hexValue.dropPrefix("04")
    }
}

extension String {
    func dropPrefix(_ prefix: String) -> Self {
        if hasPrefix(prefix) {
            return String(dropFirst(prefix.count))
        }
        return self
    }
}

public extension Flow.ChainID {

    func keyIndexer(with publicKey: String) -> URL? {
        switch self {
        case .mainnet:
            return URL(string: "https://production.key-indexer.flow.com/key/\(publicKey)")
        case .testnet:
            return URL(string: "https://staging.key-indexer.flow.com/key/\(publicKey)")
        default:
            return nil
        }
    }
}
