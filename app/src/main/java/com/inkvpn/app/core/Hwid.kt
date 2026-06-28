package com.inkvpn.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * Hardware ID generation per spec:
 * Android: SHA256(SHA256("androidId|manufacturer|model|brand|device|product|board|hardware") + salt)
 * -> 64 hex chars.
 */
object Hwid {

    private const val SALT = "incy_hwid_"

    @SuppressLint("HardwareIds")
    fun generate(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val raw = listOf(
            androidId, Build.MANUFACTURER, Build.MODEL, Build.BRAND,
            Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE
        ).joinToString("|")
        val first = sha256(raw)
        return sha256(first + SALT)
    }

    fun shortId(full: String): String = full.take(8)

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
