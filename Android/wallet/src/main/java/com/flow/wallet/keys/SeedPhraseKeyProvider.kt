package com.flow.wallet.keys

/**
 * Interface for seed phrase key providers
 * Provides functionality for managing BIP39 seed phrase based keys
 */
interface SeedPhraseKeyProvider : KeyProtocol {
    /**
     * Get the mnemonic words
     * @return Array of mnemonic words
     */
    val mnemonic: List<String>

    /**
     * Get the derivation path
     * @return BIP44 derivation path
     */
    val derivationPath: String

    /**
     * Derive a key at specified index
     * @param index Derivation index
     * @return Derived key
     */
    fun deriveKey(index: Int): KeyProtocol
} 