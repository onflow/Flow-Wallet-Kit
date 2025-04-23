package com.flow.wallet.wallet

import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.InMemoryStorage
import com.flow.wallet.storage.StorageProtocol
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.onflow.flow.ChainId
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.Signer
import org.onflow.flow.models.SigningAlgorithm
import wallet.core.jni.PrivateKey as TWPrivateKey

class TestCryptoProvider : CryptoProvider {
    override fun getPublicKey(): String = "test_public_key"
    override suspend fun getUserSignature(jwt: String): String = "test_signature"
    override suspend fun signData(data: ByteArray): String = "test_signed_data"
    override fun getSigner(): Signer = Signer("test_signer", SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
    override fun getHashAlgorithm(): HashingAlgorithm = HashingAlgorithm.SHA2_256
    override fun getSignatureAlgorithm(): SigningAlgorithm = SigningAlgorithm.ECDSA_P256
    override fun getKeyWeight(): Int = 1000
}

class WalletFactoryTest {
    private val storage: StorageProtocol = InMemoryStorage()
    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
    private val testAddress = "0x123"
    private val testKey = PrivateKey(TWPrivateKey(), storage)
    private val testCryptoProvider = TestCryptoProvider()
    
    @Test
    fun testCreateKeyWallet() = runBlocking {
        val wallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
        
        assertNotNull(wallet)
        assertEquals(WalletType.KEY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is KeyWallet)
    }

    @Test
    fun testCreateWatchWallet() = runBlocking {
        val wallet = WalletFactory.createWatchWallet(testAddress, testNetworks, storage)
        
        assertNotNull(wallet)
        assertEquals(WalletType.WATCH, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is WatchWallet)
    }

    @Test
    fun testCreateProxyWallet() = runBlocking {
        val wallet = WalletFactory.createProxyWallet(testCryptoProvider, testNetworks, storage)
        
        assertNotNull(wallet)
        assertEquals(WalletType.PROXY, wallet.type)
        assertEquals(testNetworks, wallet.networks)
        assertTrue(wallet is ProxyWallet)
    }

    @Test
    fun testCreateWalletWithType() = runBlocking {
        // Test creating KEY wallet
        val keyWallet = WalletFactory.createKeyWallet(WalletType.KEY, testNetworks, storage, testKey)
        assertNotNull(keyWallet)
        assertEquals(WalletType.KEY, keyWallet.type)
        assertTrue(keyWallet is KeyWallet)

        // Test creating WATCH wallet
        val watchWallet = WalletFactory.createWatchWallet(WalletType.WATCH, testNetworks, storage, testAddress)
        assertNotNull(watchWallet)
        assertEquals(WalletType.WATCH, watchWallet.type)
        assertTrue(watchWallet is WatchWallet)

        // Test creating PROXY wallet
        val proxyWallet = WalletFactory.createProxyWallet(WalletType.PROXY, testNetworks, storage, testAddress, testKey)
        assertNotNull(proxyWallet)
        assertEquals(WalletType.PROXY, proxyWallet.type)
        assertTrue(proxyWallet is ProxyWallet)
    }
} 