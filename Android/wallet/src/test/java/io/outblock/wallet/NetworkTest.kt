package io.outblock.wallet

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.outblock.wallet.errors.WalletError
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.onflow.flow.ChainId
import org.onflow.flow.models.AccountExpandable
import org.onflow.flow.models.Account as FlowAccount
import org.onflow.flow.models.AccountPublicKey
import org.onflow.flow.models.FlowAddress
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    @Test
    fun testFindAccountSuccess() = runBlocking {
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

    @Test
    fun testFindAccountFailure() = runBlocking {
        assertFailsWith<WalletError.KeyIndexerRequestFailed> {
            Network.findAccount("invalid-key", ChainId.Testnet)
        }
    }

    @Test
    fun testFindAccountByKey() = runBlocking {
        val accounts = Network.findAccountByKey("valid-key", ChainId.Testnet)
        assertEquals(1, accounts.size)
        assertEquals("0x123", accounts[0].address)
    }

    @Test
    fun testFindFlowAccountByKey() = runBlocking {
        val accounts = Network.findFlowAccountByKey("valid-key", ChainId.Testnet)
        assertEquals(1, accounts.size)
        
        val account = accounts[0]
        assertEquals("0x123", account.address)
        assertEquals(1, account.keys?.size)
        
        val key = account.keys?.first()
        assertNotNull(key)
        assertEquals("0", key.index)
        assertEquals("valid-key", key.publicKey)
        assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
        assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
        assertEquals("1000", key.weight)
        assertFalse(key.revoked)
    }

    @Test
    fun testKeyIndexerUrl() {
        val mainnetUrl = ChainId.Mainnet.keyIndexerUrl("test-key")
        assertTrue(mainnetUrl.toString().contains("mainnet.onflow.org"))
        
        val testnetUrl = ChainId.Testnet.keyIndexerUrl("test-key")
        assertTrue(testnetUrl.toString().contains("testnet.onflow.org"))
        
        val emulatorUrl = ChainId.Emulator.keyIndexerUrl("test-key")
        assertTrue(emulatorUrl.toString().contains("localhost:8080"))
        
        assertFailsWith<Exception> {
            ChainId.Unknown.keyIndexerUrl("test-key")
        }
    }

    @Test
    fun testAccountResponseConversion() = runBlocking {
        val response = Network.findAccount("valid-key", ChainId.Testnet)
        val flowAccounts = response.accountResponse
        
        assertEquals(1, flowAccounts.size)
        val account = flowAccounts[0]
        
        assertEquals("0x123", account.address)
        assertEquals("0", account.balance)
        assertTrue(account.contracts.isEmpty())
        assertNull(account.links)
        
        val key = account.keys?.first()
        assertNotNull(key)
        assertEquals("0", key.index)
        assertEquals("valid-key", key.publicKey)
        assertEquals(SigningAlgorithm.ECDSA_P256, key.signingAlgorithm)
        assertEquals(HashingAlgorithm.SHA2_256, key.hashingAlgorithm)
        assertEquals("1000", key.weight)
        assertFalse(key.revoked)
    }
} 