package io.outblock.wallet.connect

import io.outblock.wallet.account.Account
import org.onflow.flow.ChainId
import org.onflow.flow.models.Transaction

/**
 * Protocol defining wallet connection interfaces
 * Supports FCL and WalletConnect for both Flow and Flow-EVM
 */
interface ConnectProtocol {
    /**
     * Types of wallet connections supported
     */
    enum class ConnectionType {
        FCL,
        WALLET_CONNECT
    }

    /**
     * Types of networks supported
     */
    enum class NetworkType {
        FLOW,
        FLOW_EVM
    }

    /**
     * Connect to a wallet using specified protocol and network
     * @param type Connection type (FCL or WalletConnect)
     * @param network Network type (Flow or Flow-EVM)
     * @return Connected account
     */
    suspend fun connect(type: ConnectionType, network: NetworkType): Result<Account>

    /**
     * Disconnect from a wallet
     * @param type Connection type
     * @param network Network type
     */
    suspend fun disconnect(type: ConnectionType, network: NetworkType)

    /**
     * Sign a transaction
     * @param type Connection type
     * @param network Network type
     * @param transaction Transaction to sign
     * @return Signed transaction
     */
    suspend fun signTransaction(
        type: ConnectionType,
        network: NetworkType,
        transaction: Transaction
    ): Result<ByteArray>

    /**
     * Check if connected to a wallet
     * @param type Connection type
     * @param network Network type
     * @return Whether connected
     */
    fun isConnected(type: ConnectionType, network: NetworkType): Boolean

    /**
     * Get connected account
     * @param type Connection type
     * @param network Network type
     * @return Connected account or null
     */
    fun getConnectedAccount(type: ConnectionType, network: NetworkType): Account?

    /**
     * Handle deep link for connection
     * @param uri Deep link URI
     * @return Whether handled
     */
    fun handleDeepLink(uri: String): Boolean
} 