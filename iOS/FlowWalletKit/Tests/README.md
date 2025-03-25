# FlowWalletKit Tests

This directory contains unit tests for the FlowWalletKit package. The tests are written using the Swift Testing framework.

## Running Tests

To run the tests, use the following steps:

1. Open the project in Xcode
2. Make sure to select an iOS Simulator as the target device (tests will not run on physical devices)
3. Select "Product > Test" or use the keyboard shortcut ⌘U

## Test Structure

The tests are organized according to the components they test:

- **Keys**: Tests for the various key implementations (SecureEnclaveKey, PrivateKey, SeedPhraseKey)
- **Storage**: Tests for the storage implementations (KeychainStorage)
- **Wallet**: Tests for the Wallet implementation
- **Crypto**: Tests for cryptographic utilities (BIP39, SymmetricEncryption)

## Mocks and Stubs

Mock implementations are provided in the `Mocks` directory. These are used to isolate components for testing and avoid external dependencies.

## Best Practices

When writing tests:

1. Use descriptive test names that explain what is being tested
2. Follow the "Given-When-Then" pattern for test structure
3. Mock external dependencies
4. Ensure tests run in isolation and don't depend on external services
5. Only test on iOS Simulator to ensure consistency

## Adding New Tests

When adding new tests:

1. Create a new test file in the appropriate directory
2. Import the Testing framework
3. Create a struct containing test methods marked with @Test
4. Follow the existing patterns for test implementation

Example:

```swift
import Foundation
@testable import FlowWalletKit

struct MyComponentTests {
    
    func testFeature() {
        // Given
        let component = MyComponent()
        
        // When
        let result = component.doSomething()
        
        // Then
        #expect(result == expectedResult)
    }
} 
