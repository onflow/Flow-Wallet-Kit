package com.flow.wallet.wallet

import com.flow.wallet.errors.WalletError
import com.google.protobuf.ByteString
import org.onflow.flow.evm.EVMManager
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.Signer
import org.onflow.flow.models.toHexString
import wallet.core.jni.proto.Ethereum

/**
 * Sign an Ethereum transaction via WalletCore and submit it to Flow EVM through Cadence.
 * Returns Flow tx id and EVM tx hash.
 */
suspend fun Wallet.ethSignTransactionAndSendByCadence(
    chain: EVMChain = EVMChain.FlowMainnet,
    input: Ethereum.SigningInput,
    fromAddress: String,
    signers: List<Signer>,
    flowAddress: FlowAddress,
    payer: FlowAddress? = null,
    index: Int = 0
): FlowEVMSubmitResult {
    if (type != WalletType.KEY) throw WalletError.InvalidWalletType
    if (signers.isEmpty()) throw WalletError.EmptySignKey

    val flowChainId = chain.flowChainId ?: throw WalletError.UnsupportedEVMChain

    val normalizedFrom = fromAddress.lowercase()
    val derivedAddr = ethAddress(index).lowercase()
    if (normalizedFrom != derivedAddr) {
        throw WalletError.InvalidEVMAddress
    }

    val signingInput = input.toBuilder().apply {
        chainId = ByteString.copyFrom(chain.chainIdData())
    }.build()

    val signed = ethSignTransaction(signingInput, index)
    val proposer = flowAddress
    val flowTxId = EVMManager(flowChainId).runEVMTransaction(
        proposer = proposer,
        payer = payer ?: proposer,
        rlpEncodedTransaction = signed.encoded.toByteArray(),
        coinbaseAddress = fromAddress,
        signers = signers
    )

    return FlowEVMSubmitResult(
        flowTxId = flowTxId,
        evmTxId = signed.txId().toHexString()
    )
}
