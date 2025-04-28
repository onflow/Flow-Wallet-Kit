package com.flow.wallet.account

import org.onflow.flow.ChainId
import org.onflow.flow.models.FlowAddress

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