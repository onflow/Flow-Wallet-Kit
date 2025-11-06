//
//  File.swift
//  FlowWalletKit
//
//  Created by Hao Fu on 17/10/2025.
//

import Foundation
import WalletCore

// MARK: - Support EOA

extension Wallet {
    public func getEOAAccount(indexes: [UInt32]? = nil) throws -> [AnyAddress] {
        let key = try resolveEthereumKey()
        let normalizedIndexes = indexes?.isEmpty == false ? indexes! : [0]
        let addresses = try deriveEOAAddresses(from: key, indexes: normalizedIndexes)
        updateEOAAddressCache(with: addresses)
        return addresses
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
            eoaAddress = nil
            return
        }
        
        do {
            let addresses = try deriveEOAAddresses(from: key, indexes: [0])
            updateEOAAddressCache(with: addresses)
        } catch {
            eoaAddress = nil
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
    
    private func updateEOAAddressCache(with addresses: [AnyAddress]) {
        if addresses.isEmpty {
            eoaAddress = nil
            return
        }
        let set = Set(addresses.map { $0.description })
        eoaAddress = set.isEmpty ? nil : set
    }
    
    private func resolveEthereumKey() throws -> EthereumKeyProtocol {
        guard case let .key(rawKey) = type,
              let ethereumKey = rawKey as? EthereumKeyProtocol else {
            throw FWKError.unsupportedEthereumKey
        }
        return ethereumKey
    }
}
