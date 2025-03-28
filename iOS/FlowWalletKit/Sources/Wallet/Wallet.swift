//
//  File.swift
//
//
//  Created by Hao Fu on 12/9/2024.
//

import Flow
import Foundation

public enum WalletType {
    case key(any KeyProtocol)
//    case proxy(any ProxyProtocol)
    case watch(Flow.Address)

    var idPrefix: String {
        switch self {
        case .key:
            return "Key"
        case .watch:
            return "Watch"
        }
    }

    var id: String {
        switch self {
        case let .key(key):
            return [idPrefix, key.id].joined(separator: "/")
        case let .watch(address):
            return [idPrefix, address.hex].joined(separator: "/")
        }
    }

    var key: (any KeyProtocol)? {
        switch self {
        case let .key(key):
            return key
        default:
            return nil
        }
    }
}


public class Wallet: ObservableObject {
    static let cachePrefix: String = "Accounts"
    public let type: WalletType
    public var networks: Set<Flow.ChainID>

    @Published
    public var accounts: [Flow.ChainID: [Account]]? = nil

    public var fullWeightAccounts: [Flow.ChainID: [Account]]? {
        guard let accounts else { return nil }
        return accounts.mapValues { $0.filter { $0.hasFullWeightKey } }
    }

    var flowAccounts: [Flow.ChainID: [Flow.Account]]?

    private var cacheId: String {
        [Wallet.cachePrefix, type.id].joined(separator: "/")
    }

    public init(type: WalletType, networks: Set<Flow.ChainID> = [.mainnet, .testnet]) {
        self.type = type
        self.networks = networks
    }

    public func fetchAccount() async throws {
        try loadCahe()
        try await _ = fetchAllNetworkAccounts()
        try cache()
    }

    public func addNetwork(_ network: Flow.ChainID) {
        networks.insert(network)
    }

    public func fetchAllNetworkAccounts() async throws -> [Flow.ChainID: [Account]] {
        var flowAccounts = [Flow.ChainID: [Flow.Account]]()
        var networkAccounts = [Flow.ChainID: [Account]]()
        
        try await withThrowingTaskGroup(of: (Flow.ChainID, [Flow.Account]).self) { group in
            for network in networks {
                group.addTask {
                    if let accounts = try? await self.account(chainID: network) {
                        return (network, accounts)
                    }
                    return (network, [])
                }
            }
            
            for try await (network, accounts) in group {
                if !accounts.isEmpty {
                    flowAccounts[network] = accounts
                    networkAccounts[network] = accounts.compactMap { Account(account: $0, key: type.key) }
                }
            }
        }
        
        accounts = networkAccounts
        self.flowAccounts = flowAccounts
        return networkAccounts
    }

    public func account(chainID: Flow.ChainID, onlyFullWeight: Bool = true) async throws -> [Flow.Account] {
        guard case let .key(key) = type else {
            if case let .watch(address) = type {
                return [try await flow.getAccountAtLatestBlock(address: address)]
            }
            throw WalletError.invaildWalletType
        }

        async let p256KeyAccounts: [Flow.Account] = {
            if let p256Key = key.publicKey(signAlgo: .ECDSA_P256)?.hexString {
                return try await Network.findFlowAccountByKey(publicKey: p256Key, chainID: chainID)
            }
            return []
        }()

        async let secp256k1KeyAccounts: [Flow.Account] = {
            if let secp256k1Key = key.publicKey(signAlgo: .ECDSA_SECP256k1)?.hexString {
                return try await Network.findFlowAccountByKey(publicKey: secp256k1Key, chainID: chainID)
            }
            return []
        }()

        return try await p256KeyAccounts + secp256k1KeyAccounts
    }

    // MARK: - Cache

    public func cache() throws {
        // TODO: Handle other type
        guard let flowAccounts, case let .key(key) = type else {
            return
        }

        let data = try JSONEncoder().encode(flowAccounts)
        try key.storage.set(cacheId, value: data)
    }

    public func loadCahe() throws {
        // TODO: Handle other type
        guard case let .key(key) = type, let data = try key.storage.get(cacheId) else {
            throw WalletError.loadCacheFailed
        }
        let model = try JSONDecoder().decode([Flow.ChainID: [Flow.Account]].self, from: data)
        flowAccounts = model

        accounts = [Flow.ChainID: [Account]]()
        for network in model.keys {
            if let acc = model[network] {
                accounts?[network] = acc.compactMap { Account(account: $0, key: type.key) }
            }
        }
    }
}
