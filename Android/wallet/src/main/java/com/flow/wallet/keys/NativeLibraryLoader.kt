package com.flow.wallet.keys

object NativeLibraryLoader {
    @Volatile
    private var loaded = false

    fun load() {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    System.loadLibrary("TrustWalletCore")
                    loaded = true
                }
            }
        }
    }
}
