package io.outblock.wallet.account

import org.onflow.flow.ChainId
import org.onflow.flow.models.FlowAddress

/**
 * Represents a child account associated with a parent Flow account
 */
class ChildAccount(
    val address: FlowAddress,
    val network: ChainId,
    val name: String?,
    val description: String?,
    val icon: String?
)