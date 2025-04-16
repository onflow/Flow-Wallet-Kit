package io.outblock.wallet.keys

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(JUnit4::class)
class KeyFormatTest {

    @Test
    fun `test key format values`() {
        assertEquals(2, KeyFormat.values().size)
        assertNotNull(KeyFormat.PKCS8)
        assertNotNull(KeyFormat.RAW)
    }

    @Test
    fun `test key format names`() {
        assertEquals("PKCS8", KeyFormat.PKCS8.name)
        assertEquals("RAW", KeyFormat.RAW.name)
    }

    @Test
    fun `test key format ordinal`() {
        assertEquals(0, KeyFormat.PKCS8.ordinal)
        assertEquals(1, KeyFormat.RAW.ordinal)
    }

    @Test
    fun `test key format valueOf`() {
        assertEquals(KeyFormat.PKCS8, KeyFormat.valueOf("PKCS8"))
        assertEquals(KeyFormat.RAW, KeyFormat.valueOf("RAW"))
    }
} 