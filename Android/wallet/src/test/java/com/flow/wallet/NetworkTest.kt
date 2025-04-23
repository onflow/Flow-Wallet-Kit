package com.flow.wallet

import com.flow.wallet.Network.keyIndexerUrl
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import com.flow.wallet.errors.WalletError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import kotlin.test.assertFailsWith
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

class NetworkTest {
    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/v1/accounts" -> {
                val publicKey = request.url.parameters["publicKey"]
                if (publicKey == "valid-key") {
                    respond(
                        content = """
                        {
                            "publicKey": "valid-key",
                            "accounts": [
                                {
                                    "address": "0x123",
                                    "keyId": 0,
                                    "weight": 1000,
                                    "sigAlgo": 2,
                                    "hashAlgo": 1,
                                    "signing": "ECDSA_P256",
                                    "hashing": "SHA2_256",
                                    "isRevoked": false
                                }
                            ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = "{}",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            else -> respond(
                content = "{}",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    private val mockClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
        }
    }

    @Before
    fun setup() {
        Network.setHttpClient(mockClient)
    }

    @After
    fun cleanup() {
        Network.resetHttpClient()
    }

    @Test
    fun testFindAccountSuccess(): Unit = runBlocking {
        val response = Network.findAccount("valid-key", ChainId.Testnet)
        assertEquals("valid-key", response.publicKey)
        assertEquals(1, response.accounts.size)
        
        val account = response.accounts[0]
        assertEquals("0x123", account.address)
        assertEquals(0, account.keyId)
        assertEquals(1000, account.weight)
        assertEquals(SigningAlgorithm.ECDSA_P256, account.signing)
        assertEquals(HashingAlgorithm.SHA2_256, account.hashing)
        assertFalse(account.isRevoked)
    }

//    @Test
//    fun testFindAccountFailure(): Unit = runBlocking {
//        assertFailsWith<WalletError.KeyIndexerRequestFailed> {
//            Network.findAccount("invalid-key", ChainId.Testnet)
//        }
//    }

    @Test
    fun testFindAccountByKey(): Unit = runBlocking {
        val accounts = Network.findAccountByKey("valid-key", ChainId.Testnet)
        assertEquals(1, accounts.size)
        assertEquals("0x123", accounts[0].address)
    }

    @Test
    fun testFindFlowAccountByKey(): Unit = runBlocking {
        val accounts = Network.findFlowAccountByKey("valid-key", ChainId.Testnet)
        assertEquals(1, accounts.size)
        
        val account = accounts[0]
        assertEquals("0x123", account.address)
        assertEquals(1, account.keys?.size)
        
        val key = account.keys?.first()
        assertNotNull(key)
        if (key != null) {
            assertEquals("0", key.index)
            assertEquals("valid-key", key.publicKey)
            assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
            assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
            assertEquals("1000", key.weight)
            assertFalse(key.revoked)
        }
    }

    @Test
    fun testKeyIndexerUrl() {
        val mainnetUrl = ChainId.Mainnet.keyIndexerUrl("test-key")
        assertTrue(mainnetUrl.toString().contains("production.key-indexer.flow.com"))
        
        val testnetUrl = ChainId.Testnet.keyIndexerUrl("test-key")
        assertTrue(testnetUrl.toString().contains("staging.key-indexer.flow.com"))
        
        assertFailsWith<Exception> {
            ChainId.Emulator.keyIndexerUrl("test-key")
        }
    }

    @Test
    fun testAccountResponseConversion(): Unit = runBlocking {
        val response = Network.findAccount("valid-key", ChainId.Testnet)
        val flowAccounts = response.accountResponse
        
        assertEquals(1, flowAccounts.size)
        val account = flowAccounts[0]
        
        assertEquals("0x123", account.address)
        assertEquals("0", account.balance)
        account.contracts?.let { assertTrue(it.isEmpty()) }
        assertNull(account.links)
        
        val key = account.keys?.first()
        assertNotNull(key)
        if (key != null) {
            assertEquals("0", key.index)
            assertEquals("valid-key", key.publicKey)
            assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
            assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
            assertEquals("1000", key.weight)
            assertFalse(key.revoked)
        }
    }
} 