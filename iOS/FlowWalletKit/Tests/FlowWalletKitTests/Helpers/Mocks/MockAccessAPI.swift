//
//  MockAccessAPI.swift
//  FlowWalletKit
//
//  Created by Marty Ulrich on 3/24/25.
//

@testable import FlowWalletKit
import Flow
import Foundation
import BigInt

public final class MockAccessAPI: FlowAccessProtocol {
    private let accountsByAddress: [Flow.Address: Flow.Account]

    public init(accountsByAddress: [Flow.Address: Flow.Account]) {
        self.accountsByAddress = accountsByAddress
    }

    // MARK: - FlowAccessProtocol

    public func ping() async throws -> Bool {
        true
    }

    public func getLatestBlockHeader() async throws -> Flow.BlockHeader {
        // Provide stubbed data
        return Flow.BlockHeader(
            id: Flow.ID(data: Data("latest-block-id".utf8)),
            parentId: Flow.ID(data: Data("parent-block-id".utf8)),
            height: 999,
            timestamp: Date()
        )
    }

    public func getBlockHeaderById(id: Flow.ID) async throws -> Flow.BlockHeader {
        // Provide stubbed data
        return Flow.BlockHeader(
            id: id,
            parentId: Flow.ID(data: Data("parent-block-id".utf8)),
            height: 1000,
            timestamp: Date()
        )
    }

    public func getBlockHeaderByHeight(height: UInt64) async throws -> Flow.BlockHeader {
        // Provide stubbed data
        return Flow.BlockHeader(
            id: Flow.ID(data: Data("block-header-id-\(height)".utf8)),
            parentId: Flow.ID(data: Data("parent-block-id".utf8)),
            height: height,
            timestamp: Date()
        )
    }

    public func getLatestBlock(sealed: Bool) async throws -> Flow.Block {
        // Provide stubbed data
        return Flow.Block(
            id: Flow.ID(data: Data("latest-block-id".utf8)),
            parentId: Flow.ID(data: Data("latest-parent-id".utf8)),
            height: 999,
            timestamp: Date(),
            collectionGuarantees: [],
            blockSeals: [],
            signatures: []
        )
    }

    public func getBlockById(id: Flow.ID) async throws -> Flow.Block {
        return Flow.Block(
            id: id,
            parentId: Flow.ID(data: Data("block-parent".utf8)),
            height: 111,
            timestamp: Date(),
            collectionGuarantees: [],
            blockSeals: [],
            signatures: []
        )
    }

    public func getBlockByHeight(height: UInt64) async throws -> Flow.Block {
        return Flow.Block(
            id: Flow.ID(data: Data("block-id-\(height)".utf8)),
            parentId: Flow.ID(data: Data("block-parent-\(height)".utf8)),
            height: height,
            timestamp: Date(),
            collectionGuarantees: [],
            blockSeals: [],
            signatures: []
        )
    }

    public func getCollectionById(id: Flow.ID) async throws -> Flow.Collection {
        return Flow.Collection(
            id: id,
            transactionIds: []
        )
    }

    public func sendTransaction(transaction: Flow.Transaction) async throws -> Flow.ID {
        // Return some random or stubbed ID
        return Flow.ID(data: Data("transaction-id".utf8))
    }

    public func getTransactionById(id: Flow.ID) async throws -> Flow.Transaction {
        // Provide a stub transaction
        return Flow.Transaction(
            script: Flow.Script(text: "pub fun main() {}"),
            arguments: [],
            referenceBlockId: id,
            gasLimit: BigUInt(999),
            proposalKey: Flow.TransactionProposalKey(address: Flow.Address(hex: "0x01"), keyIndex: 0),
            payer: Flow.Address(hex: "0x01"),
            authorizers: []
        )
    }

    public func getTransactionResultById(id: Flow.ID) async throws -> Flow.TransactionResult {
        // Provide a stub result
        return Flow.TransactionResult(
            status: .sealed,
            errorMessage: "",
            events: [],
            statusCode: 0,
            blockId: id,
            computationUsed: "100"
        )
    }

    public func getAccountAtLatestBlock(address: Flow.Address) async throws -> Flow.Account {
        guard let account = accountsByAddress[address] else {
            throw WalletError.invaildWalletType
        }
        return account
    }

    public func getAccountByBlockHeight(address: Flow.Address, height: UInt64) async throws -> Flow.Account {
        // Could return the same as getAccountAtLatestBlock, or some variant
        guard let account = accountsByAddress[address] else {
            throw WalletError.invaildWalletType
        }
        return account
    }

    public func executeScriptAtLatestBlock(script: Flow.Script, arguments: [Flow.Argument]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func executeScriptAtLatestBlock(script: Flow.Script, arguments: [Flow.Cadence.FValue]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func executeScriptAtBlockId(script: Flow.Script, blockId: Flow.ID, arguments: [Flow.Argument]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func executeScriptAtBlockId(script: Flow.Script, blockId: Flow.ID, arguments: [Flow.Cadence.FValue]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func executeScriptAtBlockHeight(script: Flow.Script, height: UInt64, arguments: [Flow.Argument]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func executeScriptAtBlockHeight(script: Flow.Script, height: UInt64, arguments: [Flow.Cadence.FValue]) async throws -> Flow.ScriptResponse {
        Flow.ScriptResponse(data: Data("script-response".utf8))
    }

    public func getEventsForHeightRange(type: String, range: ClosedRange<UInt64>) async throws -> [Flow.Event.Result] {
        []
    }

    public func getEventsForBlockIds(type: String, ids: Set<Flow.ID>) async throws -> [Flow.Event.Result] {
        []
    }

    public func getNetworkParameters() async throws -> Flow.ChainID {
        .mainnet
    }
}
