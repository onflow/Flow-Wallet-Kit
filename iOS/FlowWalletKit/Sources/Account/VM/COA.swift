//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 2/4/2025.
//

import Foundation
import WalletCore
import Flow

// CadenceOwnedAccount
public class COA: FlowVMProtocol, Identifiable, Codable {
    
    public var id: String {
        address
    }
    
    public var chainID: Flow.ChainID {
        network
    }
    
    public var vm: FlowVM {
        .EVM
    }
    
    public var address: String {
        hexAddr
    }
    
    private(set) var hexAddr: String
    private(set) var network: Flow.ChainID
    
    init?(_ address: String, network: Flow.ChainID = .mainnet) {
        guard let addr = AnyAddress(string: address.addHexPrefix(), coin: .ethereum) else {
            return nil
        }
        self.hexAddr = addr.description
        self.network = network
    }
}

extension Account {
    
    public func createCOA() async throws -> Flow.ID {
        let id = try await flow.createCOA(chainID: chainID,
                                          proposer: address,
                                          payer: address,
                                          signers: [self])
        return id
    }
    
    public func createCOA(payer: Flow.Address, signers: [FlowSigner]) async throws -> Flow.ID {
        let id = try await flow.createCOA(chainID: chainID,
                                          proposer: address,
                                          payer: payer,
                                          signers: signers)
        return id
    }
    
    func createCOA(payer: Flow.Address, signers: [FlowSigner]) async throws -> COA? {
        let id = try await flow.createCOA(chainID: chainID,
                                          proposer: address,
                                          payer: payer,
                                          signers: signers)
        let _ = try await id.onceSealed()
        return try await fetchVM()
    }
}