package io.outblock.wallet.account

/**
 * Represents token permissions for a child account
 */
data class TokenPermissions(
    val fungibleTokens: List<FungibleTokenPermission>,
    val nonFungibleTokens: List<NonFungibleTokenPermission>
)

/**
 * Represents permissions for a specific fungible token
 */
data class FungibleTokenPermission(
    val tokenAddress: String,
    val tokenName: String,
    val tokenSymbol: String,
    val decimals: Int,
    val permissions: Set<Permission>
)

/**
 * Represents permissions for a specific non-fungible token
 */
data class NonFungibleTokenPermission(
    val tokenAddress: String,
    val tokenName: String,
    val permissions: Set<Permission>
)

/**
 * Enum representing different types of token permissions
 */
enum class Permission { // do we have a pre-set list of token permissions?
    READ,           
    TRANSFER,       
    MINT,           
    BURN   
} 