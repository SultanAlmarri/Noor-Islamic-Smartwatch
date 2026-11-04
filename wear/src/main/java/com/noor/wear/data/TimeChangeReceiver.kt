package com.noor.wear.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The day's times are recalculated by whoever draws them; this only asks complication and tile to redraw. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ConfigRepository.refreshSurfaces(context)
}
