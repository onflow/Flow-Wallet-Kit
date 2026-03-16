import Foundation
import WalletCore

/// Represents a single Ethereum EOA derived from a BIP44 path.
/// Bundles the address, derivation index, public key, and signing capabilities.
public class EOAAccount {
    /// EIP-55 checksummed Ethereum address
    public let address: String
    /// BIP44 derivation index (m/44'/60'/0'/0/{index})
    public let index: UInt32
    /// Uncompressed secp256k1 public key
    public let publicKey: Data

    private let key: EthereumKeyProtocol

    init(address: String, index: UInt32, publicKey: Data, key: EthereumKeyProtocol) {
        self.address = address
        self.index = index
        self.publicKey = publicKey
        self.key = key
    }

    // MARK: - Signing

    /// Sign a pre-hashed 32-byte digest. Returns [r(32)|s(32)|v(1)].
    public func sign(digest: Data) throws -> Data {
        try key.ethSign(digest: digest, index: index)
    }

    /// EIP-191 personal_sign.
    public func signPersonalMessage(_ message: Data) throws -> Data {
        let prefixString = "\u{19}Ethereum Signed Message:\n\(message.count)"
        guard let prefix = prefixString.data(using: .utf8) else {
            throw FWKError.invalidEthereumMessage
        }
        var payload = Data()
        payload.append(prefix)
        payload.append(message)
        let digest = Hash.keccak256(data: payload)
        return try sign(digest: digest)
    }

    /// EIP-712 signTypedData.
    public func signTypedData(json: String) throws -> Data {
        let digest = EthereumAbi.encodeTyped(messageJson: json)
        guard digest.count == 32 else {
            throw FWKError.invalidEthereumTypedData
        }
        return try sign(digest: digest)
    }

    /// Sign an Ethereum transaction via WalletCore AnySigner.
    public func signTransaction(_ input: EthereumSigningInput) throws -> EthereumSigningOutput {
        var signingInput = input
        signingInput.privateKey = try key.ethPrivateKey(index: index)
        defer { signingInput.privateKey = Data() }
        var output: EthereumSigningOutput = AnySigner.sign(input: signingInput, coin: .ethereum)
        let transactionHash = Hash.keccak256(data: output.encoded)
        output.preHash = transactionHash
        return output
    }

    /// Raw 32-byte secp256k1 private key. Caller is responsible for secure handling.
    public func privateKeyData() throws -> Data {
        try key.ethPrivateKey(index: index)
    }
}
