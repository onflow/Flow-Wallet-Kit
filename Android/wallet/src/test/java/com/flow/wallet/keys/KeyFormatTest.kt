package com.flow.wallet.keys

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KeyFormatTest {

    @Test
    fun `test key format values`() {
        assertEquals(5, KeyFormat.entries.size)
        assertNotNull(KeyFormat.RAW)
        assertNotNull(KeyFormat.BASE64)
        assertNotNull(KeyFormat.HEX)
        assertNotNull(KeyFormat.KEYSTORE)
        assertNotNull(KeyFormat.PKCS8)
    }

    @Test
    fun `test key format names`() {
        assertEquals("RAW", KeyFormat.RAW.name)
        assertEquals("BASE64", KeyFormat.BASE64.name)
        assertEquals("HEX", KeyFormat.HEX.name)
        assertEquals("KEYSTORE", KeyFormat.KEYSTORE.name)
        assertEquals("PKCS8", KeyFormat.PKCS8.name)
    }

    @Test
    fun `test key format ordinal`() {
        assertEquals(0, KeyFormat.RAW.ordinal)
        assertEquals(1, KeyFormat.BASE64.ordinal)
        assertEquals(2, KeyFormat.HEX.ordinal)
        assertEquals(3, KeyFormat.KEYSTORE.ordinal)
        assertEquals(4, KeyFormat.PKCS8.ordinal)
    }

    @Test
    fun `test key format valueOf`() {
        assertEquals(KeyFormat.RAW, KeyFormat.valueOf("RAW"))
        assertEquals(KeyFormat.BASE64, KeyFormat.valueOf("BASE64"))
        assertEquals(KeyFormat.HEX, KeyFormat.valueOf("HEX"))
        assertEquals(KeyFormat.KEYSTORE, KeyFormat.valueOf("KEYSTORE"))
        assertEquals(KeyFormat.PKCS8, KeyFormat.valueOf("PKCS8"))
    }
} 