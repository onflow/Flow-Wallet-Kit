package com.flow.wallet.account.vm

import com.flow.wallet.account.Account
import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.Signer
import org.onflow.flow.models.Transaction

/**
 * Represents a Cadence Owned Account (COA) that links a Flow account to an EVM address
 */
class COA(
    private val hexAddr: String,
    val network: ChainId
) : FlowVMProtocol<String> {

    private class AccountSigner(
        private val account: Account,
        private val key: KeyProtocol
    ) : Signer {
        override var address: String = account.address
        override var keyIndex: Int = 0

        override suspend fun sign(bytes: ByteArray, transaction: Transaction?): ByteArray {
            val signKey = account.findKeyInAccount().firstOrNull() ?: throw WalletError.EmptySignKey
            return key.sign(
                data = bytes,
                signAlgo = signKey.signingAlgorithm,
                hashAlgo = signKey.hashingAlgorithm
            )
        }
    }

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

        suspend fun createCOA(account: Account): String {
            val key = account.key ?: throw WalletError.EmptySignKey
            val signer = AccountSigner(account, key)
            return account.evmManager.createCOAAccount(
                proposer = FlowAddress(account.address),
                payer = FlowAddress(account.address),
                signers = listOf(signer)
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
        suspend fun createCOA(account: Account, payer: FlowAddress, signers: List<Signer>): String {
            return account.evmManager.createCOAAccount(
                proposer = FlowAddress(account.address),
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
        suspend fun createCOAAndWait(account: Account, payer: FlowAddress, signers: List<Signer>): COA {
            val id = account.evmManager.createCOAAccount(
                proposer = FlowAddress(account.address),
                payer = payer,
                signers = signers
            )

            FlowApi(account.chainID).waitForSeal(id)
            return account.fetchVM()
        }
    }
}

