package com.flow.wallet

import android.util.Log
import com.flow.wallet.errors.WalletError

object NativeLibraryManager {
    private const val TAG = "NativeLibraryManager"
    
    @Volatile
    private var isLoaded = false
    
    @Volatile
    private var loadError: Throwable? = null
    
    fun ensureLibraryLoaded(): Boolean {
        if (isLoaded) return true
        
        synchronized(this) {
            if (isLoaded) return true
            
            return try {
                System.loadLibrary("TrustWalletCore")
                isLoaded = true
                Log.d(TAG, "TrustWalletCore loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                loadError = e
                Log.e(TAG, "Failed to load TrustWalletCore: ${e.message}")
                false
            } catch (e: Exception) {
                loadError = e
                Log.e(TAG, "Unexpected error loading TrustWalletCore: ${e.message}")
                false
            }
        }
    }
    
    fun isLibraryAvailable(): Boolean = isLoaded
    
    fun getLoadError(): Throwable? = loadError
    
    fun throwIfNotLoaded() {
        if (!isLoaded) {
            val error = loadError ?: RuntimeException("TrustWalletCore not loaded")
            throw WalletError(WalletError.InitHDWalletFailed.code, "Native library not available: ${error.message}")
        }
    }
} 