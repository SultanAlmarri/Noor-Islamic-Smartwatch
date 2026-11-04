package com.noor.alerts

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.FireDecision
import com.noor.prayer.alerts.PrayerAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

internal val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** A prayer alarm (or a test alarm) fired. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        alertScope.launch {
            try {
                handle(app, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Alert failed", e)
            } finally {
                runCatching { AlertScheduler.reschedule(app) }
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val host = context.alertHost ?: return
        val config = host.alertConfig() ?: return
        val alerts = config.alerts ?: return
        val prayer = intent.getStringExtra(AlertScheduler.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: return
        val date = intent.getStringExtra(AlertScheduler.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        if (intent.action == AlertScheduler.ACTION_TEST) {
            val at = Instant.ofEpochMilli(intent.getLongExtra(AlertScheduler.EXTRA_AT, System.currentTimeMillis()))
            AlertDispatcher.dispatch(context, config, alerts, prayer, date, at, alerts.alert(prayer).copy(enabled = true), test = true)
            return
        }
        when (val d = AlertPlanner.onFire(config, alerts, host.alertDevice, prayer, date, Instant.now())) {
            is FireDecision.Alert -> AlertDispatcher.dispatch(context, config, alerts, d.prayer, d.date, d.at, d.alert, test = false)
            is FireDecision.Late -> AlertNotifications.postLate(context, config, d.prayer, d.date, d.at)
            FireDecision.Skip -> Unit
        }
    }

    private companion object { const val TAG = "NoorAlert" }
}

/** Restart, app update, clock or time-zone change, or exact-alarm permission granted: re-arm everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        alertScope.launch {
            try { AlertScheduler.reschedule(app) } finally { pending.finish() }
        }
    }
}

/** Turns one due alert into sound, vibration and notification, respecting the device's state and the user's choices. */
internal object AlertDispatcher {

    /** Why an audible alert was made quiet; shown in the notification so the user knows what happened. */
    enum class Quiet { DND, RINGER_SILENT, IN_CALL, NO_NOTIFICATIONS }

    fun quietReason(context: Context, alerts: AlertSettings): Quiet? {
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(AudioManager::class.java)
        if (am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION)) return Quiet.IN_CALL
        if (!AlertNotifications.canPost(context, AlertNotifications.CHANNEL_ADHAN)) return Quiet.NO_NOTIFICATIONS
        if (!alerts.playInDnd) {
            if (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return Quiet.DND
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return Quiet.RINGER_SILENT
        }
        return null
    }

    fun dispatch(context: Context, config: WatchConfig, alerts: AlertSettings, prayer: Prayer, date: LocalDate, at: Instant,
                 alert: PrayerAlert, test: Boolean) {
        AlertNotifications.ensureChannels(context)
        val text = AlertText(context, config)
        val title = text.title(prayer, date)
        val body = text.body(prayer, at)
        val id = AlertNotifications.idFor(prayer)
        val testNote = if (test) text.string(R.string.noor_alert_test_note) else null

        when (alert.mode) {
            AlertMode.NOTIFICATION -> { AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at, testNote); return }
            AlertMode.SILENT -> { AlertNotifications.post(context, id, AlertNotifications.CHANNEL_SILENT, title, body, at, testNote); return }
            else -> Unit
        }

        val quiet = quietReason(context, alerts)
        if (quiet != null) {
            val note = when (quiet) {
                Quiet.DND -> R.string.noor_alert_quiet_dnd
                Quiet.RINGER_SILENT -> R.string.noor_alert_quiet_ringer
                Quiet.IN_CALL -> R.string.noor_alert_quiet_call
                Quiet.NO_NOTIFICATIONS -> null // Nothing can be shown, so nothing may play either: a sound must always have a Stop button.
            } ?: return
            val am = context.getSystemService(AudioManager::class.java)
            // Silent ringer still means "vibrate" when the user set the phone to vibrate and wants vibration.
            if (quiet == Quiet.RINGER_SILENT && alert.vibrate && am?.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AdhanService.vibrate(context)
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_SILENT, title, body, at, listOfNotNull(text.string(note), testNote).joinToString("\n"))
            return
        }

        val custom = CustomSounds(context)
        val resolved = SoundLibrary.get(context).resolve(prayer, alert, custom)
        if (resolved == null) {
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at, testNote); return
        }
        val detail = listOfNotNull(describe(text, resolved), substitutionNote(text, resolved.substitution), testNote).joinToString("\n").ifEmpty { null }
        val req = PlayRequest(id, title, body, at.toEpochMilli(), detail, text.string(R.string.noor_alert_stop), resolved.source,
            alert.volumePercent, alerts.gradualSeconds, alert.maxSeconds, alert.vibrate, keepNotification = true)
        try {
            AdhanService.start(context, req)
        } catch (e: Exception) {
            // Android 12+ refuses to start playback from the background when the alarm was not exact (no permission).
            val blocked = Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at,
                listOfNotNull(if (blocked) text.string(R.string.noor_alert_blocked_background) else null, testNote).joinToString("\n").ifEmpty { null })
        }
    }

    private fun describe(text: AlertText, r: ResolvedSound): String? = when {
        r.customName != null -> r.customName
        r.entry == null -> null
        r.short -> text.string(R.string.noor_alert_short_of, r.entry.title(text.arabic))
        else -> r.entry.title(text.arabic)
    }

    private fun substitutionNote(text: AlertText, s: Substitution?): String? = when (s) {
        null -> null
        Substitution.FULL_NOT_ON_DEVICE -> text.string(R.string.noor_alert_sub_full)
        Substitution.NO_FULL_RECORDING -> text.string(R.string.noor_alert_sub_no_full)
        Substitution.CUSTOM_MISSING -> text.string(R.string.noor_alert_sub_custom)
        Substitution.SOUND_MISSING -> text.string(R.string.noor_alert_sub_sound)
        Substitution.FAJR_ADHAN_OUTSIDE_FAJR -> text.string(R.string.noor_alert_sub_fajr)
    }
}
