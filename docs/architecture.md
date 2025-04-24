# Flow Wallet Kit - Architecture

Overview

Flow Wallet Kit is a mobile framework that provides a comprehensive solution for integrating Flow blockchain wallets into mobile applications. It handles key management, account operations, and blockchain interactions with a clean, modular architecture.

This document outlines the key components and their relationships in the implementation of Flow Wallet Kit. We take swift as the example.

### Architecture Diagram

```
┌─────────────────────────┐         ┌─────────────────────────┐
│    StorageProtocol      │◄────────┤ KeychainStorage         │
│    (Data Persistence)   │         │ (Implementation)        │
└─────────────────────────┘         └─────────────────────────┘
            ▲
            │ uses
┌───────────┴─────────────┐
│      KeyProtocol        │
│  (Cryptographic Keys)   │
└───────────┬─────────────┘
            │ implements
            ▼
┌─────────────────────────┐         ┌─────────────────────────┐
│  Key Implementations    │◄────────┤ - PrivateKey           │
│                         │         │ - SecureEnclaveKey     │
└─────────────────────────┘         │ - SeedPhraseKey        │
            │                       └─────────────────────────┘
            │ powers
            ▼
┌─────────────────────────┐
│       Wallet            │
│    (Main API Class)     │
└───────────┬─────────────┘
            │ manages
            ▼
┌─────────────────────────┐         ┌─────────────────────────┐
│       Account           │◄────────┤ - Regular Account       │
│ (Blockchain Identity)   │         │ - Child Accounts        │
└───────────┬─────────────┘         │ - VM Accounts          │
            │                       └─────────────────────────┘
            │ interacts with
            ▼
┌─────────────────────────┐
│       Network           │
│ (Blockchain API Client) │
└─────────────────────────┘
```

### Core Components

#### Storage System

**StorageProtocol**

Core interface for data persistence operations.

```swift
public protocol StorageProtocol {
    var allKeys: [String] { get }
    func findKey(_ keyword: String) throws -> [String]
    func get(_ key: String) throws -> Data?
    func set(_ key: String, value: Data) throws
    func remove(_ key: String) throws
    func removeAll() throws
}
```

**KeychainStorage**

Implementation of StorageProtocol using iOS Keychain for secure storage.

```swift
public class KeychainStorage: StorageProtocol {
    public let keychain: Keychain
    
    public init(service: String) {
        keychain = Keychain(service: service)
    }
    
    // Implementation of StorageProtocol methods
}
```

#### Key Management

**KeyProtocol**

Base interface for cryptographic keys with methods for creation, retrieval, and signing.

```swift
public protocol KeyProtocol: Identifiable {
    associatedtype Key
    associatedtype Secret
    associatedtype Advance
    
    var keyType: KeyType { get }
    var storage: StorageProtocol { set get }
    
    static func create(_ advance: Advance, storage: StorageProtocol) throws -> Key
    static func create(storage: StorageProtocol) throws -> Key
    static func createAndStore(id: String, password: String, storage: StorageProtocol) throws -> Key
    static func get(id: String, password: String, storage: StorageProtocol) throws -> Key
    static func restore(secret: Secret, storage: StorageProtocol) throws -> Key
    
    func store(id: String, password: String) throws
    func publicKey(signAlgo: Flow.SignatureAlgorithm) -> Data?
    func privateKey(signAlgo: Flow.SignatureAlgorithm) -> Data?
    func sign(data: Data, signAlgo: Flow.SignatureAlgorithm, hashAlgo: Flow.HashAlgorithm) throws -> Data
    // Additional methods...
}
```

Key types supported:

* `secureEnclave`: Uses Apple's Secure Enclave
* `seedPhrase`: Mnemonic-based (BIP39)
* `privateKey`: Standard private key
* `keyStore`: Key store file

**Key Implementations**

1.  **PrivateKey**: Standard private key implementation

    ```swift
    public class PrivateKey: KeyProtocol {
        public var keyType: KeyType = .privateKey
        public var storage: StorageProtocol
        
        // Properties for both P256 and secp256k1 keys
        // Implementation of key management and signing operations
    }
    ```
2.  **SecureEnclaveKey**: Uses Apple's Secure Enclave for enhanced security

    ```swift
    public class SecureEnclaveKey: KeyProtocol {
        public var keyType: KeyType = .secureEnclave
        public var storage: StorageProtocol
        
        // Implementation using SecureEnclave
    }
    ```
