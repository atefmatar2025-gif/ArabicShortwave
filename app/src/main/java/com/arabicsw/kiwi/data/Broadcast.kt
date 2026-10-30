package com.arabicsw.kiwi.data

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line of the EiBi schedule (Arabic-language broadcasts only). */
data class Broadcast(
    val freqKhz: Double,
    val startMin: Int,          // minutes after 00:00 UTC
    val endMin: Int,            // minutes after 00:00 UTC (may be < startMin when crossing midnight)
    val days: Set<DayOfWeek>?,  // null = every day
    val daysRaw: String,
    val itu: String,            // transmitter country (ITU code)
    val station: String,
    val lang: String,
    val target: String,
    val remarks: String,
) {
    val id: String
        get() = "$station|$freqKhz|$startMin|$endMin|$daysRaw|$target"

    val durationMin: Int
        get() = when {
            endMin > startMin -> endMin - startMin
            else -> endMin + 1440 - startMin
        }

    val freqLabel: String
        get() = if (freqKhz % 1.0 == 0.0) "${freqKhz.toLong()} kHz"
        else String.format(Locale.US, "%.1f kHz", freqKhz)

    val band: String
        get() = bandFor(freqKhz)

    private fun dayOk(d: DayOfWeek) = days == null || d in days

    fun isOnAt(nowUtc: ZonedDateTime): Boolean {
        val now = nowUtc.withZoneSameInstant(ZoneOffset.UTC)
        val m = now.hour * 60 + now.minute
        val today = now.dayOfWeek
        return if (endMin > startMin) {
            m in startMin until endMin && dayOk(today)
        } else {
            (m >= startMin && dayOk(today)) || (m < endMin && dayOk(today.minus(1)))
        }
    }

    /** First start strictly after [t], within the next 8 days. */
    fun nextStartAfter(t: ZonedDateTime): ZonedDateTime? {
        val utc = t.withZoneSameInstant(ZoneOffset.UTC)
        for (k in 0..8) {
            val date = utc.toLocalDate().plusDays(k.toLong())
            val s = date.atStartOfDay(ZoneOffset.UTC).plusMinutes(startMin.toLong())
            if (s.isAfter(utc) && dayOk(date.dayOfWeek)) return s
        }
        return null
    }

    /** Start of the session that is running at [nowUtc] (only meaningful if isOnAt). */
    fun currentStart(nowUtc: ZonedDateTime): ZonedDateTime {
        val now = nowUtc.withZoneSameInstant(ZoneOffset.UTC)
        val m = now.hour * 60 + now.minute
        val base = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusMinutes(startMin.toLong())
        return if (m >= startMin) base else base.minusDays(1)
    }

    fun utcRange(): String = "${hhmm(startMin)}–${if (endMin == 1440) "24:00" else hhmm(endMin % 1440)} UTC"

    fun localRange(zone: ZoneId = ZoneId.systemDefault(), ref: ZonedDateTime? = null): String {
        val start = ref ?: ZonedDateTime.now(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).plusMinutes(startMin.toLong())
        val s = start.withZoneSameInstant(zone)
        val e = s.plusMinutes(durationMin.toLong())
        return "${s.format(HM)}–${e.format(HM)}"
    }

    fun daysLabel(): String {
        if (daysRaw.isBlank()) return "يومياً"
        val d = days ?: return daysRaw
        if (d.size == 7) return "يومياً"
        return DayOfWeek.values().filter { it in d }.joinToString("، ") { arabicDay(it) }
    }

    companion object {
        private val HM = DateTimeFormatter.ofPattern("HH:mm")

        fun hhmm(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

        fun arabicDay(d: DayOfWeek): String = when (d) {
            DayOfWeek.MONDAY -> "الإثنين"
            DayOfWeek.TUESDAY -> "الثلاثاء"
            DayOfWeek.WEDNESDAY -> "الأربعاء"
            DayOfWeek.THURSDAY -> "الخميس"
            DayOfWeek.FRIDAY -> "الجمعة"
            DayOfWeek.SATURDAY -> "السبت"
            DayOfWeek.SUNDAY -> "الأحد"
        }

        fun bandFor(f: Double): String = when {
            f < 300 -> "موجة طويلة"
            f < 1800 -> "موجة متوسطة"
            f in 2300.0..2500.0 -> "120m"
            f in 3200.0..3400.0 -> "90m"
            f in 3900.0..4050.0 -> "75m"
            f in 4750.0..5110.0 -> "60m"
            f in 5800.0..6300.0 -> "49m"
            f in 7200.0..7650.0 -> "41m"
            f in 9300.0..9950.0 -> "31m"
            f in 11550.0..12160.0 -> "25m"
            f in 13550.0..13900.0 -> "22m"
            f in 15000.0..15850.0 -> "19m"
            f in 17400.0..17950.0 -> "16m"
            f in 18850.0..19050.0 -> "15m"
            f in 21400.0..21900.0 -> "13m"
            f in 25600.0..26150.0 -> "11m"
            else -> "موجة قصيرة"
        }
    }
}
