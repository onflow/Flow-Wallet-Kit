package com.flow.wallet.wallet

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.KeyProtocol
import com.flow.wallet.storage.StorageProtocol
import org.onflow.flow.ChainId

/**
 * Factory class for creating different types of wallets
 */
object WalletFactory {
    /**
     * Create a watch wallet initialized with an address only
     * @param address The address to watch
     * @param networks Set of networks to manage (defaults to mainnet and testnet)
     * @param storage Storage implementation for caching
     */
    fun createWatchWallet(
        address: String,
        networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
        storage: StorageProtocol
    ): Wallet {
        return WatchWallet(address, networks, storage)
    }

    /**
     * Create a key wallet initialized with a private key or seed phrase
     * @param key The key protocol implementation
     * @param networks Set of networks to manage (defaults to mainnet and testnet)
     * @param storage Storage implementation for caching
     */
    fun createKeyWallet(
        key: KeyProtocol,
        networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
        storage: StorageProtocol
    ): Wallet {
        return KeyWallet(key, networks, storage)
    }

    /**
     * Create a proxy wallet backed by external devices like Ledger or Passkey
     * @param cryptoProvider The crypto provider implementation
     * @param networks Set of networks to manage (defaults to mainnet and testnet)
     * @param storage Storage implementation for caching
     */
    fun createProxyWallet(
        cryptoProvider: CryptoProvider,
        networks: Set<ChainId> = setOf(ChainId.Mainnet, ChainId.Testnet),
        storage: StorageProtocol
    ): Wallet {
        return ProxyWallet(cryptoProvider, networks, storage)
    }
} 