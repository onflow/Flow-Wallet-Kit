package io.outblock.wallet.account.vm

import org.onflow.flow.ChainId
import org.onflow.flow.models.Account
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.FlowID
import org.onflow.flow.models.Signer

/**
 * Represents a Cadence Owned Account (COA) that links a Flow account to an EVM address
 */
class COA(
    private val hexAddr: String,
    val network: ChainId
) : FlowVMProtocol<String> {
    override val address: String
        get() = hexAddr

    override val chainID: ChainId
        get() = network

    override val vm: FlowVM
        get() = FlowVM.EVM

    companion object {
        /**
         * Creates a new COA with the current account as both proposer and payer
         * @param account The account creating the COA
         * @return FlowID of the creation transaction
         */
        suspend fun createCOA(account: Account): FlowID { // to-do: add to flow-kmm models
            return account.flow.createCOA( // needs to be implemented on flow-kmm
                chainID = account.chainID,
                proposer = account.address,
                payer = account.address,
                signers = listOf(account)
            )
        }

        /**
         * Creates a new COA with a specified payer and signers
         * @param account The account creating the COA
         * @param payer The address that will pay for the transaction
         * @param signers List of signers for the transaction
         * @return FlowID of the creation transaction
         */
        suspend fun createCOA(account: Account, payer: FlowAddress, signers: List<Signer>): FlowID {
            return account.flow.createCOA(
                chainID = account.chainID,
                proposer = account.address,
                payer = payer,
                signers = signers
            )
        }

        /**
         * Creates a new COA and waits for the transaction to be sealed
         * @param account The account creating the COA
         * @param payer The address that will pay for the transaction
         * @param signers List of signers for the transaction
         * @return The created COA instance, or null if creation failed
         */
        suspend fun createCOAAndWait(account: Account, payer: FlowAddress, signers: List<Signer>): COA? {
            val id = account.flow.createCOA(
                chainID = account.chainID,
                proposer = account.address,
                payer = payer,
                signers = signers
            )
            account.flow.waitForSeal(id)
            return account.fetchVM()
        }
    }
}

