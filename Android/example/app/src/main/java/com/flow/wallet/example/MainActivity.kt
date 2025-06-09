package com.flow.wallet.example

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flow.wallet.Network
import com.flow.wallet.account.Account
import com.flow.wallet.example.databinding.ActivityMainBinding
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.storage.InMemoryStorage
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var flowAccount: Account? = null
    private var privateKey: PrivateKey? = null
    private val predefinedPublicKey = "046fbd46016912fde73c70ae7ed4beade32d6e384539d889e226d2c3a30dfd2e783aa6459e96f011565d33aca5a510fe3435e4554c54ee96735f073ce383c71f"
    
    // Storage for the keys using in-memory storage for demo safety
    private lateinit var storage: InMemoryStorage

    companion object {
        init {
            try {
                // Load the Trust Wallet Core native library
                System.loadLibrary("TrustWalletCore")
                Log.d("MainActivity", "TrustWalletCore library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("MainActivity", "Failed to load TrustWalletCore library", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            // Initialize safe storage for demo
            storage = InMemoryStorage()
            Log.d("MainActivity", "Storage initialized successfully")
            
            setupClickListeners()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing app", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnGenerateKey.setOnClickListener {
            generateKeyPair()
        }

        binding.btnFindAccount.setOnClickListener {
            findAccount()
        }

        binding.btnLoadLinkedAccounts.setOnClickListener {
            loadLinkedAccounts()
        }
    }

    private fun generateKeyPair() {
        try {
            Log.d("MainActivity", "Starting key generation...")
            
            // Create a new private key using the modern Flow Wallet Kit API
            privateKey = PrivateKey.create(storage)
            Log.d("MainActivity", "Private key created successfully")
            
            // Get the public key in the correct format
            val publicKeyBytes = privateKey!!.publicKey(SigningAlgorithm.ECDSA_P256)
            val publicKeyHex = publicKeyBytes?.joinToString("") { "%02x".format(it) } ?: ""
            
            binding.tvPublicKey.text = "Public Key: $publicKeyHex"
            Toast.makeText(this, "Key pair generated successfully", Toast.LENGTH_SHORT).show()
            
            Log.d("MainActivity", "Generated new key pair with public key: $publicKeyHex")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error generating key pair", e)
            Toast.makeText(this, "Error generating key pair: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findAccount() {
        // Use the predefined public key for lookup
        binding.tvPublicKey.text = "Public Key: $predefinedPublicKey"
        
        lifecycleScope.launch {
            try {
                Log.d("FindAccount", "Starting account search for key: $predefinedPublicKey")
                val accounts = Network.findAccountByKey(predefinedPublicKey, ChainId.Mainnet)
                Log.d("FindAccount", "Found ${accounts.size} accounts")
                
                if (accounts.isNotEmpty()) {
                    val account = accounts.first()
                    Log.d("FindAccount", "First account details - Address: ${account.address}, KeyId: ${account.keyId}, Weight: ${account.weight}")
                    
                    binding.tvAccountInfo.text = """
                        Address: ${account.address}
                        Key ID: ${account.keyId}
                        Weight: ${account.weight}
                    """.trimIndent()
                    
                    // Create Account instance
                    Log.d("FindAccount", "Searching for Flow accounts")
                    val flowAccounts = Network.findFlowAccountByKey(predefinedPublicKey, ChainId.Mainnet)
                    Log.d("FindAccount", "Found ${flowAccounts.size} Flow accounts")
                    
                    if (flowAccounts.isNotEmpty()) {
                        val flowAccountModel = flowAccounts.first()
                        Log.d("FindAccount", "Creating Account instance with Flow account: ${flowAccountModel.address}")
                        flowAccount = Account(flowAccountModel, ChainId.Mainnet, null)
                        Toast.makeText(this@MainActivity, "Account found successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("FindAccount", "No Flow accounts found")
                        flowAccount = null
                        Toast.makeText(this@MainActivity, "No Flow accounts found for this key", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("FindAccount", "No accounts found")
                    flowAccount = null
                    Toast.makeText(this@MainActivity, "No accounts found for this key", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FindAccount", "Error finding account", e)
                flowAccount = null
                Toast.makeText(this@MainActivity, "Error finding account: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadLinkedAccounts() {
        val account = flowAccount ?: run {
            Toast.makeText(this, "Please find an account first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                account.loadLinkedAccounts()
                
                val childAccounts = account.childs?.joinToString("\n") { 
                    "Child Account: ${it.address} - ${it.name}"
                } ?: "No child accounts"
                
                val coaInfo = account.coa?.let { "COA: ${it.address}" } ?: "No COA"
                
                binding.tvLinkedAccounts.text = """
                    $childAccounts
                    $coaInfo
                """.trimIndent()
                
                Toast.makeText(this@MainActivity, "Linked accounts loaded successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading linked accounts", e)
                Toast.makeText(this@MainActivity, "Error loading linked accounts: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
} 