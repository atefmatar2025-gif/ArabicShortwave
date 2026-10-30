package com.arabicsw.kiwi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * Downloads the public EiBi shortwave schedule (eibispace.de), keeps only the
 * Arabic-language entries ("A") and caches them on disk.
 *
 * EiBi CSV columns (semicolon separated):
 * kHz;Time(UTC);Days;ITU;Station;Lng;Target;Remarks;P;Start;Stop
 */
object ScheduleRepository {

    private const val CACHE_FILE = "eibi_arabic.csv"
    private const val MIRROR_URL =
        "https://raw.githubusercontent.com/atefmatar2025-gif/ArabicShortwave/main/data/arabic.csv"
    private val _items = MutableStateFlow<List<Broadcast>>(emptyList())
    val items: StateFlow<List<Broadcast>> = _items

    @Volatile
    private var loaded = false

    fun ensureLoaded(ctx: Context): List<Broadcast> {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                val f = File(ctx.filesDir, CACHE_FILE)
                if (f.exists()) {
                    _items.value = runCatching { parse(f.readText()).first }.getOrDefault(emptyList())
                }
                loaded = true
            }
        }
        return _items.value
    }

    fun lastUpdated(ctx: Context): Long = File(ctx.filesDir, CACHE_FILE).let { if (it.exists()) it.lastModified() else 0L }

    /** Downloads the current season's schedule. Returns the number of Arabic entries. */
    suspend fun refresh(ctx: Context): Int = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        // 1) Daily copy kept in the project's GitHub repo (EiBi's own site has an outdated certificate)
        try {
            val text = Http.getText(MIRROR_URL, Charsets.ISO_8859_1)
            val (list, lines) = parse(text)
            if (list.size >= 10) {
                File(ctx.filesDir, CACHE_FILE).writeText(lines.joinToString("\n"))
                val src = text.lineSequence().firstOrNull { it.startsWith("# source=") }
                    ?.removePrefix("# source=")?.trim()?.uppercase() ?: "EiBi"
                Prefs.setScheduleSource(ctx, src)
                _items.value = list
                loaded = true
                return@withContext list.size
            }
        } catch (e: Exception) {
            lastError = e
        }
        // 2) EiBi directly
        for (code in seasonCodes(LocalDate.now(ZoneOffset.UTC))) {
            for (base in listOf("http://www.eibispace.de/dx/", "https://www.eibispace.de/dx/")) {
                try {
                    val text = Http.getText("${base}sked-$code.csv", Charsets.ISO_8859_1)
                    val (list, lines) = parse(text)
                    if (list.size >= 10) {
                        File(ctx.filesDir, CACHE_FILE).writeText(lines.joinToString("\n"))
                        Prefs.setScheduleSource(ctx, "EiBi $code".uppercase())
                        _items.value = list
                        loaded = true
                        return@withContext list.size
                    }
                    lastError = IOException("الملف sked-$code لا يحتوي على بيانات كافية")
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw lastError ?: IOException("تعذّر تنزيل الجدول")
    }

    /**
     * Broadcast seasons: A = last Sunday of March, B = last Sunday of October.
     * Returns the current season first, then the previous one as a fallback.
     */
    fun seasonCodes(today: LocalDate): List<String> {
        val y = today.year
        val yy = y % 100
        val aStart = LocalDate.of(y, 3, 31).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))
        val bStart = LocalDate.of(y, 10, 31).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))
        fun two(n: Int) = String.format("%02d", (n + 100) % 100)
        return when {
            !today.isBefore(bStart) -> listOf("b${two(yy)}", "a${two(yy)}")
            !today.isBefore(aStart) -> listOf("a${two(yy)}", "b${two(yy - 1)}")
            else -> listOf("b${two(yy - 1)}", "a${two(yy - 1)}")
        }
    }

    /** Returns parsed Arabic broadcasts and the raw lines they came from. */
    fun parse(text: String): Pair<List<Broadcast>, List<String>> {
        val out = ArrayList<Broadcast>()
        val lines = ArrayList<String>()
        for (line in text.lineSequence()) {
            val b = parseLine(line) ?: continue
            out += b
            lines += line
        }
        out.sortWith(compareBy<Broadcast> { it.startMin }.thenBy { it.freqKhz })
        return out to lines
    }

    private val timeRx = Regex("""^(\d{4})-(\d{4})$""")

    fun parseLine(line: String): Broadcast? {
        val c = line.split(';')
        if (c.size < 7) return null
        val freq = c[0].trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (freq <= 0 || freq > 30000) return null

        val lang = c[5].trim()
        val langs = lang.split(',', '/', ' ').map { it.trim().removePrefix("-") }
        if ("A" !in langs) return null

        val m = timeRx.find(c[1].trim()) ?: return null
        val start = toMinutes(m.groupValues[1]) ?: return null
        val end = toMinutes(m.groupValues[2]) ?: return null

        val daysRaw = c[2].trim()
        return Broadcast(
            freqKhz = freq,
            startMin = start % 1440,
            endMin = end,
            days = parseDays(daysRaw),
            daysRaw = daysRaw,
            itu = c[3].trim(),
            station = c[4].trim().ifEmpty { "محطة غير معروفة" },
            lang = lang,
            target = c[6].trim(),
            remarks = c.getOrNull(7)?.trim().orEmpty(),
        )
    }

    private fun toMinutes(hhmm: String): Int? {
        val h = hhmm.substring(0, 2).toIntOrNull() ?: return null
        val mi = hhmm.substring(2, 4).toIntOrNull() ?: return null
        if (h > 24 || mi > 59) return null
        return h * 60 + mi
    }

    private val dayAbbr = mapOf(
        "Mo" to DayOfWeek.MONDAY, "Tu" to DayOfWeek.TUESDAY, "We" to DayOfWeek.WEDNESDAY,
        "Th" to DayOfWeek.THURSDAY, "Fr" to DayOfWeek.FRIDAY, "Sa" to DayOfWeek.SATURDAY,
        "Su" to DayOfWeek.SUNDAY,
    )
    private val rangeRx = Regex("""^([A-Z][a-z])-([A-Z][a-z])$""")

    /** EiBi days: "" = daily, "1245" (1 = Monday), "Mo-Fr", "Sa,Su". Unknown forms → null (daily). */
    fun parseDays(raw: String): Set<DayOfWeek>? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.all { it in '1'..'7' }) return s.map { DayOfWeek.of(it - '0') }.toSet()
        val out = mutableSetOf<DayOfWeek>()
        for (tok in s.split(',', '/')) {
            val t = tok.trim()
            val r = rangeRx.find(t)
            if (r != null) {
                val a = dayAbbr[r.groupValues[1]] ?: return null
                val b = dayAbbr[r.groupValues[2]] ?: return null
                var d = a
                while (true) {
                    out += d
                    if (d == b) break
                    d = d.plus(1)
                }
            } else {
                out += dayAbbr[t] ?: return null
            }
        }
        return out.ifEmpty { null }
    }
}
