# Flow Wallet Kit

A cross-platform SDK for integrating Flow blockchain wallet functionality into iOS and Android applications. This SDK provides a secure interface for managing Flow accounts and handling transactions on the Flow blockchain.

## Current Features

### Security & Storage
- **Secure Storage Protocol**: Platform-agnostic storage interface with multiple implementations:
  - iOS:
    - KeychainStorage: Secure key storage using Keychain
    - FileSystemStorage: Encrypted file-based storage
  - Android:
    - HardwareBackedStorage: Hardware-backed secure storage using Android Keystore
    - FileSystemStorage: Encrypted file-based storage
    - InMemoryStorage: Volatile in-memory storage
  - Common Features:
    - Cacheable interface for efficient data access
    - Encrypted storage for sensitive data
    - Hardware-backed storage where available
- **Key Management Protocol**: Unified interface for different key types:
  - Private Key Provider: Direct key management with support for ECDSA P-256 and secp256k1
  - Secure Element Provider: Hardware-backed key storage (Secure Enclave on iOS, Android Keystore)
  - Seed Phrase Provider: BIP-39 mnemonic support with HD wallet derivation
- **Backup**: Basic backup functionality with platform-specific secure storage

### Cryptographic Operations
- **Symmetric Encryption**:
  - AES-GCM: Authenticated encryption with associated data
  - ChaCha20-Poly1305: High-performance authenticated encryption
- **Key Derivation**:
  - BIP-39: Mnemonic phrase generation and validation
  - HD Wallet: Hierarchical deterministic wallet support
- **Hashing**: Secure hash functions for data integrity

### Core Wallet Types
- **Watch Wallet**: Initialize with address only
- **Key Wallet**: Initialize with private key or seed phrase
    - Integration with the Key Indexer API

### Account Management
- **Multi-Account Support**: Manage accounts across different networks
- **Child Accounts**: Manage child accounts with metadata
- **Flow-EVM Integration**: Support for EVM accounts and addresses

## Planned Features

### Balances
- Query token balances for an account's FTs and NFTs

### Proxy Wallet Support
- Backed by external devices (e.g. Ledger)

### Enhanced Backup Support
- Cloud backup provider integration

### Integration Support
- FCL integration
- WalletConnect support

## Documentation

For detailed implementation guides and examples, please refer to:
- [iOS Documentation](iOS/README.md)
- [Android Documentation](Android/README.md)

## Architecture

The SDK is organized into several key components:

### Core Components
- **Storage Protocol**: Platform-agnostic interface for secure data storage
  - iOS:
    - KeychainStorage: Secure key storage using Keychain
    - FileSystemStorage: Encrypted file-based storage
  - Android:
    - HardwareBackedStorage: Hardware-backed secure storage
    - FileSystemStorage: Encrypted file-based storage
    - InMemoryStorage: Volatile in-memory storage
  - Common Features:
    - Cacheable interface for efficient data access
    - Encrypted storage for sensitive data
- **Key Protocol**: Unified interface for key management
  - Private Key Provider: Direct key management with multiple algorithms
  - Secure Element Provider: Hardware-backed key storage
  - Seed Phrase Provider: BIP-39 mnemonic and HD wallet support
- **Crypto**: Cryptographic operations
  - Symmetric encryption with multiple algorithms
  - Key derivation and HD wallet support
  - Secure hashing
- **Wallet**: Core wallet functionality with multiple wallet types
- **Account**: Account management, child accounts, and EVM integration
- **Security**: Cryptographic operations and key management
- **Network**: Interaction with the key indexer API

## Development Status

This SDK is under active development. The current version focuses on secure key management and storage implementations across both iOS and Android platforms, providing a robust foundation for wallet functionality. Additional features are planned for future releases. Please check the platform-specific documentation for the current implementation status of each feature.

## Support

For support, please open an issue in the GitHub repository or contact the maintainers. 