//
//  EVMModels.swift
//  FlowWalletKit
//

import Foundation
import Flow

/// Represents supported EVM chains (Flow EVM plus custom extensions).
public enum EVMChain: Equatable {
    case flowMainnet
    case flowTestnet
    case custom(UInt64)

    /// Numeric chain ID.
    public var chainId: UInt64 {
        switch self {
        case .flowMainnet: return 747
        case .flowTestnet: return 545
        case let .custom(id): return id
        }
    }

    /// Encoded chain ID for WalletCore signing input.
    public var chainIdData: Data {
        var value = chainId
        var bytes: [UInt8] = []
        repeat {
            bytes.insert(UInt8(value & 0xff), at: 0)
            value >>= 8
        } while value > 0
        return Data(bytes)
    }

    /// Whether this chain is Flow EVM (mainnet/testnet).
    public var isFlowEVM: Bool {
        switch self {
        case .flowMainnet, .flowTestnet:
            return true
        default:
            return false
        }
    }

    /// Corresponding Flow chain ID when applicable (nil for non-Flow EVM).
    public var flowChainID: Flow.ChainID? {
        switch self {
        case .flowMainnet:
            return .mainnet
        case .flowTestnet:
            return .testnet
        case .custom:
            return nil
        }
    }
}

/// Result model for submitting an EVM transaction via Flow.
public struct FlowEVMSubmitResult {
    public let flowTxId: String
    public let evmTxId: String

    public init(flowTxId: String, evmTxId: String) {
        self.flowTxId = flowTxId
        self.evmTxId = evmTxId
    }
}

public extension Flow.ChainID {
    /// Map Flow network to its corresponding EVM chain when available.
    var evmChain: EVMChain? {
        switch self {
        case .mainnet:
            return .flowMainnet
        case .testnet:
            return .flowTestnet
        default:
            return nil
        }
    }
}
