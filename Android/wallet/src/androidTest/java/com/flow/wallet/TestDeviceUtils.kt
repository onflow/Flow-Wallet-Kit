package com.flow.wallet

import android.os.Build

object TestDeviceUtils {
    /** Returns true if running on an emulator */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MODEL.contains("google_sdk") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk" == Build.PRODUCT
    }
}
