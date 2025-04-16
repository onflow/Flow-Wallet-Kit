package io.outblock.wallet.account

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
    val decimals: Int
) {
    /**
     * Enum representing different types of tokens
     */
    @Serializable
    enum class TokenType {
        /**
         * Flow native token
         */
        FLOW_FT,
        
        /**
         * Flow fungible tokens
         */
        FLOW_FUNGIBLE,
        
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