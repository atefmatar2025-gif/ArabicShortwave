package com.arabicsw.kiwi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/** A radio station streamed over the internet (FM and satellite stations included). */
data class OnlineStation(
    val uuid: String,
    val name: String,
    val url: String,
    val country: String,
    val countryCode: String,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val hls: Boolean,
) {
    val info: String
        get() = listOfNotNull(
            country.takeIf { it.isNotBlank() },
            tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString("، ").takeIf { it.isNotBlank() },
        ).joinToString(" • ")

    val quality: String
        get() = listOfNotNull(codec.takeIf { it.isNotBlank() }, bitrate.takeIf { it > 0 }?.let { "$it kbps" })
            .joinToString(" ")

    fun toJson(): JSONObject = JSONObject()
        .put("stationuuid", uuid).put("name", name).put("url_resolved", url)
        .put("country", country).put("countrycode", countryCode).put("tags", tags)
        .put("codec", codec).put("bitrate", bitrate).put("hls", if (hls) 1 else 0)

    companion object {
        fun fromJson(o: JSONObject): OnlineStation? {
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
            val name = o.optString("name").trim()
            if (!url.startsWith("http") || name.isEmpty()) return null
            return OnlineStation(
                uuid = o.optString("stationuuid").ifBlank { url },
                name = name,
                url = url,
                country = o.optString("country").trim(),
                countryCode = o.optString("countrycode").trim(),
                tags = o.optString("tags").trim(),
                codec = o.optString("codec").trim(),
                bitrate = o.optInt("bitrate", 0),
                hls = o.optInt("hls", 0) == 1,
            )
        }
    }
}

/**
 * Community directory of internet radio streams: radio-browser.info (free, open API).
 * Several mirror servers; the first one that answers is used.
 */
object OnlineRadio {

    private val SERVERS = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )
    private const val CACHE_FILE = "online_arabic.json"
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

    @Volatile private var arabicCache: List<OnlineStation> = emptyList()
    @Volatile private var arabicAt = 0L

    private fun fetch(pathAndQuery: String): String {
        var last: Exception? = null
        for (s in SERVERS) {
            try {
                return Http.getText(s + pathAndQuery)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("الدليل غير متاح")
    }

    fun parse(json: String): List<OnlineStation> {
        val arr = JSONArray(json)
        val out = ArrayList<OnlineStation>(arr.length())
        for (i in 0 until arr.length()) {
            OnlineStation.fromJson(arr.optJSONObject(i) ?: continue)?.let { out += it }
        }
        return out.distinctBy { it.name.lowercase().replace(Regex("\\s+"), " ") }
    }

    /** Most-listened Arabic-language stations. */
    suspend fun arabic(ctx: Context, force: Boolean = false): List<OnlineStation> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && arabicCache.isNotEmpty() && now - arabicAt < MAX_AGE_MS) return@withContext arabicCache
        val cache = File(ctx.filesDir, CACHE_FILE)
        val text = try {
            fetch("/json/stations/search?language=arabic&hidebroken=true&order=clickcount&reverse=true&limit=300")
                .also { cache.writeText(it) }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        parse(text).also { arabicCache = it; arabicAt = now }
    }

    private val countryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<OnlineStation>>>()

    /** Every working station the directory lists for a country (ISO code, e.g. "PS"). */
    suspend fun byCountry(ctx: Context, code: String, force: Boolean = false): List<OnlineStation> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            countryCache[code]?.let { (at, list) -> if (!force && now - at < MAX_AGE_MS) return@withContext list }
            val cache = File(ctx.filesDir, "online_$code.json")
            val text = try {
                fetch("/json/stations/search?countrycode=$code&hidebroken=true&order=clickcount&reverse=true&limit=1000")
                    .also { cache.writeText(it) }
            } catch (e: Exception) {
                if (cache.exists()) cache.readText() else throw e
            }
            parse(text).also { countryCache[code] = now to it }
        }

    /** Search all stations worldwide by name (e.g. "KBS", "Monte Carlo", "Medi1"). */
    suspend fun search(query: String): List<OnlineStation> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        parse(fetch("/json/stations/search?name=$q&hidebroken=true&order=clickcount&reverse=true&limit=100"))
    }

    /** Tells the directory a station was played (its popularity ranking relies on this). */
    fun countClick(uuid: String) {
        Thread {
            runCatching { fetch("/json/url/" + URLEncoder.encode(uuid, "UTF-8")) }
        }.start()
    }
}
