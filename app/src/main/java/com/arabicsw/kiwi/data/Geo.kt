package com.arabicsw.kiwi.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/**
 * Approximate centroids for EiBi target-area codes and ITU country codes.
 * Used to pick a KiwiSDR receiver located where the broadcast is aimed.
 */
object Geo {

    fun distanceKm(a: LatLon, b: LatLon): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** EiBi region codes (target column). */
    private val regions = mapOf(
        "ME" to LatLon(26.0, 44.0),     // الشرق الأوسط
        "NE" to LatLon(31.0, 36.0),     // الشرق الأدنى
        "Gul" to LatLon(26.0, 51.0),    // الخليج
        "NAf" to LatLon(30.0, 8.0),     // شمال أفريقيا
        "Af" to LatLon(5.0, 20.0),
        "EAf" to LatLon(2.0, 38.0),
        "WAf" to LatLon(11.0, -5.0),
        "CAf" to LatLon(2.0, 20.0),
        "SAf" to LatLon(-25.0, 25.0),
        "Eu" to LatLon(49.0, 10.0),
        "WEu" to LatLon(48.0, 2.0),
        "CEu" to LatLon(50.0, 14.0),
        "EEu" to LatLon(50.0, 28.0),
        "SEu" to LatLon(41.0, 15.0),
        "NEu" to LatLon(60.0, 15.0),
        "As" to LatLon(30.0, 80.0),
        "CAs" to LatLon(42.0, 65.0),
        "SAs" to LatLon(22.0, 78.0),
        "WAs" to LatLon(32.0, 45.0),
        "EAs" to LatLon(35.0, 115.0),
        "SEA" to LatLon(10.0, 105.0),
        "FE" to LatLon(40.0, 130.0),
        "Oc" to LatLon(-25.0, 135.0),
        "NAm" to LatLon(40.0, -95.0),
        "CAm" to LatLon(14.0, -87.0),
        "SAm" to LatLon(-15.0, -55.0),
        "LAm" to LatLon(-5.0, -60.0),
        "Am" to LatLon(20.0, -80.0),
        "Car" to LatLon(18.0, -72.0),
        "CIS" to LatLon(55.0, 45.0),
        "AO" to LatLon(10.0, -30.0),
        "IO" to LatLon(-10.0, 70.0),
    )

    /** ITU country codes as used by EiBi (target or transmitter country). */
    private val countries = mapOf(
        // الدول العربية
        "ALG" to LatLon(32.0, 3.0), "ARS" to LatLon(24.0, 45.0), "BHR" to LatLon(26.1, 50.5),
        "COM" to LatLon(-11.7, 43.3), "DJI" to LatLon(11.6, 43.1), "EGY" to LatLon(28.5, 31.0),
        "IRQ" to LatLon(33.3, 44.4), "JOR" to LatLon(31.9, 35.9), "KWT" to LatLon(29.3, 47.9),
        "LBN" to LatLon(33.9, 35.5), "LBY" to LatLon(30.0, 15.0), "MRC" to LatLon(33.0, -6.8),
        "MTN" to LatLon(18.1, -15.9), "OMA" to LatLon(23.0, 57.5), "QAT" to LatLon(25.3, 51.5),
        "SDN" to LatLon(15.5, 32.5), "SOM" to LatLon(4.0, 45.5), "SYR" to LatLon(34.8, 37.0),
        "TUN" to LatLon(35.5, 10.0), "UAE" to LatLon(24.4, 54.4), "YEM" to LatLon(15.4, 45.0),
        "PAL" to LatLon(31.9, 35.2), "SSD" to LatLon(6.9, 31.3),
        // دول البث الشائعة
        "ISR" to LatLon(31.8, 34.9), "TUR" to LatLon(39.5, 32.8), "IRN" to LatLon(33.0, 52.0),
        "CHN" to LatLon(35.0, 110.0), "D" to LatLon(51.0, 10.0), "F" to LatLon(46.5, 2.5),
        "G" to LatLon(52.5, -1.5), "HOL" to LatLon(52.3, 5.3), "I" to LatLon(42.5, 12.5),
        "E" to LatLon(40.0, -4.0), "POR" to LatLon(39.5, -8.0), "ROU" to LatLon(45.8, 25.0),
        "BUL" to LatLon(42.7, 25.3), "RUS" to LatLon(55.7, 37.6), "UKR" to LatLon(49.0, 31.0),
        "USA" to LatLon(38.0, -97.0), "CAN" to LatLon(50.0, -100.0), "KOR" to LatLon(36.5, 127.8),
        "KRE" to LatLon(40.0, 127.0), "J" to LatLon(36.0, 138.0), "IND" to LatLon(22.0, 78.0),
        "PAK" to LatLon(30.0, 70.0), "AFG" to LatLon(34.0, 66.0), "TJK" to LatLon(38.5, 71.0),
        "UZB" to LatLon(41.3, 64.5), "KAZ" to LatLon(48.0, 67.0), "ARM" to LatLon(40.2, 44.5),
        "AZE" to LatLon(40.4, 47.7), "GEO" to LatLon(42.0, 43.5), "ASC" to LatLon(-7.9, -14.4),
        "NIG" to LatLon(9.0, 8.0), "ETH" to LatLon(9.0, 39.0), "ERI" to LatLon(15.3, 38.9),
        "KEN" to LatLon(0.0, 38.0), "MDG" to LatLon(-19.0, 47.0), "SEY" to LatLon(-4.6, 55.5),
        "MLI" to LatLon(17.0, -4.0), "TCD" to LatLon(15.0, 19.0), "GAB" to LatLon(-0.6, 11.6),
        "AFS" to LatLon(-29.0, 25.0), "AUT" to LatLon(47.5, 14.5), "CZE" to LatLon(49.8, 15.5),
        "SVK" to LatLon(48.7, 19.5), "HNG" to LatLon(47.0, 19.5), "POL" to LatLon(52.0, 19.5),
        "S" to LatLon(62.0, 15.0), "NOR" to LatLon(61.0, 9.0), "FIN" to LatLon(62.0, 26.0),
        "GRC" to LatLon(39.0, 22.0), "CYP" to LatLon(35.0, 33.0), "MLT" to LatLon(35.9, 14.4),
        "VTN" to LatLon(16.0, 107.0), "THA" to LatLon(15.0, 101.0), "TWN" to LatLon(23.7, 121.0),
        "PHL" to LatLon(12.0, 122.0), "INS" to LatLon(-2.0, 118.0), "MLA" to LatLon(3.5, 102.0),
        "SNG" to LatLon(1.35, 103.8), "AUS" to LatLon(-25.0, 134.0), "MDA" to LatLon(47.0, 29.0),
        "MNG" to LatLon(46.5, 104.0), "B" to LatLon(-10.0, -52.0), "ARG" to LatLon(-34.0, -64.0),
        "CUB" to LatLon(21.7, -79.5), "GUM" to LatLon(13.4, 144.8), "BEL" to LatLon(50.6, 4.6),
        "SUI" to LatLon(46.8, 8.2), "SRB" to LatLon(44.0, 20.9), "ALB" to LatLon(41.2, 20.0),
        "BLR" to LatLon(53.7, 28.0), "LTU" to LatLon(55.2, 23.9), "EST" to LatLon(58.6, 25.0),
        "IRL" to LatLon(53.4, -8.2), "NZL" to LatLon(-41.0, 174.0), "BOT" to LatLon(-22.3, 24.7),
        "RRW" to LatLon(-1.9, 29.9), "UGA" to LatLon(1.4, 32.3), "TZA" to LatLon(-6.4, 34.9),
        "MOZ" to LatLon(-18.7, 35.5), "ZMB" to LatLon(-13.1, 27.8), "GHA" to LatLon(7.9, -1.0),
        "SEN" to LatLon(14.5, -14.5), "CME" to LatLon(5.7, 12.7), "NPL" to LatLon(28.4, 84.1),
        "BGD" to LatLon(23.7, 90.4), "CLN" to LatLon(7.9, 80.8), "TKM" to LatLon(38.9, 59.6),
        "KGZ" to LatLon(41.2, 74.8),
    )

