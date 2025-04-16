package io.outblock.wallet.security

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val result = delegate.verify().first()
        assertTrue(result)
    }

    @Test
    fun testSecurityCheckFail() = runBlocking {
        val delegate = TestSecurityCheckDelegate(false)
        val result = delegate.verify().first()
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
            delegate.verify().first()
        }
        
        assertEquals(listOf(true, false, true), results)
    }

    @Test
    fun testSecurityCheckFlow() = runBlocking {
        val delegate = object : SecurityCheckDelegate {
            override suspend fun verify(): Flow<Boolean> {
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