# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Flow Wallet Kit is a cross-platform SDK for integrating Flow blockchain wallet functionality into iOS and Android applications. It provides secure interfaces for managing Flow accounts and handling transactions across networks.

## Key Commands

### iOS Development
```bash
# Build the iOS library
cd iOS
swift build

# Run iOS tests
swift test

# Run specific test
swift test --filter SEWalletTests

# Open demo app
open FWKDemo/FWKDemo.xcodeproj
```

### Android Development
```bash
# Build Android library
cd Android/wallet
./gradlew build

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests (requires emulator)
./gradlew connectedDebugAndroidTest

# Run specific test
./gradlew testDebugUnitTest --tests "*KeyManagerTest*"

# Build and install example app
cd ../example
./gradlew installDebug
```

### Documentation
```bash
# Development server
cd docs/wallet-docs
bun install
bun dev

# Build docs
bun build
```

## Architecture

### Platform-specific Implementation

**iOS** (`iOS/FlowWalletKit/`)
- Uses Swift Package Manager
- Secure Enclave integration via `SecureElementProvider`
- Keychain storage via `KeychainAccess` library
- Minimum iOS 13+

**Android** (`Android/wallet/`)
- Uses Gradle build system
- Android Keystore hardware security via `HardwareBackedStorage`
- Requires GitHub token for wallet-core dependency (see local.properties setup)
- Minimum SDK 24 (Android 7.0)

### Core Abstractions

1. **Storage Protocol**: Platform-agnostic interface
   - iOS: `KeychainStorage`, `FileSystemStorage`
   - Android: `HardwareBackedStorage`, `FileSystemStorage`, `InMemoryStorage`

2. **Key Management**: Three provider types
   - `PrivateKeyProvider`: Software keys (ECDSA P-256 & secp256k1)
   - `SecureElementProvider`/`HardwareBackedStorage`: Hardware-backed keys
   - `SeedPhraseProvider`: BIP-39 mnemonic phrases with HD wallet support

3. **Wallet Types**
   - `WatchWallet`: Address-only monitoring
   - `KeyWallet`: Full key management with signing capabilities

4. **Account Management**
   - Multi-network support (mainnet, testnet, custom)
   - Child account creation and management
   - COA (Cadence Owned Account) support

### Security Features

- Hardware-backed key storage (Secure Enclave/Android Keystore)
- Multiple encryption options: AES-GCM, ChaCha20-Poly1305
- Secure key derivation with BIP-39 and HD wallet support
- Platform-specific secure storage implementations

### Key Dependencies

**iOS**
- `flow-swift`: Flow blockchain SDK
- `wallet-core`: TrustWallet's HD wallet implementation
- `KeychainAccess`: Secure keychain storage

**Android**
- `flow-kmm`: Flow Kotlin Multiplatform SDK
- `wallet-core`: HD wallet implementation (requires GitHub auth)
- `security-crypto`: Android security library
- `ktor`: HTTP client for networking

### Testing

Both platforms have comprehensive test coverage:
- Unit tests for key management, storage, crypto operations
- Integration tests for wallet operations
- Hardware security tests (platform-specific)

### Publishing

- Android: Published to JitPack as `com.github.onflow:flow-wallet-kit`
- iOS: Available via Swift Package Manager
- Current version: 0.1.6 (Android)

## Development Notes

- When modifying crypto operations, ensure compatibility across both platforms
- Hardware security features should gracefully fall back to software implementations
- Key Indexer API integration is used for account discovery
- The codebase follows clean architecture principles with clear separation between storage, keys, and wallet layers