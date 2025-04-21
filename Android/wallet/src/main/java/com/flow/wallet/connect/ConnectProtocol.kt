package com.flow.wallet.connect

import com.flow.wallet.account.Account
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
    suspend fun connect(type: com.flow.wallet.connect.ConnectProtocol.ConnectionType, network: com.flow.wallet.connect.ConnectProtocol.NetworkType): Result<Account>

    /**
     * Disconnect from a wallet
     * @param type Connection type
     * @param network Network type
     */
    suspend fun disconnect(type: com.flow.wallet.connect.ConnectProtocol.ConnectionType, network: com.flow.wallet.connect.ConnectProtocol.NetworkType)

    /**
     * Sign a transaction
     * @param type Connection type
     * @param network Network type
     * @param transaction Transaction to sign
     * @return Signed transaction
     */
    suspend fun signTransaction(
        type: com.flow.wallet.connect.ConnectProtocol.ConnectionType,
        network: com.flow.wallet.connect.ConnectProtocol.NetworkType,
        transaction: Transaction
    ): Result<ByteArray>

    /**
     * Check if connected to a wallet
     * @param type Connection type
     * @param network Network type
     * @return Whether connected
     */
    fun isConnected(type: com.flow.wallet.connect.ConnectProtocol.ConnectionType, network: com.flow.wallet.connect.ConnectProtocol.NetworkType): Boolean

    /**
     * Get connected account
     * @param type Connection type
     * @param network Network type
     * @return Connected account or null
     */
    fun getConnectedAccount(type: com.flow.wallet.connect.ConnectProtocol.ConnectionType, network: com.flow.wallet.connect.ConnectProtocol.NetworkType): Account?

    /**
     * Handle deep link for connection
     * @param uri Deep link URI
     * @return Whether handled
     */
    fun handleDeepLink(uri: String): Boolean
} 