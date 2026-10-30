package com.arabicsw.kiwi.alerts

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arabicsw.kiwi.App
import com.arabicsw.kiwi.R
import com.arabicsw.kiwi.data.Broadcast
import com.arabicsw.kiwi.data.Geo
import com.arabicsw.kiwi.data.Prefs
import com.arabicsw.kiwi.data.ScheduleRepository
import com.arabicsw.kiwi.player.PlayerService
import com.arabicsw.kiwi.ui.MainActivity
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Keeps exactly one alarm pending: the next moment any watched broadcast starts
 * (minus the user's lead time). When it fires we notify and schedule the next one.
 */
object AlertScheduler {

    private const val REQ_ALARM = 1001
    const val EXTRA_START = "start_epoch"

    fun watched(ctx: Context): List<Broadcast> {
        val mode = Prefs.notifyMode(ctx)
        if (mode == Prefs.NOTIFY_OFF) return emptyList()
        val all = ScheduleRepository.ensureLoaded(ctx)
        if (mode == Prefs.NOTIFY_ALL) return all
        val favs = Prefs.favorites(ctx)
        return all.filter { it.station in favs }
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun alarmIntent(ctx: Context, startEpoch: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALARM,
            Intent(ctx, AlertReceiver::class.java).putExtra(EXTRA_START, startEpoch),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(alarmIntent(ctx, 0))
        Prefs.setPendingAlertStart(ctx, 0)

        val list = watched(ctx)
        if (list.isEmpty()) return
        val lead = Prefs.leadMinutes(ctx).toLong()
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val from = now.plusMinutes(lead).plusSeconds(30)
        val next = list.mapNotNull { it.nextStartAfter(from) }.minOrNull() ?: return

        val startEpoch = next.toEpochSecond()
        val fireAt = (startEpoch - lead * 60) * 1000
        val pi = alarmIntent(ctx, startEpoch)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        Prefs.setPendingAlertStart(ctx, startEpoch)
    }

    /** Broadcasts in the watch list that start exactly at [startEpoch]. */
    fun dueAt(ctx: Context, startEpoch: Long): List<Broadcast> {
        val start = Instant.ofEpochSecond(startEpoch)
        val probe = ZonedDateTime.ofInstant(start.minusSeconds(1), ZoneOffset.UTC)
        return watched(ctx).filter { it.nextStartAfter(probe)?.toInstant() == start }
    }

    @SuppressLint("MissingPermission")
    fun notifyBroadcasts(ctx: Context, due: List<Broadcast>, startEpoch: Long) {
        if (due.isEmpty()) return
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val lead = Prefs.leadMinutes(ctx)
        val prefix = if (lead > 0) "يبدأ خلال $lead د" else "بدأ البث الآن"
        val openApp = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        if (due.size > 4) {
            val names = due.map { "${it.station} (${it.freqKhz.toLong()})" }.distinct()
            val n = NotificationCompat.Builder(ctx, App.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle("$prefix: ${due.size} فترات بث عربية")
                .setContentText(names.take(4).joinToString("، "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n")))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            runCatching { nmc.notify((startEpoch % 100000).toInt(), n) }
            return
        }

        for (b in due) {
            val listen = PendingIntent.getForegroundService(
                ctx, b.id.hashCode(), PlayerService.playIntent(ctx, b),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, App.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle("$prefix: ${b.station}")
                .setContentText("${b.freqLabel} • ${b.band} • ${Geo.targetLabel(b.target)} • ${b.localRange()}")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "استمع الآن", listen)
                .build()
            runCatching { nmc.notify(b.id.hashCode(), n) }
        }
    }
}
