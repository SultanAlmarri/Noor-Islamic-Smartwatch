package com.noor.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noor.prayer.LocalSetup
import com.noor.prayer.Places
import com.noor.prayer.SetupPlace
import com.noor.wear.NoorWearApp
import com.noor.wear.facepush.FacePush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Used by tools/devicetest/watch_face.sh on a Wear OS 6 emulator:
 * - WATCH_CITY: the watch set up on its own with a city (id, e.g. dxb), as a user does on the watch.
 * - FACE: the Noor face chosen ([face] id), exactly as when the phone's editor sends one (FacePush.choose).
 * - FACE_STATE: logs the face's state (tag NoorWatchTest).
 */
class WatchTestHook : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoorWearApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    "com.noor.smartwatch.debug.WATCH_CITY" -> {
                        val c = Places.byId(intent.getStringExtra("city") ?: "dxb") ?: Places.ALL.first()
                        app.config.saveSetup(LocalSetup(place = SetupPlace(c.point, c.zoneId, c.nameEn, c.nameAr, c.countryCode, c.region, fromGps = false)))
                        Log.i(TAG, "WATCH_CITY done ${c.id}")
                    }
                    "com.noor.smartwatch.debug.FACE" -> {
                        val st = FacePush(context).choose(intent.getStringExtra("face") ?: "horizon_gold")
                        Log.i(TAG, "FACE done $st")
                    }
                    "com.noor.smartwatch.debug.FACE_STATE" -> Log.i(TAG, "FACE_STATE ${FacePush(context).state()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "hook failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "NoorWatchTest" }
}
