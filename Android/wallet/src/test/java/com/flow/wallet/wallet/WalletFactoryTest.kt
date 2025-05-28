//package com.flow.wallet.wallet
//
//import com.flow.wallet.CryptoProvider
//import com.flow.wallet.keys.PrivateKey
//import com.flow.wallet.storage.InMemoryStorage
//import com.flow.wallet.storage.StorageProtocol
//import junit.framework.TestCase.assertEquals
//import junit.framework.TestCase.assertNotNull
//import junit.framework.TestCase.assertTrue
//import kotlinx.coroutines.runBlocking
//import org.junit.Test
//import org.onflow.flow.ChainId
//import org.onflow.flow.models.HashingAlgorithm
//import org.onflow.flow.models.Signer
//import org.onflow.flow.models.SigningAlgorithm
//import org.onflow.flow.models.Transaction
//import wallet.core.jni.PrivateKey as TWPrivateKey
//
//class TestCryptoProvider : CryptoProvider {
//    override fun getPublicKey(): String = "test_public_key"
//    override suspend fun getUserSignature(jwt: String): String = "test_signature"
//    override suspend fun signData(data: ByteArray): String = "test_signed_data"
//    override fun getSigner(): Signer = object : Signer {
//        override var address: String = "test_signer"
//        override var keyIndex: Int = 0
//        override suspend fun sign(transaction: Transaction?, bytes: ByteArray): ByteArray {
//            return "test_signature".toByteArray()
//        }
//
//        override suspend fun sign(bytes: ByteArray): ByteArray {
//            return "test_signature".toByteArray()
//        }
//    }
//    override fun getHashAlgorithm(): HashingAlgorithm = HashingAlgorithm.SHA2_256
//    override fun getSignatureAlgorithm(): SigningAlgorithm = SigningAlgorithm.ECDSA_P256
//    override fun getKeyWeight(): Int = 1000
//}
//
//class WalletFactoryTest {
//    private val storage: StorageProtocol = InMemoryStorage()
//    private val testNetworks = setOf(ChainId.Mainnet, ChainId.Testnet)
//    private val testAddress = "0x123"
//    private val testKey = PrivateKey(TWPrivateKey(), storage)
//    private val testCryptoProvider = TestCryptoProvider()
//
//    @Test
//    fun testCreateKeyWallet() = runBlocking {
//        val wallet = WalletFactory.createKeyWallet(testKey, testNetworks, storage)
//
//        assertNotNull(wallet)
//        assertEquals(WalletType.KEY, wallet.type)
//        assertEquals(testNetworks, wallet.networks)
//        assertTrue(wallet is KeyWallet)
//    }
//
//    @Test
//    fun testCreateWatchWallet() = runBlocking {
//        val wallet = WalletFactory.createWatchWallet(testAddress, testNetworks, storage)
//
//        assertNotNull(wallet)
//        assertEquals(WalletType.WATCH, wallet.type)
//        assertEquals(testNetworks, wallet.networks)
//        assertTrue(wallet is WatchWallet)
//    }
//
//    @Test
//    fun testCreateProxyWallet() = runBlocking {
//        val wallet = WalletFactory.createProxyWallet(testCryptoProvider, testNetworks, storage)
//
//        assertNotNull(wallet)
//        assertEquals(WalletType.PROXY, wallet.type)
//        assertEquals(testNetworks, wallet.networks)
//        assertTrue(wallet is ProxyWallet)
//    }
//}