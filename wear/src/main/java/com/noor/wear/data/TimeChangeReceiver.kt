package com.noor.wear.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noor.wear.NoorWearApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The day's times are recalculated by whoever draws them; this asks complications and tiles to redraw, and when the
 * watch was set up with its own location, follows the new time zone (travel) like the phone's automatic mode does.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ConfigRepository.refreshSurfaces(context)
        val app = context.applicationContext as? NoorWearApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { app.config.refreshLocal() } finally { pending.finish() }
        }
    }
}
