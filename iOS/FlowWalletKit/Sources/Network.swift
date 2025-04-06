/// FlowWalletKit - Network Layer
///
/// This module handles network communication with Flow blockchain and key indexer services.
/// It provides functionality for:
/// - Finding accounts by public key
/// - Querying key indexer service
/// - Converting between response types

import Flow
import Foundation

// MARK: - Key Indexer Response Types

/// Response structure from the Flow key indexer service
public struct KeyIndexerResponse: Codable {
    /// The public key being queried
    public let publicKey: String
    /// List of accounts associated with this public key
    public let accounts: [Account]

    /// Represents an account entry in the key indexer response
    public struct Account: Codable, Hashable {
        /// The Flow address in hex format
        public let address: String
        /// The key index in the account
        public let keyId: Int
        /// The key weight (determines signing authority)
        public let weight: Int
        /// Signature algorithm identifier
        public let sigAlgo: Int
        /// Hash algorithm identifier
        public let hashAlgo: Int
        /// The signature algorithm used
        public let signing: Flow.SignatureAlgorithm
        /// The hash algorithm used
        public let hashing: Flow.HashAlgorithm
        /// Whether the key has been revoked
        public let isRevoked: Bool
    }
}

// MARK: - Response Transformations

extension KeyIndexerResponse {
    /// Converts the key indexer response to Flow account objects
    /// This transformation aggregates keys by account address
    var accountResponse: [Flow.Account] {
        var response: [Flow.Account] = []
        for account in accounts {
            let index = response.firstIndex { a in
                a.address.hex == account.address
            }
            if let index {
                // Add key to existing account
                response[index].keys.append(
                    .init(index: account.keyId,
                          publicKey: .init(hex: publicKey),
                          signAlgo: account.signing,
                          hashAlgo: account.hashing,
                          weight: account.weight,
                          revoked: account.isRevoked)
                )

            } else {
                // Create new account with first key
                response.append(
                    Flow.Account(address: Flow.Address(hex: account.address),
                                 keys: [Flow.AccountKey(
                                     index: account.keyId,
                                     publicKey: .init(hex: publicKey),
                                     signAlgo: account.signing,
                                     hashAlgo: account.hashing,
                                     weight: account.weight,
                                     revoked: account.isRevoked
                                 )])
                )
            }
        }

        return response
    }
}

// MARK: - Network Operations

/// Network operations for Flow blockchain interactions
public enum Network {
    /// Find account information using the key indexer service
    /// - Parameters:
    ///   - publicKey: The public key to search for
    ///   - chainID: The Flow network to search on
    /// - Returns: Key indexer response containing account information
    /// - Throws: WalletError if the request fails
    public static func findAccount(publicKey: String, chainID: Flow.ChainID) async throws -> KeyIndexerResponse {
        guard let url = chainID.keyIndexer(with: publicKey) else {
            throw WalletError.incorrectKeyIndexerURL
        }
        let urlRequest = URLRequest(url: url)
        let (data, response) = try await URLSession.shared.data(for: urlRequest)

        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw WalletError.keyIndexerRequestFailed
        }

        return try JSONDecoder().decode(KeyIndexerResponse.self, from: data)
    }

    /// Find accounts associated with a public key
    /// - Parameters:
    ///   - publicKey: The public key to search for
    ///   - chainID: The Flow network to search on
    /// - Returns: Array of accounts associated with the key
    public static func findAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [KeyIndexerResponse.Account] {
        let model = try await findAccount(publicKey: publicKey, chainID: chainID)
        return model.accounts
    }

    /// Find Flow accounts associated with a public key
    /// - Parameters:
    ///   - publicKey: The public key to search for
    ///   - chainID: The Flow network to search on
    /// - Returns: Array of Flow accounts associated with the key
    public static func findFlowAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [Flow.Account] {
        let model = try await findAccount(publicKey: publicKey, chainID: chainID)
        return model.accountResponse
    }
}
