package com.flow.wallet.account.vm
import org.onflow.flow.ChainId

/**
 * Enum representing different virtual machine types supported by Flow
 */
enum class FlowVM {
    EVM
}

/**
 * Interface for virtual machine protocol implementations
 */
interface FlowVMProtocol<VMAddress> {
    val vm: FlowVM
    val address: VMAddress
    val chainID: ChainId
}