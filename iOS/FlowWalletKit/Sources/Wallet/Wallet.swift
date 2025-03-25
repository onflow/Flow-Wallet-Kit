//
//  File.swift
//
//
//  Created by Hao Fu on 12/9/2024.
//

//TODO: REMOVE @preconcurrency
@preconcurrency import Flow
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

    public var flowAccounts: [Flow.ChainID: [Flow.Account]]?

    var cacheId: String {
        [Wallet.cachePrefix, type.id].joined(separator: "/")
    }

    public init(type: WalletType, networks: Set<Flow.ChainID> = [.mainnet, .testnet]) {
        self.type = type
        self.networks = networks
        Task {
            await fetchAccount()
        }
    }

    public func fetchAccount() async {
        do {
            try loadCahe()
            do {
                _ = try await fetchAllNetworkAccounts()
                try cache()
            } catch {
                // TODO: Handle Error
            }
        } catch {
            // TODO: Handle Error
        }
    }

    public func addNetwork(_ network: Flow.ChainID) {
        networks.insert(network)
    }

    public func fetchAllNetworkAccounts() async throws -> [Flow.ChainID: [Account]] {
        var flowAccounts = [Flow.ChainID: [Flow.Account]]()
        var networkAccounts = [Flow.ChainID: [Account]]()
        // TODO: Improve this to parallel fetch
        for network in networks {
            guard let accounts = try? await account(chainID: network) else {
                continue
            }
            flowAccounts[network] = accounts
            networkAccounts[network] = accounts.compactMap { Account(account: $0, key: type.key) }
        }
        accounts = networkAccounts
        self.flowAccounts = flowAccounts
        return networkAccounts
    }

    public func account(chainID: Flow.ChainID) async throws -> [Flow.Account] {
        guard case let .key(key) = type else {
            if case let .watch(address) = type {
                return [try await Flow.shared.accessAPI.getAccountAtLatestBlock(address: address)]
            }
            throw WalletError.invaildWalletType
        }

        var accounts: [Flow.Account] = []
        if let p256Key = try key.publicKey(signAlgo: .ECDSA_P256)?.hexString {
            let p256KeyRequest = try await Network.findFlowAccountByKey(publicKey: p256Key, chainID: chainID)
            accounts.append(contentsOf: p256KeyRequest)
        }

        if let secp256k1Key = try key.publicKey(signAlgo: .ECDSA_SECP256k1)?.hexString {
            let secp256k1KeyRequest = try await Network.findFlowAccountByKey(publicKey: secp256k1Key, chainID: chainID)
            accounts.append(contentsOf: secp256k1KeyRequest)
        }

        return accounts
    }

    public func fullAccount(chainID: Flow.ChainID) async throws -> [Flow.Account] {
        guard case let .key(key) = type else {
            if case let .watch(address) = type {
                return [try await Flow.shared.accessAPI.getAccountAtLatestBlock(address: address)]
            }
            throw WalletError.invaildWalletType
        }

        var accounts: [KeyIndexerResponse.Account] = []
        if let p256Key = try key.publicKey(signAlgo: .ECDSA_P256)?.hexString {
            let p256KeyRequest = try await Network.findAccountByKey(publicKey: p256Key, chainID: chainID)
            accounts.append(contentsOf: p256KeyRequest)
        }

        if let secp256k1Key = try key.publicKey(signAlgo: .ECDSA_SECP256k1)?.hexString {
            let secp256k1KeyRequest = try await Network.findAccountByKey(publicKey: secp256k1Key, chainID: chainID)
            accounts.append(contentsOf: secp256k1KeyRequest)
        }

        let addresses = Set(accounts).compactMap { Flow.Address(hex: $0.address) }
        return try await fetchAccounts(addresses: addresses)
    }

    public func fetchAccounts(addresses: [Flow.Address]) async throws -> [Flow.Account] {
        try await withThrowingTaskGroup(of: Flow.Account.self) { group in

            addresses.forEach { address in
                group.addTask { try await Flow.shared.accessAPI.getAccountAtLatestBlock(address: address) }
            }

            var result = [Flow.Account]()

            for try await image in group {
                result.append(image)
            }

            return result
        }
    }

    // MARK: - Cache

    public func cache() throws {
        guard let flowAccounts else {
            return
        }

        let data = try JSONEncoder().encode(flowAccounts)
        if case let .key(key) = type {
            try key.storage.set(cacheId, value: data)
        }
    }

    public func loadCahe() throws {
        guard case let .key(key) = type else {
            return
        }
        
        guard let data = try key.storage.get(cacheId) else {
            return
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
