package com.inkvpn.app.data

import android.content.Context
import com.inkvpn.app.core.Hwid
import com.inkvpn.app.core.ParsedSubscription
import com.inkvpn.app.core.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches a subscription URL with the InkVPN headers and parses the response. */
class SubscriptionFetcher(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(url: String): ParsedSubscription = withContext(Dispatchers.IO) {
        val hwid = Hwid.generate(context)
        val locale = context.resources.configuration.locales[0].toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "InkVPN/1.0/android")
            .header("x-app-version", "1.0.0")
            .header("x-device-locale", locale)
            .header("x-client", "InkVPN")
            .header("x-hwid", hwid)
            .header("X-Device-ID", hwid)
            .header("x-device-os", "android")
            .header("x-ver-os", android.os.Build.VERSION.RELEASE)
            .header("x-device-model", android.os.Build.MODEL)
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val headers = mutableMapOf<String, String>()
            for (i in 0 until resp.headers.size) {
                headers[resp.headers.name(i)] = resp.headers.value(i)
            }
            SubscriptionParser.parse(body, headers)
        }
    }
}
