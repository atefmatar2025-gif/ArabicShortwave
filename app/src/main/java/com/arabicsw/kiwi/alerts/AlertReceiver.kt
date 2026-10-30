package com.arabicsw.kiwi.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val start = intent.getLongExtra(AlertScheduler.EXTRA_START, 0L)
        if (start > 0) {
            val due = AlertScheduler.dueAt(context, start)
            AlertScheduler.notifyBroadcasts(context, due, start)
        }
        AlertScheduler.reschedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertScheduler.reschedule(context)
    }
}
