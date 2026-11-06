package com.noor.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.noor.alerts.AlertLog
import com.noor.alerts.AlertScheduler
import com.noor.app.NoorApplication
import com.noor.app.data.LocationMode
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Used by tools/devicetest/alert_e2e.sh on emulators and phones:
 *
 * - SETUP: Dubai as a chosen city, alerts on this phone, the Adhan for [prayer] limited to [max] seconds, optionally the
 *   ringer on vibrate, then a real test alarm in [seconds] through the normal path (alarm → receiver → player service).
 * - DUMP: writes the alert history to logcat (tag NoorE2E), one line per alert.
 */
class TestHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoorApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    "com.noor.smartwatch.debug.SETUP" -> setup(app, intent)
                    "com.noor.smartwatch.debug.DUMP" -> AlertLog.all(app).forEach { r ->
                        Log.i(TAG, "RECORD prayer=${r.prayer} test=${r.test} outcome=${r.outcome} playedMs=${r.playedMs} " +
                            "delayMs=${r.delayMs} exact=${r.exactAlarms} vol=${r.alarmVolumePercent} ringer=${r.ringerMode} detail=${r.detail}")
                    }.also { Log.i(TAG, "DUMP done") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "hook failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun setup(app: NoorApplication, intent: Intent) {
        val repo = app.container.settings
        val prayer = intent.getStringExtra("prayer")?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: Prayer.DHUHR
        val max = intent.getIntExtra("max", 30)
        val seconds = intent.getIntExtra("seconds", 40)
        repo.setOnboardingDone()
        repo.setManualCity("dxb")
        repo.setLocationMode(LocationMode.MANUAL)
        repo.updateAlerts { a ->
            a.copy(enabled = true, device = AlertDevice.PHONE, gradualSeconds = 0)
                .let { s -> s.with(prayer, s.alert(prayer).copy(enabled = true, mode = AlertMode.ADHAN, maxSeconds = max, volumePercent = 100)) }
        }
        app.getSystemService(AudioManager::class.java).ringerMode =
            if (intent.getBooleanExtra("vibrate", false)) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        AlertLog.clear(app)
        val ok = AlertScheduler.scheduleTest(app, prayer, seconds)
        Log.i(TAG, "SETUP done prayer=$prayer seconds=$seconds max=$max exact=${AlertScheduler.canScheduleExact(app)} scheduled=$ok " +
            "ringer=${app.getSystemService(AudioManager::class.java).ringerMode}")
    }

    private companion object { const val TAG = "NoorE2E" }
}
