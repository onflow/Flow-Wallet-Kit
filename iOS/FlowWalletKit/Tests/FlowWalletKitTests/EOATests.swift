@testable import FlowWalletKit
import WalletCore
import XCTest

final class EOATests: XCTestCase {
    private let expectedMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private let expectedMnemonicEthAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    
    private let samplePrivateKeyHex = "9a983cb3d832fbde5ab49d692b7a8bf5b5d232479c99333d0fc8e1d21f1b55b6"
    private let expectedPrivateKeyEthAddress = "0x6Fac4D18c912343BF86fa7049364Dd4E424Ab9C0"

    override func setUp() {
        super.setUp()
        SeedPhraseKey.ethBaseDerivationPath = "m/44'/60'/0'/0/0"
    }

    func testSeedPhraseKeyDerivesEOA() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(expectedMnemonic, storage: storage)

        XCTAssertEqual(try key.ethAddress(), expectedMnemonicEthAddress)

        let publicKey = try key.ethPublicKey()
        XCTAssertEqual(publicKey.count, 65)
        XCTAssertEqual(publicKey.first, 0x04)

        let privateKey = try key.ethPrivateKey()
        XCTAssertEqual(privateKey.count, 32)

        let digest = Hash.keccak256(data: Data("hello world".utf8))
        let signature = try key.ethSign(digest: digest)
        XCTAssertEqual(signature.count, 65)
        XCTAssertTrue(signature.last == 27 || signature.last == 28)
    }

    func testPrivateKeyDerivesEOA() throws {
        let storage = makeEphemeralStorage()
        guard
            let pkData = Data(hexString: samplePrivateKeyHex),
            let corePrivateKey = WalletCore.PrivateKey(data: pkData)
        else {
            XCTFail("Failed to create WalletCore.PrivateKey from sample data")
            return
        }

        let key = PrivateKey(pk: corePrivateKey, storage: storage)
        XCTAssertEqual(try key.ethAddress(), expectedPrivateKeyEthAddress)

        let digest = Hash.keccak256(data: Data("flow".utf8))
        let signature = try key.ethSign(digest: digest)
        XCTAssertEqual(signature.count, 65)
        XCTAssertTrue(signature.last == 27 || signature.last == 28)
    }

    func testWalletCachesEOAAddresses() throws {
        let storage = makeEphemeralStorage()
        guard
            let pkData = Data(hexString: samplePrivateKeyHex),
            let corePrivateKey = WalletCore.PrivateKey(data: pkData)
        else {
            XCTFail("Failed to create WalletCore.PrivateKey from sample data")
            return
        }
        let key = PrivateKey(pk: corePrivateKey, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let addresses = try wallet.getEOAAccount()

        XCTAssertEqual(addresses.count, 1)
        XCTAssertEqual(addresses.first?.description, expectedPrivateKeyEthAddress)
        XCTAssertEqual(wallet.eoaAddress, Set([expectedPrivateKeyEthAddress]))
    }

    func testWalletPersonalSignMatchesDirectSignature() throws {
        let storage = makeEphemeralStorage()
        guard
            let pkData = Data(hexString: samplePrivateKeyHex),
            let corePrivateKey = WalletCore.PrivateKey(data: pkData)
        else {
            XCTFail("Failed to create WalletCore.PrivateKey from sample data")
            return
        }
        let key = PrivateKey(pk: corePrivateKey, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        let message = Data("Flow Wallet".utf8)
        let prefix = "\u{19}Ethereum Signed Message:\n\(message.count)".data(using: .utf8)!
        var payload = Data()
        payload.append(prefix)
        payload.append(message)
        let expectedDigest = Hash.keccak256(data: payload)

        let directSignature = try key.ethSign(digest: expectedDigest)
        let walletSignature = try wallet.ethSignPersonalMessage(message)

        XCTAssertEqual(directSignature, walletSignature)
    }

    func testWalletTypedDataSigningMatchesDirectSignature() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(expectedMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.testnet], cacheStorage: storage)

        let typedData = """
{
    "types": {
        "EIP712Domain": [
            {"name": "name", "type": "string"},
            {"name": "version", "type": "string"},
            {"name": "chainId", "type": "uint256"},
            {"name": "verifyingContract", "type": "address"}
        ],
        "Person": [
            {"name": "name", "type": "string"},
            {"name": "wallets", "type": "address[]"}
        ],
        "Mail": [
            {"name": "from", "type": "Person"},
            {"name": "to", "type": "Person[]"},
            {"name": "contents", "type": "string"}
        ]
    },
    "primaryType": "Mail",
    "domain": {
        "name": "Ether Mail",
        "version": "1",
        "chainId": 1,
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
    },
    "message": {
        "from": {
            "name": "Cow",
            "wallets": [
                "CD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "DeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": [
            {
                "name": "Bob",
                "wallets": [
                    "bBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "B0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                    "B0B0b0b0b0b0B000000000000000000000000000"
                ]
            }
        ],
        "contents": "Hello, Bob!"
    }
}
"""

        let typedDataHash = EthereumAbi.encodeTyped(messageJson: typedData)
        XCTAssertEqual(typedDataHash.hexString, "a85c2e2b118698e88db68a8105b794a8cc7cec074e89ef991cb4f5f533819cc2")

        let directSignature = try key.ethSign(digest: typedDataHash)
        let walletSignature = try wallet.ethSignTypedData(json: typedData)

        XCTAssertEqual(directSignature, walletSignature)
    }

    func testWalletTransactionSigningMatchesWalletCoreExample() throws {
        let storage = makeEphemeralStorage()
        guard
            let pkData = Data(hexString: "0x4646464646464646464646464646464646464646464646464646464646464646"),
            let corePrivateKey = WalletCore.PrivateKey(data: pkData)
        else {
            XCTFail("Failed to create WalletCore.PrivateKey from sample data")
            return
        }
        let key = PrivateKey(pk: corePrivateKey, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        var input = EthereumSigningInput()
        input.chainID = Data(hexString: "01")!
        input.nonce = Data(hexString: "09")!
        input.gasPrice = Data(hexString: "04a817c800")!
        input.gasLimit = Data(hexString: "5208")!
        input.toAddress = "0x3535353535353535353535353535353535353535"
        input.transaction = EthereumTransaction.with {
            $0.transfer = EthereumTransaction.Transfer.with {
                $0.amount = Data(hexString: "0de0b6b3a7640000")!
            }
        }

        let output = try wallet.ethSignTransaction(input)
        XCTAssertEqual(output.encoded.hexString, "f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83")
        let expectedSigningHash = "daf5a779ae972f972197303d7b574746c7ef83eadac0f2791ad23db92e4c8e53"
        let expectedTxHash = Hash.keccak256(data: output.encoded).hexString

        // WalletCore preHash is the signing hash (pre-signing payload hash)
        XCTAssertEqual(output.preHash.hexString, expectedSigningHash)
        // txId uses keccak of the signed/encoded transaction
        XCTAssertEqual(output.txId().hexString, expectedTxHash)
        XCTAssertEqual(output.txIdHex(), "0x" + expectedTxHash)
    }

    func testEcRecoverReturnsExpectedAddress() throws {
        guard let signature = Data(hexString: "0xa77836f00d36b5cd16c17bb26f23cdc78db7928b8d1d1341bd3f11cc279b60a508b80e01992cb0ad9a6c2212177dd84a43535e3bf29794c1dc13d17a59c2d98c1b") else {
            XCTFail("Failed to decode signature")
            return
        }
        let message = Data("Hello, Flow EVM!".utf8)

        let recovered = try Wallet.ethRecoverAddress(signature: signature, message: message)

		XCTAssertEqual(recovered.lowercased(), "0xe513e4f52f76c9bd3db2474e885b8e7e814ea516")
    }

    func testInvalidTypedDataThrows() throws {
        let storage = makeEphemeralStorage()
        let key = try SeedPhraseKey.create(expectedMnemonic, storage: storage)
        let wallet = Wallet(type: .key(key), networks: [.mainnet], cacheStorage: storage)

        XCTAssertThrowsError(try wallet.ethSignTypedData(json: "{}")) { error in
            XCTAssertEqual(error as? FWKError, FWKError.invalidEthereumTypedData)
        }
    }

    func testSignDigestRejectsInvalidLength() throws {
        let storage = makeEphemeralStorage()
        guard
            let pkData = Data(hexString: samplePrivateKeyHex),
            let corePrivateKey = WalletCore.PrivateKey(data: pkData)
        else {
            XCTFail("Failed to create WalletCore.PrivateKey from sample data")
            return
        }
        let key = PrivateKey(pk: corePrivateKey, storage: storage)

        XCTAssertThrowsError(try key.ethSign(digest: Data([0x01]))) { error in
            XCTAssertEqual(error as? FWKError, FWKError.invalidEthereumMessage)
        }
    }

    // MARK: - Helpers

    private func makeEphemeralStorage() -> FileSystemStorage {
        let temporaryDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: temporaryDirectory, withIntermediateDirectories: true, attributes: nil)
        return FileSystemStorage(type: .documentDirectory, directory: temporaryDirectory)
    }
}

