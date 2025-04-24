# Flow Wallet Kit Android Example

This example app demonstrates how to use the Flow Wallet Kit SDK for Android. It showcases the main features of the SDK including key generation, account discovery, and linked accounts management.

## Prerequisites

- Android Studio (latest version recommended)
- Android SDK with API level 24 or higher
- Kotlin 1.8 or higher
- Gradle 7.0 or higher

## Setup

1. Clone the repository:
```bash
git clone <repository-url>
cd Flow-Wallet-Kit/Android/example
```

2. Open the project in Android Studio:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the `Android/example` directory and select it

3. Sync the project with Gradle:
   - Click on "Sync Project with Gradle Files" in the toolbar
   - Wait for the sync to complete

4. Build the project:
   - Click on "Build" > "Make Project"
   - Wait for the build to complete

## Running the Example

1. Connect an Android device or start an emulator
2. Click the "Run" button in Android Studio
3. Select your target device
4. Wait for the app to install and launch

## Features Demonstrated

The example app demonstrates three main features of the Flow Wallet Kit SDK:

### 1. Key Generation
- Generates a new key pair using the Android Keystore
- Displays the generated public key
- Uses the `WalletKeyManager` class for key management

### 2. Account Discovery
- Searches for accounts associated with the generated public key
- Uses the `Network` class to interact with the Flow blockchain
- Displays account information including address, key ID, and weight
- Currently configured to use the Testnet network

### 3. Linked Accounts
- Loads and displays linked accounts (child accounts and COA)
- Uses the `Account` class to manage account relationships
- Shows both child accounts and COA information

## Usage Flow

1. **Generate Key Pair**
   - Tap the "Generate Key Pair" button
   - The app will generate a new key pair and display the public key
   - This key will be used for subsequent operations

2. **Find Account**
   - Tap the "Find Account" button
   - The app will search for accounts associated with the generated public key
   - If found, it will display the account information
   - If no accounts are found, it will show an appropriate message

3. **Load Linked Accounts**
   - Tap the "Load Linked Accounts" button
   - The app will load and display any linked accounts
   - Shows both child accounts and COA information if available

## License

This example is provided under the same license as the Flow Wallet Kit SDK.

## Support

For support, please refer to the Flow Wallet Kit documentation or create an issue in the repository. 