package com.arabicsw.kiwi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

data class KiwiReceiver(
    val id: String,
    val name: String,
    val url: String,
    val location: String,
    val antenna: String,
    val pos: LatLon,
    val users: Int,
    val usersMax: Int,
    val lowHz: Long,
    val highHz: Long,
    val snr: Int,
)

data class RankedReceiver(
    val rx: KiwiReceiver,
    val distanceKm: Double,
    val score: Double,
)

/**
 * Public KiwiSDR list. Source: rx.linkfanel.net (mirror of kiwisdr.com/public),
 * a JavaScript file of the form `var kiwisdr_com = [ { "name": "...", ... }, ... ];`
 */
object KiwiDirectory {

    private val SOURCES = listOf(
        "http://rx.linkfanel.net/kiwisdr_com.js",
        "https://rx.linkfanel.net/kiwisdr_com.js",
    )
    private const val CACHE_FILE = "kiwisdr_list.js"
    private const val MAX_AGE_MS = 20 * 60 * 1000L

    @Volatile
    private var memory: List<KiwiReceiver> = emptyList()
    @Volatile
    private var memoryAt = 0L

    suspend fun load(ctx: Context, force: Boolean = false): List<KiwiReceiver> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && memory.isNotEmpty() && now - memoryAt < MAX_AGE_MS) return@withContext memory

        val cache = File(ctx.filesDir, CACHE_FILE)
        var text: String? = null
        if (!force && cache.exists() && now - cache.lastModified() < MAX_AGE_MS) text = cache.readText()
        if (text == null) {
            for (u in SOURCES) {
                try {
                    val t = Http.getText(u)
                    if (t.contains("\"url\"")) {
                        text = t
                        cache.writeText(t)
                        break
                    }
                } catch (_: Exception) {
                }
            }
        }
        // offline: fall back to an old cache rather than nothing
        if (text == null && cache.exists()) text = cache.readText()
        val list = text?.let { parse(it) }.orEmpty()
        if (list.isNotEmpty()) {
            memory = list
            memoryAt = now
        }
        list
    }

    private val objRx = Regex("""\{([^{}]*)\}""", RegexOption.DOT_MATCHES_ALL)
    private val fieldRx = Regex(""""([A-Za-z_0-9]+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val numRx = Regex("""[-+]?\d+(?:\.\d+)?""")
    private val uintRx = Regex("""\d+""")

    fun parse(text: String): List<KiwiReceiver> {
        val out = ArrayList<KiwiReceiver>()
        for (obj in objRx.findAll(text)) {
            val f = HashMap<String, String>()
            for (m in fieldRx.findAll(obj.groupValues[1])) {
                f[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\/", "/")
            }
            val url = f["url"]?.trim().orEmpty()
            if (!url.startsWith("http")) continue
            if (f["offline"] == "yes") continue
            val status = f["status"].orEmpty()
            if (status.isNotEmpty() && status != "active") continue

            val gps = numRx.findAll(f["gps"].orEmpty()).map { it.value.toDouble() }.toList()
            if (gps.size < 2) continue
            val pos = LatLon(gps[0], gps[1])
            if (pos.lat == 0.0 && pos.lon == 0.0) continue

            val bandNums = uintRx.findAll(f["bands"].orEmpty()).mapNotNull { it.value.toLongOrNull() }.toList()
            val low = bandNums.minOrNull() ?: 0L
            val high = bandNums.maxOrNull() ?: 30_000_000L

            val snrParts = f["snr"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }

            out += KiwiReceiver(
                id = f["id"] ?: url,
                name = decode(f["name"].orEmpty()).ifBlank { url },
                url = url,
                location = decode(f["loc"].orEmpty()),
                antenna = decode(f["antenna"].orEmpty()),
                pos = pos,
                users = f["users"]?.toIntOrNull() ?: 0,
                usersMax = f["users_max"]?.toIntOrNull() ?: 4,
                lowHz = low,
                highHz = if (high <= low) 30_000_000L else high,
                snr = snrParts.lastOrNull() ?: 10,
            )
        }
        return out
    }

    private fun decode(s: String): String =
        runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /**
     * Ranks receivers for a frequency and the area the broadcast targets.
     * Lower score is better: near the target, free slots, good HF SNR.
     */
    fun rank(list: List<KiwiReceiver>, freqKhz: Double, target: LatLon, limit: Int = 25): List<RankedReceiver> {
        val hz = (freqKhz * 1000).toLong()
        return list.asSequence()
            .filter { it.users < it.usersMax }
            .filter { hz in it.lowHz..it.highHz }
            .map { rx ->
                val d = Geo.distanceKm(rx.pos, target)
                val occupancy = if (rx.usersMax > 0) rx.users.toDouble() / rx.usersMax else 1.0
                val score = d / 500.0 + occupancy * 1.5 - min(rx.snr, 30) * 0.12
                RankedReceiver(rx, d, score)
            }
            .sortedBy { it.score }
            .take(limit)
            .toList()
    }
}