private extension Data {
    init?(hexString: String) {
        let cleaned = hexString.lowercased().hasPrefix("0x") ? String(hexString.dropFirst(2)) : hexString
        guard cleaned.count % 2 == 0 else { return nil }

        var result = Data(capacity: cleaned.count / 2)
        var index = cleaned.startIndex
        while index < cleaned.endIndex {
            let nextIndex = cleaned.index(index, offsetBy: 2)
            guard nextIndex <= cleaned.endIndex else { return nil }
            let byteString = cleaned[index..<nextIndex]
            guard let byte = UInt8(byteString, radix: 16) else { return nil }
            result.append(byte)
            index = nextIndex
        }
        self = result
    }
}

private func dataFromHexMinimal(_ hex: String, paddedTo length: Int = 32) -> Data {
    var cleaned = hex.lowercased().hasPrefix("0x") ? String(hex.dropFirst(2)) : hex
    cleaned = cleaned.trimmingCharacters(in: CharacterSet(charactersIn: "0"))
    if cleaned.isEmpty {
        return Data(count: length)
    }
    if cleaned.count % 2 != 0 {
        cleaned = "0" + cleaned
    }
    guard let value = Data(hexString: "0x" + cleaned) else {
        return Data(count: length)
    }
    if value.count >= length {
        return Data(value.suffix(length))
    }
    var padded = Data(count: length - value.count)
    padded.append(value)
    return padded
}
