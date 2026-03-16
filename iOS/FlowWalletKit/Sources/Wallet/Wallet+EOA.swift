//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 17/10/2025.
//

import Foundation
import Flow
import WalletCore

// MARK: - Support EOA

extension Wallet {
    public func getEOAAccount(indexes: [UInt32]? = nil) throws -> [AnyAddress] {
        let key = try resolveEthereumKey()
        let normalizedIndexes = indexes?.isEmpty == false ? indexes! : [0]
        let addresses = try deriveEOAAddresses(from: key, indexes: normalizedIndexes)
        for (i, addr) in zip(normalizedIndexes, addresses) {
            eoaAddressMap[i] = addr.description
        }
        return addresses
    }

    /// Derive multiple EOA accounts from the wallet's seed phrase.
    /// Each returned `EOAAccount` carries its own signing context.
    /// - Parameter indexes: BIP44 address indexes to derive (defaults to [0]).
    /// - Returns: Array of `EOAAccount` instances with signing capabilities.
    public func getEOAAccounts(indexes: [UInt32]? = nil) throws -> [EOAAccount] {
        let key = try resolveEthereumKey()
        let normalizedIndexes = indexes?.isEmpty == false ? indexes! : [0]
        let accounts = try normalizedIndexes.map { index in
            let address = try key.ethAddress(index: index)
            let publicKey = try key.ethPublicKey(index: index)
            eoaAddressMap[index] = address
            return EOAAccount(address: address, index: index, publicKey: publicKey, key: key)
        }
        try? cacheEOAAddressMap()
        return accounts
    }
    
    /// Returns the Ethereum address for the given derivation index (default index 0).
    public func ethAddress(index: UInt32 = 0) throws -> String {
        try resolveEthereumKey().ethAddress(index: index)
    }
    
    /// Signs a pre-hashed 32-byte digest with the wallet's Ethereum key.
    public func ethSignDigest(_ digest: Data, index: UInt32 = 0) throws -> Data {
        try resolveEthereumKey().ethSign(digest: digest, index: index)
    }
    
    /// EIP-191 personal sign (`personal_sign`). Prefixes the payload and hashes with keccak256 before signing.
    public func ethSignPersonalMessage(_ message: Data, index: UInt32 = 0) throws -> Data {
        let key = try resolveEthereumKey()
        let prefixString = "\u{19}Ethereum Signed Message:\n\(message.count)"
        guard let prefix = prefixString.data(using: .utf8) else {
            throw FWKError.invalidEthereumMessage
        }
        var payload = Data()
        payload.append(prefix)
        payload.append(message)
        let digest = Hash.keccak256(data: payload)
        return try key.ethSign(digest: digest, index: index)
    }
    
    /// Convenience alias matching RPC naming (`eth_sign` / `personal_sign`).
    public func ethSignPersonalData(_ data: Data, index: UInt32 = 0) throws -> Data {
        try ethSignPersonalMessage(data, index: index)
    }
    
    /// Signs structured data (EIP-712). Expects a JSON payload matching wallet-core's schema.
    public func ethSignTypedData(json: String, index: UInt32 = 0) throws -> Data {
        let key = try resolveEthereumKey()
        let digest = EthereumAbi.encodeTyped(messageJson: json)
        guard digest.count == 32 else {
            throw FWKError.invalidEthereumTypedData
        }
        return try key.ethSign(digest: digest, index: index)
    }
    
    /// Recovers the Ethereum address from a personal-sign style signature.
    static public func ethRecoverAddress(signature: Data, message: Data) throws -> String {
        let normalizedSignature = try normalizeEthereumSignature(signature)
        let prefixString = "\u{19}Ethereum Signed Message:\n\(message.count)"
        guard let prefix = prefixString.data(using: .utf8) else {
            throw FWKError.invalidEthereumMessage
        }
        var payload = Data()
        payload.append(prefix)
        payload.append(message)
        let digest = Hash.keccak256(data: payload)
        guard let publicKey = PublicKey.recover(signature: normalizedSignature, message: digest) else {
            throw FWKError.invalidEthereumSignature
        }
        let address = AnyAddress(publicKey: publicKey, coin: .ethereum)
        return address.description
    }
    
    /// Signs an Ethereum transaction using WalletCore's AnySigner pipeline.
    public func ethSignTransaction(_ input: EthereumSigningInput, index: UInt32 = 0) throws -> EthereumSigningOutput {
        let key = try resolveEthereumKey()
        var signingInput = input
        signingInput.privateKey = try key.ethPrivateKey(index: index)
        defer { signingInput.privateKey = Data() }
        var output: EthereumSigningOutput = AnySigner.sign(input: signingInput, coin: .ethereum)
        let transactionHash = Hash.keccak256(data: output.encoded)
        output.preHash = transactionHash
        return output
    }
    
    public func refreshEOAAddresses() {
        guard let key = try? resolveEthereumKey() else {
            eoaAddressMap = [:]
            return
        }

        do {
            let address = try key.ethAddress(index: 0)
            eoaAddressMap[0] = address
        } catch {
            eoaAddressMap = [:]
        }
    }