3.  **SeedPhraseKey**: Mnemonic phrase-based key generation

    ```swift
    public class SeedPhraseKey: KeyProtocol {
        public var keyType: KeyType = .seedPhrase
        public var storage: StorageProtocol
        
        // BIP39 implementation
    }
    ```

#### Account Management

**Account**

Represents a Flow blockchain account with signing capabilities.

```swift
public class Account {
    var childs: [Account]? // Child accounts such as dapper wallet
    var vm: [Account]? // VM account such as flow-EVM Account
    
    let account: Flow.Account
    let key: (any KeyProtocol)?
    
    // Methods for working with accounts and signing transactions
}
```

Key features:

* Contains Flow.Account reference
* Has optional key reference for signing operations
* Implements FlowSigner protocol for transaction signing
* Methods to find compatible keys
* Support for child accounts and VM accounts

**Wallet**

Main wallet class that manages accounts across different networks.

```swift
public class Wallet: ObservableObject {
    public let type: WalletType
    public var networks: Set<Flow.ChainID>
    
    @Published
    public var accounts: [Flow.ChainID: [Account]]?
    
    // Methods for account fetching, network operations, and caching
}
```

Wallet Types:

```swift
public enum WalletType {
    case key(any KeyProtocol)
    case watch(Flow.Address)
    // case redirect (Ledger, Hardware wallet)
}
```

Key features:

* Manages accounts across different networks
* Handles account fetching and caching
* Support for watch-only and signing wallets

#### Network

Manages blockchain API interactions and account discovery by looking up account from Key Indexer API.

```swift
public enum Network {
    public static func findAccount(publicKey: String, chainID: Flow.ChainID) async throws -> KeyIndexerResponse
    public static func findAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [KeyIndexerResponse.Account]
    public static func findFlowAccountByKey(publicKey: String, chainID: Flow.ChainID) async throws -> [Flow.Account]
}
```

Response structure:

```swift
public struct KeyIndexerResponse: Codable {
    public let publicKey: String
    public let accounts: [Account]
    
    public struct Account: Codable, Hashable {
        public let address: String
        public let keyId: Int
        public let weight: Int
        // Additional properties
    }
}
```

### Key Features

1. **Multi-network support**: Works with Flow mainnet, testnet, and other networks
2. **Multiple key types**: Supports various key security methods
3. **Account hierarchy**: Manages main accounts, child accounts, and VM accounts
4. **Transaction signing**: Implements Flow signing protocols
5. **Data persistence**: Secure storage for keys and account information

### Main Workflow

1.  Initialize FWKManager with storage configuration:

    ```swift
    FWKManager.setup(Config(storage: KeychainStorage(service: "com.flowwalletkit")))
    ```
2.  Create or retrieve keys:

    ```swift
    let key = try SeedPhraseKey.create(storage: FWKManager.shared.storage)
    // or
    let key = try PrivateKey.get(id: "saved-key-id", password: "user-password", storage: FWKManager.shared.storage)
    ```
3.  Create a Wallet instance:

    ```swift
    let wallet = Wallet(type: .key(key), networks: [.mainnet, .testnet])
    ```
4.  Access accounts:

    ```swift
    if let mainnetAccounts = wallet.accounts?[.mainnet] {
        // Use accounts
    }
    ```
5.  Sign transactions:

    ```swift
    let account = wallet.accounts?[.mainnet]?.first
    let signature = try await account?.sign(transaction: txn, signableData: signableData)
    ```

### Error Handling

The library provides a comprehensive error type:

```swift
public enum WalletError: Error {
    case emptySignKey
    case invaildWalletType
    case incorrectKeyIndexerURL
    case keyIndexerRequestFailed
    case loadCacheFailed
    case noImplement
    // Additional error cases
}
```

### Security Considerations

* Keys are securely stored in the Keychain
* Secure Enclave option for maximum hardware security
* Password-based encryption for sensitive data
* BIP39 standard for seed phrase management

### Conclusion

Flow Wallet Kit provides a comprehensive, secure, and flexible framework for building Flow blockchain wallets on mobile. Its modular architecture supports various key management approaches and security requirements, making it suitable for a wide range of wallet applications.
