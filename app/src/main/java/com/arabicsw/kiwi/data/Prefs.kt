package com.arabicsw.kiwi.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NOTIFY_OFF = 0
    const val NOTIFY_FAVORITES = 1
    const val NOTIFY_ALL = 2

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun favorites(ctx: Context): Set<String> = sp(ctx).getStringSet("favorites", emptySet())!!.toSet()

    fun toggleFavorite(ctx: Context, station: String): Set<String> {
        val s = favorites(ctx).toMutableSet()
        if (!s.remove(station)) s.add(station)
        sp(ctx).edit().putStringSet("favorites", s).apply()
        return s
    }

    fun notifyMode(ctx: Context): Int = sp(ctx).getInt("notify_mode", NOTIFY_FAVORITES)
    fun setNotifyMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("notify_mode", v).apply()

    /** Minutes before start to notify (0 = at start). */
    fun leadMinutes(ctx: Context): Int = sp(ctx).getInt("lead_min", 0)
    fun setLeadMinutes(ctx: Context, v: Int) = sp(ctx).edit().putInt("lead_min", v).apply()

    fun autoSwitch(ctx: Context): Boolean = sp(ctx).getBoolean("auto_switch", true)
    fun setAutoSwitch(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_switch", v).apply()

    /** "am" or "sam" (synchronous AM, steadier on fading signals). */
    fun mode(ctx: Context): String = sp(ctx).getString("mode", "am") ?: "am"
    fun setMode(ctx: Context, v: String) = sp(ctx).edit().putString("mode", v).apply()

    fun scheduleSource(ctx: Context): String = sp(ctx).getString("sched_src", "") ?: ""
    fun setScheduleSource(ctx: Context, v: String) = sp(ctx).edit().putString("sched_src", v).apply()

    fun pendingAlertStart(ctx: Context): Long = sp(ctx).getLong("pending_alert", 0L)
    fun setPendingAlertStart(ctx: Context, v: Long) = sp(ctx).edit().putLong("pending_alert", v).apply()
}
