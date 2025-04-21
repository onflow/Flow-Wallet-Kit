package com.flow.wallet.security

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SecurityCheckDelegateTest {
    private class TestSecurityCheckDelegate(
        private val shouldPass: Boolean
    ) : SecurityCheckDelegate {
        override suspend fun verify(): Boolean {
            return kotlinx.coroutines.flow.flow { emit(shouldPass) }
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
    fun testSecurityCheckFlow() = runBlocking { // to-do: no longer uses a coroutine
        val delegate = object : SecurityCheckDelegate {
            override suspend fun verify(): Boolean {
                return kotlinx.coroutines.flow.flow {
                    emit(true)
                    emit(false)
                    emit(true)
                }
            }
        }
        
        val results = mutableListOf<Boolean>()
        delegate.verify().collect { results.add(it) }
        
        assertEquals(listOf(true, false, true), results)
    }
} 