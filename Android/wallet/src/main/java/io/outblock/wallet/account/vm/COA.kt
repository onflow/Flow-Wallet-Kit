package io.outblock.wallet.account.vm

import io.outblock.wallet.account.Account
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.models.FlowAddress
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
         * @return ByteArray of the creation transaction
         */
        suspend fun createCOA(account: Account): ByteArray {
            return FlowApi.createCOA( // implemented
                chainID = account.chainID,
                proposer = account.address,
                payer = account.address,
                signers = listOf(account)
            )
        }

        /**
         * Creates a new COA instance from an EVM address
         * @param address The EVM address
         * @param network The chain ID for the network
         * @return The created COA instance
         */
        fun createCOA(address: String, network: ChainId): COA {
            return COA(address, network)
        }

        /**
         * Creates a new COA with a specified payer and signers
         * @param account The account creating the COA
         * @param payer The address that will pay for the transaction
         * @param signers List of signers for the transaction
         * @return ByteArray of the creation transaction
         */
        suspend fun createCOA(account: Account, payer: FlowAddress, signers: List<Signer>): ByteArray {
            return FlowApi.createCOA( // implemented
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
            val id = FlowApi.createCOA( // implemented
                chainID = account.chainID,
                proposer = account.address,
                payer = payer,
                signers = signers
            )

            FlowApi(account.chainID).waitForSeal(id) // implemented
            return account.fetchVM()
        }
    }
}

