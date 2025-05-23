//package com.flow.wallet
//
//import com.flow.wallet.Network.keyIndexerUrl
//import com.flow.wallet.errors.WalletError
//import io.ktor.client.*
//import io.ktor.client.engine.*
//import io.ktor.client.plugins.contentnegotiation.*
//import io.ktor.client.request.*
//import io.ktor.client.statement.*
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import kotlinx.coroutines.runBlocking
//import kotlinx.serialization.json.*
//import org.junit.After
//import org.junit.Before
//import org.junit.Test
//import org.onflow.flow.ChainId
//import org.onflow.flow.models.HashingAlgorithm
//import org.onflow.flow.models.SigningAlgorithm
//import kotlin.test.assertEquals
//import kotlin.test.assertFalse
//import kotlin.test.assertNotNull
//import kotlin.test.assertNull
//import kotlin.test.assertTrue
//import kotlin.test.assertFailsWith
//
//class NetworkTest {
//    private lateinit var mockClient: HttpClient
//
//    @Before
//    fun setup() {
//        mockClient = HttpClient(TestEngine()) {
//            install(ContentNegotiation) {
//                json()
//            }
//            engine {
//                addHandler { request ->
//                    when (request.url.encodedPath) {
//                        "/v1/accounts" -> {
//                            val publicKey = request.url.parameters["publicKey"]
//                            if (publicKey == "valid-key") {
//                                respond(
//                                    content = """
//                                    {
//                                        "publicKey": "valid-key",
//                                        "accounts": [
//                                            {
//                                                "address": "0x123",
//                                                "keyId": 0,
//                                                "weight": 1000,
//                                                "sigAlgo": 2,
//                                                "hashAlgo": 1,
//                                                "signing": "ECDSA_P256",
//                                                "hashing": "SHA2_256",
//                                                "isRevoked": false
//                                            }
//                                        ]
//                                    }
//                                    """.trimIndent(),
//                                    status = HttpStatusCode.OK,
//                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
//                                )
//                            } else {
//                                respond(
//                                    content = "{}",
//                                    status = HttpStatusCode.NotFound,
//                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
//                                )
//                            }
//                        }
//                        else -> respond(
//                            content = "{}",
//                            status = HttpStatusCode.NotFound,
//                            headers = headersOf(HttpHeaders.ContentType, "application/json")
//                        )
//                    }
//                }
//            }
//        }
//        Network.setHttpClient(mockClient)
//    }
//
//    @After
//    fun cleanup() {
//        Network.resetHttpClient()
//    }
//
//    @Test
//    fun testFindAccountSuccess(): Unit = runBlocking {
//        val response = Network.findAccount("valid-key", ChainId.Testnet)
//        assertEquals("valid-key", response.publicKey)
//        assertEquals(1, response.accounts.size)
//
//        val account = response.accounts[0]
//        assertEquals("0x123", account.address)
//        assertEquals(0, account.keyId)
//        assertEquals(1000, account.weight)
//        assertEquals(SigningAlgorithm.ECDSA_P256, account.signing)
//        assertEquals(HashingAlgorithm.SHA2_256, account.hashing)
//        assertFalse(account.isRevoked)
//    }
//
//    @Test
//    fun testFindAccountByKey(): Unit = runBlocking {
//        val accounts = Network.findAccountByKey("valid-key", ChainId.Testnet)
//        assertEquals(1, accounts.size)
//        assertEquals("0x123", accounts[0].address)
//    }
//
//    @Test
//    fun testFindFlowAccountByKey(): Unit = runBlocking {
//        val accounts = Network.findFlowAccountByKey("valid-key", ChainId.Testnet)
//        assertEquals(1, accounts.size)
//
//        val account = accounts[0]
//        assertEquals("0x123", account.address)
//        assertEquals(1, account.keys?.size)
//
//        val key = account.keys?.first()
//        assertNotNull(key)
//        if (key != null) {
//            assertEquals("0", key.index)
//            assertEquals("valid-key", key.publicKey)
//            assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
//            assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
//            assertEquals("1000", key.weight)
//            assertFalse(key.revoked)
//        }
//    }
//
//    @Test
//    fun testKeyIndexerUrl() {
//        val mainnetUrl = ChainId.Mainnet.keyIndexerUrl("test-key")
//        assertTrue(mainnetUrl.toString().contains("production.key-indexer.flow.com"))
//
//        val testnetUrl = ChainId.Testnet.keyIndexerUrl("test-key")
//        assertTrue(testnetUrl.toString().contains("staging.key-indexer.flow.com"))
//
//        assertFailsWith<Exception> {
//            ChainId.Emulator.keyIndexerUrl("test-key")
//        }
//    }
//
//    @Test
//    fun testAccountResponseConversion(): Unit = runBlocking {
//        val response = Network.findAccount("valid-key", ChainId.Testnet)
//        val flowAccounts = response.accountResponse
//
//        assertEquals(1, flowAccounts.size)
//        val account = flowAccounts[0]
//
//        assertEquals("0x123", account.address)
//        assertEquals("0", account.balance)
//        account.contracts?.let { assertTrue(it.isEmpty()) }
//        assertNull(account.links)
//
//        val key = account.keys?.first()
//        assertNotNull(key)
//        if (key != null) {
//            assertEquals("0", key.index)
//            assertEquals("valid-key", key.publicKey)
//            assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
//            assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
//            assertEquals("1000", key.weight)
//            assertFalse(key.revoked)
//        }
//    }
//}
//
//private class TestEngine : HttpClientEngineFactory<HttpClientEngineConfig> {
//    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
//        return object : HttpClientEngine {
//            override val config: HttpClientEngineConfig = object : HttpClientEngineConfig {
//                override fun toString(): String = "TestEngine"
//            }.apply(block)
//
//            override suspend fun execute(data: HttpRequestData): HttpResponseData {
//                throw UnsupportedOperationException("This engine is only for testing")
//            }
//
//            override fun close() {}
//        }
//    }
//}