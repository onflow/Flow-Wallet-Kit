package io.outblock.wallet.account

import org.onflow.flow.ChainId
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.FlowApi

/**
 * Represents a child account associated with a parent Flow account
 */
class ChildAccount(
    val address: FlowAddress,
    val network: ChainId,
    val name: String?,
    val description: String?,
    val icon: String?
) {
    private var _tokenPermissions: TokenPermissions? = null
    val tokenPermissions: TokenPermissions?
        get() = _tokenPermissions

    /**
     * Fetches token permissions for this child account
     */
    suspend fun fetchTokenPermissions(): TokenPermissions {
        // Fetch FT permissions
        val ftPermissions = FlowApi.getFungibleTokenPermissions(network, address) // to-do: implement in flow-kmm
            .map { token ->
                FungibleTokenPermission(
                    tokenAddress = token.address,
                    tokenName = token.name,
                    tokenSymbol = token.symbol,
                    decimals = token.decimals,
                    permissions = token.permissions
                )
            }

        // Fetch NFT permissions
        val nftPermissions = FlowApi.getNonFungibleTokenPermissions(network, address) // to-do: implement in flow-kmm
            .map { token ->
                NonFungibleTokenPermission(
                    tokenAddress = token.address,
                    tokenName = token.name,
                    permissions = token.permissions
                )
            }

        val permissions = TokenPermissions(ftPermissions, nftPermissions)
        _tokenPermissions = permissions
        return permissions
    }

    /**
     * Checks if the account has a specific permission for a token
     * @param tokenAddress The address of the token
     * @param permission The permission to check
     * @param isFungible Whether the token is fungible (true) or non-fungible (false)
     * @return true if the account has the permission, false otherwise
     */
    fun hasPermission(tokenAddress: String, permission: Permission, isFungible: Boolean): Boolean {
        val permissions = _tokenPermissions ?: return false

        return if (isFungible) {
            permissions.fungibleTokens
                .find { it.tokenAddress == tokenAddress }
                ?.permissions
                ?.contains(permission) ?: false
        } else {
            permissions.nonFungibleTokens
                .find { it.tokenAddress == tokenAddress }
                ?.permissions
                ?.contains(permission) ?: false
        }
    }
}