package com.noor.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Time-zone or clock change: refresh the location once (the dashboard recalculates by itself). */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            LocationWorker.once(context)
        }
    }
}
