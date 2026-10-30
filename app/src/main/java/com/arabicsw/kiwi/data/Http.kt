package com.arabicsw.kiwi.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** WebSocket client: no read timeout, the stream is long-lived. */
    val wsClient: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun getText(url: String, charset: Charset = Charsets.UTF_8): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ArabicSW-Android/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} — $url")
            val bytes = resp.body?.bytes() ?: throw IOException("Empty body — $url")
            return String(bytes, charset)
        }
    }
}
