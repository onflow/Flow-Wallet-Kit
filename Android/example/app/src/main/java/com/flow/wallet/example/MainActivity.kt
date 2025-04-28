package com.flow.wallet.example

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flow.wallet.Network
import com.flow.wallet.KeyManager
import com.flow.wallet.account.Account
import com.flow.wallet.example.databinding.ActivityMainBinding
import com.flow.wallet.toFormatString
import kotlinx.coroutines.launch
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var flowAccount: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
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
            val keyPair = KeyManager.generateKeyWithPrefix("example_key")
            val publicKey = keyPair.public.toFormatString()
            
            binding.tvPublicKey.text = "Public Key: $publicKey"
            Toast.makeText(this, "Key pair generated successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating key pair: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findAccount() {
        val publicKey = binding.tvPublicKey.text.toString().removePrefix("Public Key: ")
        if (publicKey.isEmpty()) {
            Toast.makeText(this, "Please generate a key pair first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val accounts = Network.findAccountByKey(publicKey, ChainId.Testnet)
                if (accounts.isNotEmpty()) {
                    val account = accounts.first()
                    binding.tvAccountInfo.text = """
                        Address: ${account.address}
                        Key ID: ${account.keyId}
                        Weight: ${account.weight}
                    """.trimIndent()
                    
                    // Create Account instance
                    val flowApi = FlowApi(ChainId.Testnet)
                    val flowAccountModel = Network.findFlowAccountByKey(publicKey, ChainId.Testnet).first()
                    flowAccount = Account(flowAccountModel, ChainId.Testnet, null)
                    
                    Toast.makeText(this@MainActivity, "Account found successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "No accounts found for this key", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.println(Log.WARN, "FindAccount", e.message!!)
                Toast.makeText(this@MainActivity, "Error finding account: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@MainActivity, "Error loading linked accounts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 