package com.arabicsw.kiwi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.arabicsw.kiwi.alerts.AlertScheduler
import com.arabicsw.kiwi.data.ScheduleRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "تنبيهات بدء البث", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعار عند بدء فترة بث لمحطة عربية"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PLAYER, "التشغيل", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار التشغيل الجاري"
                setShowBadge(false)
            }
        )
        ScheduleRepository.ensureLoaded(this)
        AlertScheduler.reschedule(this)
    }

    companion object {
        const val CH_ALERTS = "alerts"
        const val CH_PLAYER = "player"
    }
}
