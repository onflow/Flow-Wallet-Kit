package io.outblock.wallet.account

import java.math.BigDecimal

/**
 * Represents a token balance for either a Flow or EVM token
 */
data class TokenBalance(
    val tokenType: TokenType,
    val balance: BigDecimal,
    val symbol: String,
    val name: String,
    val decimals: Int
) {
    enum class TokenType {
        FLOW_FT,    // Flow Fungible Token
        FLOW_NFT,   // Flow Non-Fungible Token
        EVM_ERC20,  // EVM ERC20 Token
        EVM_ERC721  // EVM ERC721 Token
    }

    companion object {
        fun formatBalance(balance: BigDecimal, decimals: Int): String {
            return balance.movePointLeft(decimals).toPlainString()
        }
    }
} 