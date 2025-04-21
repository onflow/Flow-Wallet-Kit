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
} 