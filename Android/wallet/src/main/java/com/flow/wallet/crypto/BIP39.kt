package com.flow.wallet.crypto

import wallet.core.jni.HDWallet
import wallet.core.jni.Mnemonic

/**
 * BIP39 implementation for seed phrase management
 * Provides functionality for:
 * - Generating mnemonic phrases of different lengths
 * - Validating mnemonic phrases
 * - Word validation and suggestions
 */
object BIP39 {
    /**
     * Supported lengths for BIP39 mnemonic phrases
     */
    enum class SeedPhraseLength(val strength: Int) {
        TWELVE(128),  // 12 words (128 bits of entropy)
        FIFTEEN(160), // 15 words (160 bits of entropy)
        TWENTY_FOUR(256) // 24 words (256 bits of entropy)
    }

    /**
     * Generate a new BIP39 mnemonic phrase
     * @param length Length of the mnemonic phrase (default: 12 words)
     * @param passphrase Optional passphrase for additional security
     * @return Generated mnemonic phrase
     */
    fun generate(length: SeedPhraseLength = SeedPhraseLength.TWELVE, passphrase: String = ""): String {
        return HDWallet(length.strength, passphrase).mnemonic()
    }

    /**
     * Validate a complete mnemonic phrase
     * @param mnemonic The mnemonic phrase to validate
     * @return Whether the phrase is valid according to BIP39
     */
    fun isValid(mnemonic: String): Boolean {
        return Mnemonic.isValid(mnemonic)
    }

    /**
     * Validate a single word from the BIP39 wordlist
     * @param word The word to validate
     * @return Whether the word is in the BIP39 wordlist
     */
    fun isValidWord(word: String): Boolean {
        return Mnemonic.isValidWord(word)
    }

    /**
     * Get a suggested completion for a partial word
     * @param prefix The partial word to complete
     * @return A suggested complete word from the BIP39 wordlist
     */
    fun suggest(prefix: String): String {
        return Mnemonic.suggest(prefix)
    }
} 