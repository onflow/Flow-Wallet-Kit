package com.flow.wallet.account

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Represents a token balance for a specific token type
 */
@Serializable
data class TokenBalance(
    /**
     * The type of token
     */
    val tokenType: TokenType,
    
    /**
     * The balance amount as a string (to handle large numbers)
     */
    val balance: String,
    
    /**
     * The token symbol (e.g., "FLOW", "ETH")
     */
    val symbol: String,
    
    /**
     * The token name (e.g., "Flow Token", "Ethereum")
     */
    val name: String,
    
    /**
     * The number of decimal places for the token
     */
    val decimals: Int,

    /**
     * The Flow identifier for the token (e.g., contract address or resource path)
     * Used to identify the token on the Flow blockchain
     */
    val flowIdentifier: String? = null,

    /**
     * The EVM address of the token (e.g., ERC20 contract address)
     * Used to identify the token on EVM-compatible chains
     */
    val evmAddress: String? = null
) {
    /**
     * Enum representing different types of tokens
     */
    @Serializable
    enum class TokenType {
        /**
         * Flow fungible tokens
         */
        FLOW_FT,
        /**
         * Flow non-fungible tokens
         */
        FLOW_NFT,
        
        /**
         * EVM ERC20 tokens
         */
        EVM_ERC20,
        
        /**
         * EVM ERC721 tokens
         */
        EVM_ERC721
    }

    companion object {
        fun formatBalance(balance: String, decimals: Int): String {
            return BigDecimal(balance).movePointLeft(decimals).toPlainString()
        }
    }
} 