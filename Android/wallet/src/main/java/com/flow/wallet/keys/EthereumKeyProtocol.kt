package com.flow.wallet.keys

/**
 * Capabilities required for keys that can derive Ethereum-compatible (EOA) credentials.
 */
interface EthereumKeyProtocol {
    fun ethAddress(index: Int = 0): String
    fun ethPublicKey(index: Int = 0): ByteArray
    fun ethPrivateKey(index: Int = 0): ByteArray
    fun ethSignDigest(digest: ByteArray, index: Int = 0): ByteArray
}