    /** Default when nothing matches: center of the Arab world. */
    val ARAB_WORLD = LatLon(26.0, 35.0)

    /**
     * Where the signal is aimed. Prefers the EiBi target code, falls back to the
     * transmitter country, then to the Arab world in general.
     */
    fun targetPoint(target: String, itu: String): LatLon {
        val t = target.trim()
        regions[t]?.let { return it }
        countries[t]?.let { return it }
        // targets like "ME,NAf" or "NAf/Eu": use the first resolvable token
        for (tok in t.split(',', '/', ' ', '-')) {
            val k = tok.trim()
            regions[k]?.let { return it }
            countries[k]?.let { return it }
        }
        countries[itu.trim()]?.let { return it }
        return ARAB_WORLD
    }

    /**
     * Where the transmitter is. EiBi remarks like "/CYP" name the transmitter
     * country when it differs from the station's home country (ITU column).
     */
    fun transmitterPoint(itu: String, remarks: String): LatLon? {
        Regex("""/([A-Z]{1,3})""").find(remarks)?.groupValues?.get(1)?.let { code ->
            countries[code]?.let { return it }
        }
        return countries[itu.trim()]
    }

    /** Arabic description of the target area, if known. */
    fun targetLabel(target: String): String = when (target.trim()) {
        "ME" -> "الشرق الأوسط"
        "NE" -> "الشرق الأدنى"
        "Gul" -> "الخليج"
        "NAf" -> "شمال أفريقيا"
        "Af" -> "أفريقيا"
        "EAf" -> "شرق أفريقيا"
        "WAf" -> "غرب أفريقيا"
        "CAf" -> "وسط أفريقيا"
        "SAf" -> "جنوب أفريقيا"
        "Eu" -> "أوروبا"
        "WEu" -> "غرب أوروبا"
        "As" -> "آسيا"
        "ALG" -> "الجزائر"
        "ARS" -> "السعودية"
        "EGY" -> "مصر"
        "IRQ" -> "العراق"
        "SYR" -> "سوريا"
        "LBY" -> "ليبيا"
        "MRC" -> "المغرب"
        "TUN" -> "تونس"
        "SDN" -> "السودان"
        "YEM" -> "اليمن"
        "SOM" -> "الصومال"
        "MTN" -> "موريتانيا"
        "OMA" -> "عُمان"
        "KWT" -> "الكويت"
        "UAE" -> "الإمارات"
        "JOR" -> "الأردن"
        "LBN" -> "لبنان"
        "" -> "غير محدد"
        else -> target
    }
}
