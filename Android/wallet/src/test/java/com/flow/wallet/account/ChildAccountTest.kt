package com.flow.wallet.account

import org.junit.Test
import org.junit.Assert.*
import org.onflow.flow.ChainId
import org.onflow.flow.models.FlowAddress

class ChildAccountTest {

    @Test
    fun `test child account creation with all fields`() {
        val address = FlowAddress("0x1234")
        val network = ChainId.Mainnet
        val name = "Test Account"
        val description = "Test Description"
        val icon = "https://example.com/icon.png"

        val childAccount = ChildAccount(
            address = address,
            network = network,
            name = name,
            description = description,
            icon = icon
        )

        assertEquals(address, childAccount.address)
        assertEquals(network, childAccount.network)
        assertEquals(name, childAccount.name)
        assertEquals(description, childAccount.description)
        assertEquals(icon, childAccount.icon)
    }

    @Test
    fun `test child account creation with null optional fields`() {
        val address = FlowAddress("0x5678")
        val network = ChainId.Testnet

        val childAccount = ChildAccount(
            address = address,
            network = network,
            name = null,
            description = null,
            icon = null
        )

        assertEquals(address, childAccount.address)
        assertEquals(network, childAccount.network)
        assertNull(childAccount.name)
        assertNull(childAccount.description)
        assertNull(childAccount.icon)
    }

    @Test
    fun `test token permissions getter`() {
        val childAccount = ChildAccount(
            address = FlowAddress("0x1234"),
            network = ChainId.Mainnet,
            name = null,
            description = null,
            icon = null
        )

        // Initially token permissions should be null
        assertNull(childAccount.tokenPermissions)

        // After setting permissions, they should be accessible
        val permissions = TokenPermissions(
            fungibleTokens = listOf(
                FungibleTokenPermission(
                    tokenAddress = "0x123",
                    tokenName = "Test Token",
                    tokenSymbol = "TEST",
                    decimals = 18,
                    permissions = setOf(Permission.READ)
                )
            ),
            nonFungibleTokens = emptyList()
        )
        childAccount.javaClass.getDeclaredField("_tokenPermissions").apply {
            isAccessible = true
            set(childAccount, permissions)
        }

        assertEquals(permissions, childAccount.tokenPermissions)
    }

    @Test
    fun `test hasPermission for fungible tokens`() {
        val childAccount = ChildAccount(
            address = FlowAddress("0x1234"),
            network = ChainId.Mainnet,
            name = null,
            description = null,
            icon = null
        )

        val permissions = TokenPermissions(
            fungibleTokens = listOf(
                FungibleTokenPermission(
                    tokenAddress = "0x123",
                    tokenName = "Test Token",
                    tokenSymbol = "TEST",
                    decimals = 18,
                    permissions = setOf(Permission.READ, Permission.WRITE)
                )
            ),
            nonFungibleTokens = emptyList()
        )
        childAccount.javaClass.getDeclaredField("_tokenPermissions").apply {
            isAccessible = true
            set(childAccount, permissions)
        }

        // Test existing token with existing permission
        assertTrue(childAccount.hasPermission("0x123", Permission.READ, true))
        assertTrue(childAccount.hasPermission("0x123", Permission.WRITE, true))

        // Test existing token with non-existent permission
        assertFalse(childAccount.hasPermission("0x123", Permission.READ, false)) // Wrong token type

        // Test non-existent token
        assertFalse(childAccount.hasPermission("0x456", Permission.READ, true))
    }

    @Test
    fun `test hasPermission for non-fungible tokens`() {
        val childAccount = ChildAccount(
            address = FlowAddress("0x1234"),
            network = ChainId.Mainnet,
            name = null,
            description = null,
            icon = null
        )

        val permissions = TokenPermissions(
            fungibleTokens = emptyList(),
            nonFungibleTokens = listOf(
                NonFungibleTokenPermission(
                    tokenAddress = "0x123",
                    tokenName = "Test NFT",
                    permissions = setOf(Permission.READ)
                )
            )
        )
        childAccount.javaClass.getDeclaredField("_tokenPermissions").apply {
            isAccessible = true
            set(childAccount, permissions)
        }

        // Test existing token with existing permission
        assertTrue(childAccount.hasPermission("0x123", Permission.READ, false))
        
        // Test existing token with non-existent permission
        assertFalse(childAccount.hasPermission("0x123", Permission.WRITE, false))
        assertFalse(childAccount.hasPermission("0x123", Permission.READ, true)) // Wrong token type

        // Test non-existent token
        assertFalse(childAccount.hasPermission("0x456", Permission.READ, false))
    }

    @Test
    fun `test hasPermission with null token permissions`() {
        val childAccount = ChildAccount(
            address = FlowAddress("0x1234"),
            network = ChainId.Mainnet,
            name = null,
            description = null,
            icon = null
        )

        // When token permissions are null, all permission checks should return false
        assertFalse(childAccount.hasPermission("0x123", Permission.READ, true))
        assertFalse(childAccount.hasPermission("0x123", Permission.WRITE, true))
        assertFalse(childAccount.hasPermission("0x123", Permission.READ, false))
        assertFalse(childAccount.hasPermission("0x123", Permission.WRITE, false))
    }
} 