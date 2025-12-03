package com.flow.wallet.wallet

import org.onflow.flow.ChainId

/**
 * Supported EVM chains (Flow EVM plus extensibility for future chains).
 */
sealed class EVMChain(open val chainId: Long) {
    object FlowMainnet : EVMChain(747)
    object FlowTestnet : EVMChain(545)
    data class Custom(override val chainId: Long) : EVMChain(chainId)

    val isFlowEVM: Boolean
        get() = this is FlowMainnet || this is FlowTestnet

    val flowChainId: ChainId?
        get() = when (this) {
            FlowMainnet -> ChainId.Mainnet
            FlowTestnet -> ChainId.Testnet
            is Custom -> null
        }

    fun chainIdData(): ByteArray {
        var value = chainId
        val bytes = mutableListOf<Byte>()
        do {
            bytes.add(0, (value and 0xff).toByte())
            value = value shr 8
        } while (value > 0)
        return bytes.toByteArray()
    }
}

data class FlowEVMSubmitResult(
    val flowTxId: String,
    val evmTxId: String
)

/**
 * Convenience mapping from Flow network to Flow EVM chain (when available).
 */
fun ChainId.evmChain(): EVMChain? = when (this) {
    ChainId.Mainnet -> EVMChain.FlowMainnet
    ChainId.Testnet -> EVMChain.FlowTestnet
    else -> null
}