    private func deriveEOAAddresses(from key: EthereumKeyProtocol,
                                    indexes: [UInt32]) throws -> [AnyAddress] {
        var results: [AnyAddress] = []
        for index in indexes {
            let addressString = try key.ethAddress(index: index)
            guard let address = AnyAddress(string: addressString, coin: .ethereum) else {
                throw FWKError.invaildEVMAddress
            }
            results.append(address)
        }
        return results
    }
    
    // MARK: - EOA Address Map Cache

    private static let eoaMapCachePrefix = "EOAMap"

    private var eoaMapCacheId: String {
        [Wallet.cachePrefix, Self.eoaMapCachePrefix, type.id].joined(separator: "-")
    }

    /// Persist the current eoaAddressMap to storage.
    public func cacheEOAAddressMap() throws {
        let stringKeyed = Dictionary(uniqueKeysWithValues: eoaAddressMap.map { (String($0.key), $0.value) })
        let data = try JSONEncoder().encode(stringKeyed)
        try cacheStorage.set(eoaMapCacheId, value: data)
    }

    /// Load eoaAddressMap from storage. Called during init.
    func loadCachedEOAAddressMap() {
        guard let data = try? cacheStorage.get(eoaMapCacheId),
              let stringKeyed = try? JSONDecoder().decode([String: String].self, from: data) else {
            return
        }
        for (key, value) in stringKeyed {
            if let index = UInt32(key) {
                eoaAddressMap[index] = value
            }
        }
    }

    private func resolveEthereumKey() throws -> EthereumKeyProtocol {
        guard case let .key(rawKey) = type,
              let ethereumKey = rawKey as? EthereumKeyProtocol else {
            throw FWKError.unsupportedEthereumKey
        }
        return ethereumKey
    }

    /// Sends an EOA-signed Ethereum transaction to Flow EVM through Cadence.
    /// - Parameters:
    ///   - account: Flow account used as proposer/payer/authorizer.
    ///   - rlpEncodedTransaction: Signed Ethereum transaction payload.
    ///   - coinbaseAddr: EOA coinbase address.
    /// - Returns: Flow transaction ID after submission.
	public func ethSendSignedTransactionByCadence(chainId: Flow.ChainID = .mainnet,
												  account: Flow.Address,
												  rlpEncodedTransaction: Data,
												  coinbaseAddr: String,
												  signers: [FlowSigner],
												  payer: Flow.Address? = nil
    ) async throws -> Flow.ID {
        try await flow.runEVMTransaction(
            chainID: chainId,
            proposer: account,
            payer: payer ?? account,
            rlpEncodedTransaction: Array(rlpEncodedTransaction),
            coinbaseAddress: coinbaseAddr,
            signers: signers
        )
    }

    /// Sign an Ethereum transaction (WalletCore input) and submit it to Flow EVM via Cadence.
    /// Returns both the Flow transaction ID and the EVM transaction hash.
    /// - Parameters:
    ///   - chain: Flow EVM chain (mainnet/testnet only).
    ///   - input: Unsigned Ethereum signing input; chainId is set automatically based on `chain`.
    ///   - fromAddress: Expected EOA sender/coinbase; must match the wallet's derived address for `index`.
    ///   - signers: Flow signers (proposer/authorizers/payer).
    ///   - flowAddress: Optional Flow address for proposer/payer; defaults to the first signer address.
    ///   - payer: Optional custom payer; defaults to proposer.
    ///   - index: HD derivation index for EVM key (defaults to 0).
    /// - Returns: `FlowEVMSubmitResult` containing Flow tx id and EVM tx hash (0x-prefixed).
    public func ethSignTransactionAndSendByCadence(
        chain: EVMChain = .flowMainnet,
        input: EthereumSigningInput,
        fromAddress: String,
        signers: [FlowSigner],
        flowAddress: Flow.Address? = nil,
        payer: Flow.Address? = nil,
        index: UInt32 = 0
    ) async throws -> FlowEVMSubmitResult {
        guard case .key = type else {
            throw FWKError.invaildWalletType
        }
        guard !signers.isEmpty else {
            throw FWKError.emptySignKey
        }

        var signingInput = input
        signingInput.chainID = chain.chainIdData
        guard let fromAddr = AnyAddress(string: fromAddress, coin: .ethereum) else {
            throw FWKError.invaildEVMAddress
        }
        let derivedAddr = try ethAddress(index: index)
        guard fromAddr.description.lowercased() == derivedAddr.lowercased() else {
            throw FWKError.invaildEVMAddress
        }

        let signed = try ethSignTransaction(signingInput, index: index)
        guard let flowChainID = chain.flowChainID else {
            throw FWKError.unsupportedEVMChain
        }
        guard let proposer = flowAddress ?? signers.first?.address else {
            throw FWKError.emptyFlowAddress
        }
        let flowTxId = try await ethSendSignedTransactionByCadence(
            chainId: flowChainID,
            account: proposer,
            rlpEncodedTransaction: signed.encoded,
            coinbaseAddr: fromAddr.description,
            signers: signers,
            payer: payer ?? proposer
        )
        let flowTxIdString = String(describing: flowTxId)
        return FlowEVMSubmitResult(flowTxId: flowTxIdString, evmTxId: signed.txIdHex())
    }
}
