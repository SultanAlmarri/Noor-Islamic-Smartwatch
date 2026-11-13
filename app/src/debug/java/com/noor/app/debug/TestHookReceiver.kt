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
 *   With `scheduled`, no test alarm: every prayer's Adhan is on (limited to [max] seconds) and the real prayer alarms
 *   are armed; the next one is logged (NEXT at=<epoch ms>) so the test can move the clock to just before it and let
 *   the actual scheduled path fire, exactly as at a prayer time.
 * - DUMP: writes the alert history to logcat (tag NoorE2E), one line per alert.
 * - CITY: a chosen city by id (e.g. dxb, ist), onboarding done; used by tools/devicetest/home_sky.sh.
 * - SKY: the home scenery and the sky's quality by name (Scenery, SkyGraphics); used by tools/devicetest/real_sky.sh.
 * - LANG: Noor's own language (en, ar, or the system's) and digits (western, arabic), whatever the phone's; for
 *   tools/devicetest/phone_098.sh (Noor in English on a phone set to Arabic).
 * - SYSTEM_LOCALE: the phone's language (e.g. ar-SA), without a restart.
 * - STATUS: turns the persistent prayer notification on and logs the moments its countdown changes next
 *   (STATUS done next=<epoch ms> prayer=<P> since=<epoch ms or -1>), for tools/devicetest/phone_097.sh.
 */
class TestHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoorApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    "com.noor.smartwatch.debug.SETUP" -> setup(app, intent)
                    "com.noor.smartwatch.debug.CITY" -> {
                        app.container.settings.setOnboardingDone()
                        app.container.settings.setManualCity(intent.getStringExtra("city") ?: "dxb")
                        // The emulator's software GPU is slow: keep the GPU sky at full quality so it is what gets photographed.
                        app.getSharedPreferences("noor_ui", 0).edit().putBoolean("sky_quality_locked", true).putInt("sky_quality", 0).commit()
                        Log.i(TAG, "CITY done ${intent.getStringExtra("city")}")
                    }
                    "com.noor.smartwatch.debug.SKY" -> {
                        // The real sky (0.10): a scenery and a quality by name, for tools/devicetest/real_sky.sh.
                        val repo = app.container.settings
                        intent.getStringExtra("scenery")?.let { n -> com.noor.app.data.Scenery.entries.firstOrNull { it.name == n }?.let { repo.setScenery(it) } }
                        intent.getStringExtra("graphics")?.let { n -> com.noor.app.data.SkyGraphics.entries.firstOrNull { it.name == n }?.let { repo.setSkyGraphics(it) } }
                        Log.i(TAG, "SKY done ${intent.getStringExtra("scenery")} ${intent.getStringExtra("graphics")}")
                    }
                    "com.noor.smartwatch.debug.STATUS" -> {
                        app.container.settings.setStatusNotification(true)
                        com.noor.app.notify.StatusNotification.refresh(app)
                        val cfg = app.container.alertConfig()
                        val st = cfg?.state(java.time.Instant.now())
                        Log.i(TAG, "STATUS done next=${st?.next?.at?.toEpochMilli()} prayer=${st?.next?.prayer}")
                    }
                    "com.noor.smartwatch.debug.LANG" -> {
                        val repo = app.container.settings
                        repo.setLanguage(when (intent.getStringExtra("lang")) { "ar" -> com.noor.app.data.Language.ARABIC; "en" -> com.noor.app.data.Language.ENGLISH; else -> com.noor.app.data.Language.SYSTEM })
                        repo.setNumerals(if (intent.getStringExtra("numerals") == "arabic") com.noor.app.data.Numerals.ARABIC_INDIC else com.noor.app.data.Numerals.WESTERN)
                        // As Settings does it: the app's own language for its screens (per-app language).
                        val tags = when (intent.getStringExtra("lang")) { "ar" -> "ar"; "en" -> "en"; else -> "" }
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tags))
                        }
                        com.noor.app.notify.StatusNotification.refresh(app)
                        Log.i(TAG, "LANG done ${intent.getStringExtra("lang")} ${intent.getStringExtra("numerals")}")
                    }
                    "com.noor.smartwatch.debug.SYSTEM_LOCALE" -> {
                        // The phone's own language, as Settings changes it (no restart): needs CHANGE_CONFIGURATION
                        // (granted by adb) and the emulator's hidden-API policy relaxed (also by adb).
                        val tag = intent.getStringExtra("locale") ?: "en-US"
                        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
                        val cfg = am.javaClass.getMethod("getConfiguration").invoke(am) as android.content.res.Configuration
                        cfg.setLocales(android.os.LocaleList.forLanguageTags(tag))
                        // Not persisted (that needs WRITE_SETTINGS): the running system, SystemUI included, takes it at once.
                        runCatching { am.javaClass.getMethod("updatePersistentConfiguration", android.content.res.Configuration::class.java).invoke(am, cfg) }
                            .recoverCatching { am.javaClass.getMethod("updateConfiguration", android.content.res.Configuration::class.java).invoke(am, cfg) }
                            .getOrThrow()
                        Log.i(TAG, "SYSTEM_LOCALE done $tag")
                    }
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
        val scheduled = intent.getBooleanExtra("scheduled", false)
        repo.setOnboardingDone()
        repo.setManualCity("dxb")
        repo.setLocationMode(LocationMode.MANUAL)
        repo.updateAlerts { a ->
            a.copy(enabled = true, device = AlertDevice.PHONE, gradualSeconds = 0).let { s ->
                val which = if (scheduled) Prayer.entries.filter { it != Prayer.SHURUQ } else listOf(prayer)
                which.fold(s) { acc, p -> acc.with(p, acc.alert(p).copy(enabled = true, mode = AlertMode.ADHAN, maxSeconds = max, volumePercent = 100)) }
            }
        }
        if (scheduled) {
            app.getSystemService(AudioManager::class.java).ringerMode =
                if (intent.getBooleanExtra("vibrate", false)) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            AlertLog.clear(app)
            val status = AlertScheduler.reschedule(app)
            val next = status.next
            Log.i(TAG, "SETUP done scheduled=true exact=${status.exact} armed=${status.count} " +
                "NEXT at=${next?.at?.toEpochMilli()} prayer=${next?.prayer}")
            return
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
