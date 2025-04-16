package io.outblock.wallet.connect

import android.content.Context
import android.webkit.WebView
import io.outblock.wallet.errors.WalletError

/**
 * Factory class for creating and managing wallet connections
 */
class ConnectFactory(
    private val context: Context,
    private val webView: WebView
) {
    private val fclConnect = FCLConnect(context, webView)
    private val walletConnect = WalletConnect(context)

    /**
     * Get a connection instance for the specified type
     * @param type Connection type (FCL or WalletConnect)
     * @return Connection instance
     * @throws WalletError if connection type is invalid
     */
    fun getConnection(type: ConnectProtocol.ConnectionType): ConnectProtocol {
        return when (type) {
            ConnectProtocol.ConnectionType.FCL -> fclConnect
            ConnectProtocol.ConnectionType.WALLET_CONNECT -> walletConnect
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
    fun isAnyConnected(network: ConnectProtocol.NetworkType): Boolean {
        return fclConnect.isConnected(ConnectProtocol.ConnectionType.FCL, network) ||
               walletConnect.isConnected(ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
    }

    /**
     * Get all active connections
     * @param network Network type to check
     * @return Map of connection types to their active status
     */
    fun getActiveConnections(network: ConnectProtocol.NetworkType): Map<ConnectProtocol.ConnectionType, Boolean> {
        return mapOf(
            ConnectProtocol.ConnectionType.FCL to fclConnect.isConnected(ConnectProtocol.ConnectionType.FCL, network),
            ConnectProtocol.ConnectionType.WALLET_CONNECT to walletConnect.isConnected(ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
        )
    }

    /**
     * Disconnect all active connections
     * @param network Network type to disconnect from
     */
    suspend fun disconnectAll(network: ConnectProtocol.NetworkType) {
        fclConnect.disconnect(ConnectProtocol.ConnectionType.FCL, network)
        walletConnect.disconnect(ConnectProtocol.ConnectionType.WALLET_CONNECT, network)
    }
} 