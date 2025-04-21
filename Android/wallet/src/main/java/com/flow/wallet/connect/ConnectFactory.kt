package com.flow.wallet.connect

import android.content.Context
import android.webkit.WebView
import com.flow.wallet.errors.WalletError

/**
 * Factory class for creating and managing wallet connections
 */
class ConnectFactory(
    private val context: Context,
    private val webView: WebView
) {
    private val fclConnect = FCLConnect(context, webView)
    private val walletConnect = com.flow.wallet.connect.WalletConnect(context)

    /**
     * Get a connection instance for the specified type
     * @param type Connection type (FCL or WalletConnect)
     * @return Connection instance
     * @throws WalletError if connection type is invalid
     */
    fun getConnection(type: com.flow.wallet.connect.ConnectProtocol.ConnectionType): com.flow.wallet.connect.ConnectProtocol {
        return when (type) {
            com.flow.wallet.connect.ConnectProtocol.ConnectionType.FCL -> fclConnect
            com.flow.wallet.connect.ConnectProtocol.ConnectionType.WALLET_CONNECT -> walletConnect
        }
    }

    /**
     * Handle deep link for any connection type
     * @param uri Deep link URI
     * @return Whether the URI was handled
     */
    fun handleDeepLink(uri: String): Boolean {
        return fclConnect.handleDeepLink(uri) || walletConnect.handleDeepLink(uri)
    }

    /**
     * Check if any connection is active
     * @param network Network type to check
     * @return Whether any connection is active
     */
    fun isAnyConnected(network: com.flow.wallet.connect.ConnectProtocol.NetworkType): Boolean {
        return fclConnect.isConnected(com.flow.wallet.connect.ConnectProtocol.ConnectionType.FCL, network) ||
               walletConnect.isConnected(com.flow.wallet.connect.ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
    }

    /**
     * Get all active connections
     * @param network Network type to check
     * @return Map of connection types to their active status
     */
    fun getActiveConnections(network: com.flow.wallet.connect.ConnectProtocol.NetworkType): Map<com.flow.wallet.connect.ConnectProtocol.ConnectionType, Boolean> {
        return mapOf(
            com.flow.wallet.connect.ConnectProtocol.ConnectionType.FCL to fclConnect.isConnected(com.flow.wallet.connect.ConnectProtocol.ConnectionType.FCL, network),
            com.flow.wallet.connect.ConnectProtocol.ConnectionType.WALLET_CONNECT to walletConnect.isConnected(
                com.flow.wallet.connect.ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
        )
    }

    /**
     * Disconnect all active connections
     * @param network Network type to disconnect from
     */
    suspend fun disconnectAll(network: com.flow.wallet.connect.ConnectProtocol.NetworkType) {
        fclConnect.disconnect(com.flow.wallet.connect.ConnectProtocol.ConnectionType.FCL, network)
        walletConnect.disconnect(com.flow.wallet.connect.ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
    }
} 