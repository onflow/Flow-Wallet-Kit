package com.flow.wallet.security

import com.flow.wallet.errors.WalletError
import com.flow.wallet.keys.KeyProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.onflow.flow.ChainId
import org.onflow.flow.models.Account
import org.onflow.flow.models.Transaction
import kotlin.test.assertFailsWith

class SecurityCheckDelegateTest {
    private class TestSecurityCheckDelegate(
        private val shouldPass: Boolean
    ) : SecurityCheckDelegate {
        override suspend fun verify(): Boolean {
            return shouldPass
        }
    }

    @Test
    fun testSecurityCheckPass() = runBlocking {
        val delegate = TestSecurityCheckDelegate(true)
        val result = delegate.verify()
        assertTrue(result)
    }

    @Test
    fun testSecurityCheckFail() = runBlocking {
        val delegate = TestSecurityCheckDelegate(false)
        val result = delegate.verify()
        assertFalse(result)
    }

    @Test
    fun testMultipleSecurityChecks() = runBlocking {
        val delegates = listOf(
            TestSecurityCheckDelegate(true),
            TestSecurityCheckDelegate(false),
            TestSecurityCheckDelegate(true)
        )
        
        val results = delegates.map { delegate ->
            delegate.verify()
        }
        
        assertEquals(listOf(true, false, true), results)
    }

    @Test
    fun testSecurityCheckFlow() = runBlocking {
        val delegate = object : SecurityCheckDelegate {
            override suspend fun verify(): Boolean {
                return true
            }
        }
        
        val result = delegate.verify()
        assertTrue(result)
    }

    @Test
    fun testSecurityCheckWithSigning(): Unit = runBlocking {
        val delegate = TestSecurityCheckDelegate(true)
        val account = com.flow.wallet.account.Account(
            account = mock(Account::class.java),
            chainID = ChainId.Testnet,
            key = mock(KeyProtocol::class.java),
            securityDelegate = delegate
        )

        // Mock the key signing to succeed
        `when`(account.key?.sign(any(), any(), any())).thenReturn(ByteArray(0))

        // This should not throw an exception since security check passes
        account.sign(mock(Transaction::class.java), ByteArray(0))
    }

    @Test
    fun testSecurityCheckFailureWithSigning(): Unit = runBlocking {
        val delegate = TestSecurityCheckDelegate(false)
        val account = com.flow.wallet.account.Account(
            account = mock(Account::class.java),
            chainID = ChainId.Testnet,
            key = mock(KeyProtocol::class.java),
            securityDelegate = delegate
        )

        // This should throw an exception since security check fails
        assertFailsWith<WalletError> {
            account.sign(mock(Transaction::class.java), ByteArray(0))
        }
    }
} 