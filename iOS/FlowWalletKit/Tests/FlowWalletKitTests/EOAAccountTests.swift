@testable import FlowWalletKit
import WalletCore
import XCTest

final class EOAAccountTests: XCTestCase {
    private let testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    // Standard BIP44 m/44'/60'/0'/0/0 address for this mnemonic
    private let expectedIndex0Address = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

    override func setUp() {
        super.setUp()
        SeedPhraseKey.ethBaseDerivationPath = "m/44'/60'/0'/0/0"
    }

    // MARK: - Multi EOA Derivation

    func testDeriveMultipleEOAAccounts() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0, 1, 2])

        XCTAssertEqual(accounts.count, 3)
        // Each account should have a unique address
        let addresses = Set(accounts.map { $0.address })
        XCTAssertEqual(addresses.count, 3, "Each derivation index should produce a unique address")

        // Verify indexes match
        XCTAssertEqual(accounts[0].index, 0)
        XCTAssertEqual(accounts[1].index, 1)
        XCTAssertEqual(accounts[2].index, 2)

        // Index 0 should match known address
        XCTAssertEqual(accounts[0].address, expectedIndex0Address)
    }

    func testDefaultIndexIsZero() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts()

        XCTAssertEqual(accounts.count, 1)
        XCTAssertEqual(accounts[0].index, 0)
        XCTAssertEqual(accounts[0].address, expectedIndex0Address)
    }

    // MARK: - EOAAccount Signing

    func testEOAAccountSignDigest() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0])
        let account = accounts[0]

        let digest = Hash.keccak256(data: Data("hello world".utf8))
        let signature = try account.sign(digest: digest)

        XCTAssertEqual(signature.count, 65)
        XCTAssertTrue(signature.last == 27 || signature.last == 28)

        // Should match direct key signing
        let directSignature = try key.ethSign(digest: digest, index: 0)
        XCTAssertEqual(signature, directSignature)
    }

    func testEOAAccountPersonalSign() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0])
        let account = accounts[0]

        let message = Data("Flow Wallet".utf8)
        let signature = try account.signPersonalMessage(message)

        XCTAssertEqual(signature.count, 65)

        // Should match wallet-level personal sign
        let walletSignature = try wallet.ethSignPersonalMessage(message, index: 0)
        XCTAssertEqual(signature, walletSignature)
    }

    func testEOAAccountTypedDataSign() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0])
        let account = accounts[0]

        let typedData = """
{
    "types": {
        "EIP712Domain": [
            {"name": "name", "type": "string"},
            {"name": "version", "type": "string"},
            {"name": "chainId", "type": "uint256"}
        ],
        "Test": [
            {"name": "value", "type": "string"}
        ]
    },
    "primaryType": "Test",
    "domain": {
        "name": "Test",
        "version": "1",
        "chainId": 1
    },
    "message": {
        "value": "hello"
    }
}
"""

        let signature = try account.signTypedData(json: typedData)
        XCTAssertEqual(signature.count, 65)

        // Should match wallet-level typed data sign
        let walletSignature = try wallet.ethSignTypedData(json: typedData, index: 0)
        XCTAssertEqual(signature, walletSignature)
    }

    func testDifferentIndexesProduceDifferentSignatures() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0, 1])

        let digest = Hash.keccak256(data: Data("test message".utf8))
        let sig0 = try accounts[0].sign(digest: digest)
        let sig1 = try accounts[1].sign(digest: digest)

        XCTAssertNotEqual(sig0, sig1, "Different derivation indexes should produce different signatures")
    }

    func testEOAAccountPublicKey() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0])
        let account = accounts[0]

        // Uncompressed secp256k1 public key is 65 bytes (04 prefix + 32x + 32y)
        XCTAssertEqual(account.publicKey.count, 65)
        XCTAssertEqual(account.publicKey.first, 0x04)
    }

    func testEOAAccountTransactionSigning() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0])
        let account = accounts[0]

        var input = EthereumSigningInput()
        input.chainID = Data([0x01])
        input.nonce = Data([0x09])
        input.gasPrice = Data([0x04, 0xa8, 0x17, 0xc8, 0x00])
        input.gasLimit = Data([0x52, 0x08])
        input.toAddress = "0x3535353535353535353535353535353535353535"
        input.transaction = EthereumTransaction.with {
            $0.transfer = EthereumTransaction.Transfer.with {
                $0.amount = Data([0x0d, 0xe0, 0xb6, 0xb3, 0xa7, 0x64, 0x00, 0x00])
            }
        }

        let output = try account.signTransaction(input)
        XCTAssertFalse(output.encoded.isEmpty)

        // Should match wallet-level signing
        let walletOutput = try wallet.ethSignTransaction(input, index: 0)
        XCTAssertEqual(output.encoded, walletOutput.encoded)
    }

    func testEOAAccountPrivateKey() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [0, 1])

        let pk0 = try accounts[0].privateKeyData()
        let pk1 = try accounts[1].privateKeyData()

        XCTAssertEqual(pk0.count, 32)
        XCTAssertEqual(pk1.count, 32)
        XCTAssertNotEqual(pk0, pk1)
    }

    // MARK: - Address Map

    func testEOAAddressMapUpdatedOnDerive() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        // Map should have index 0 from init (refreshEOAAddresses)
        XCTAssertEqual(wallet.eoaAddressMap[0], expectedIndex0Address)

        // Derive more accounts
        let accounts = try wallet.getEOAAccounts(indexes: [0, 1, 2])

        XCTAssertEqual(wallet.eoaAddressMap.count, 3)
        XCTAssertEqual(wallet.eoaAddressMap[0], accounts[0].address)
        XCTAssertEqual(wallet.eoaAddressMap[1], accounts[1].address)
        XCTAssertEqual(wallet.eoaAddressMap[2], accounts[2].address)
    }

    func testEOAAddressMapLookupByIndex() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        _ = try wallet.getEOAAccounts(indexes: [0, 5, 10])

        // Can look up any derived address by index
        XCTAssertNotNil(wallet.eoaAddressMap[0])
        XCTAssertNotNil(wallet.eoaAddressMap[5])
        XCTAssertNotNil(wallet.eoaAddressMap[10])
        // Non-derived index should be nil
        XCTAssertNil(wallet.eoaAddressMap[3])
    }

    // MARK: - Edge Cases

    func testEmptyIndexesDefaultsToZero() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [])

        XCTAssertEqual(accounts.count, 1)
        XCTAssertEqual(accounts[0].index, 0)
    }

    func testLargeDerivationIndex() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(testMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let accounts = try wallet.getEOAAccounts(indexes: [100])
        XCTAssertEqual(accounts.count, 1)
        XCTAssertEqual(accounts[0].index, 100)
        // Should still produce a valid address (0x-prefixed, 42 chars)
        XCTAssertTrue(accounts[0].address.hasPrefix("0x"))
        XCTAssertEqual(accounts[0].address.count, 42)
    }

    // MARK: - Helpers

    private func makeEphemeralStorage() -> FileSystemStorage {
        let temporaryDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: temporaryDirectory, withIntermediateDirectories: true, attributes: nil)
        return FileSystemStorage(type: .documentDirectory, directory: temporaryDirectory)
    }
}
