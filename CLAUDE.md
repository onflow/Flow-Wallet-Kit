# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Flow Wallet Kit is a cross-platform SDK for integrating Flow blockchain wallet functionality into iOS, Android, and TypeScript/JavaScript applications. It provides secure interfaces for managing Flow accounts and handling transactions across networks.

## Key Commands

### TypeScript Development
```bash
# Install dependencies (ALWAYS use pnpm)
cd typescript
pnpm install

# Build the library
pnpm build

# Development mode (watch)
pnpm dev

# Run tests
pnpm test

# Run tests with coverage
pnpm test:coverage

# Type checking
pnpm typecheck

# Linting
pnpm lint

# Clean build artifacts
pnpm clean
```

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

**TypeScript** (`typescript/`)
- Uses ESM modules with Node.js 22+
- Environment-agnostic design (no DOM dependencies)
- Noble crypto libraries for curves and ciphers
- Vitest for testing with full coverage support
- Strict TypeScript configuration

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
   - TypeScript: `InMemoryProvider`, `FileSystemProvider`, `EncryptedStorageProvider`
   - iOS: `KeychainStorage`, `FileSystemStorage`
   - Android: `HardwareBackedStorage`, `FileSystemStorage`, `InMemoryStorage`

2. **Key Management**: Three provider types
   - `PrivateKeyProvider`: Software keys (ECDSA P-256 & secp256k1)
   - `SecureElementProvider`/`HardwareBackedStorage`: Hardware-backed keys (iOS/Android only)
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
- Password-based encryption for vault-style key storage

### Key Dependencies

**TypeScript**
- `@onflow/fcl`: Flow Client Library
- `@noble/curves`: Elliptic curve cryptography
- `@noble/ciphers`: ChaCha20-Poly1305 encryption
- `@noble/hashes`: Hashing functions
- `@scure/bip39` & `@scure/bip32`: HD wallet support

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

All platforms have comprehensive test coverage:
- Unit tests for key management, storage, crypto operations
- Integration tests for wallet operations
- Hardware security tests (platform-specific)
- TypeScript uses Vitest for fast testing

### Publishing

- TypeScript: NPM package as `@onflow/flow-wallet-kit`
- Android: Published to JitPack as `com.github.onflow:flow-wallet-kit`
- iOS: Available via Swift Package Manager
- Current version: 0.1.6 (Android), 0.1.0 (TypeScript)

## Development Notes

- When modifying crypto operations, ensure compatibility across all platforms
- Hardware security features should gracefully fall back to software implementations
- Key Indexer API integration is used for account discovery
- The codebase follows clean architecture principles with clear separation between storage, keys, and wallet layers
- TypeScript implementation is designed to be environment-agnostic (works in Node.js, browsers, React Native)
- FRW-Core keyring service patterns were studied for the TypeScript implementation but not directly copied

## Important Development Guidelines

- **Always use pnpm** for TypeScript package management
- TypeScript code must compile with strict mode enabled
- All imports must use .js extensions for ESM compatibility
- Minimum Node.js version is 22.0.0 for TypeScript development
- Avoid browser-specific APIs to maintain cross-platform compatibility