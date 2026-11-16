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
import com.noor.prayer.alerts.AlarmPlanner
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
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
        val firedAt = System.currentTimeMillis()
        val pending = goAsync()
        // (0.17: the user's alarms are neither a prayer alert nor in its history, like the reminders.)
        val alarm = intent.action == AlertScheduler.ACTION_ALARM || intent.action == AlertScheduler.ACTION_ALARM_DISMISS
        val reminder = intent.action == AlertScheduler.ACTION_REMIND || alarm
        alertScope.launch {
            try {
                when (intent.action) {
                    AlertScheduler.ACTION_REMIND -> remind(app, intent, firedAt)
                    AlertScheduler.ACTION_ALARM -> alarm(app, intent, firedAt)
                    AlertScheduler.ACTION_ALARM_DISMISS -> AlertScheduler.cancelSnooze(app, intent.getIntExtra(AlertScheduler.EXTRA_ALARM, 0))
                    else -> handle(app, intent, firedAt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Alert failed", e)
                // A reminder is only a notification; the alert history is for the prayer alerts themselves.
                if (!reminder) runCatching { AlertDispatcher.record(app, intent, firedAt, AlertOutcome.FAILED, detail = "${e.javaClass.simpleName}: ${e.message}") }
            } finally {
                runCatching { AlertScheduler.reschedule(app) }
                if (!reminder) runCatching { app.alertHost?.onPrayerAlarm(app) }
                pending.finish()
            }
        }
    }

    /** 0.9.9: a reminder before a prayer ("Asr in 15 minutes"), re-checked against the current times and choices. */
    private suspend fun remind(context: Context, intent: Intent, firedAt: Long) {
        val prayer = intent.getStringExtra(AlertScheduler.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: return
        val date = intent.getStringExtra(AlertScheduler.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val host = context.alertHost ?: return
        val config = host.alertConfig() ?: return
        val alerts = config.alerts ?: return
        // 0.16: or a reminder after it (the iqamah).
        val due = if (intent.getBooleanExtra(AlertScheduler.EXTRA_AFTER, false))
            AlertPlanner.onAfter(config, alerts, host.alertDevice, prayer, date, Instant.ofEpochMilli(firedAt))
        else AlertPlanner.onRemind(config, alerts, host.alertDevice, prayer, date, Instant.ofEpochMilli(firedAt))
        AlertNotifications.postReminder(context, config, due ?: return)
    }

    /**
     * 0.17: one of the user's alarms (before or after a prayer), checked again against the current times and choices;
     * or it rings again after Snooze; or it is being tried.
     */
    private suspend fun alarm(context: Context, intent: Intent, firedAt: Long) {
        val id = intent.getIntExtra(AlertScheduler.EXTRA_ALARM, 0)
        val date = intent.getStringExtra(AlertScheduler.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val host = context.alertHost ?: return
        val config = host.alertConfig() ?: return
        val alerts = config.alerts ?: AlertSettings()
        val now = Instant.ofEpochMilli(firedAt)
        val text = intent.getStringExtra(AlertScheduler.EXTRA_ALARM_TEXT)
        val tried = text?.let { PrayerAlarm.decode(it) }
        val snoozed = intent.getBooleanExtra(AlertScheduler.EXTRA_SNOOZE, false)
        if (snoozed) AlertNotifications.cancel(context, AlertNotifications.snoozedId(id))
        val planned = when {
            tried != null -> PlannedAlarm(tried, date, config.day(date).instant(tried.prayer) ?: now, now)
            // (Snoozed: it rings again unless it was turned off or deleted meanwhile.)
            snoozed -> alerts.alarms.firstOrNull { it.id == id && it.enabled }?.let { a -> PlannedAlarm(a, date, config.day(date).instant(a.prayer) ?: now, now) }
            else -> AlarmPlanner.onFire(config, alerts, id, date, now)
        } ?: return
        AlarmDispatcher.ring(context, config, alerts, planned, tried = text.takeIf { tried != null })
    }

    private suspend fun handle(context: Context, intent: Intent, firedAt: Long) {
        val prayer = intent.getStringExtra(AlertScheduler.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: return
        val date = intent.getStringExtra(AlertScheduler.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val host = context.alertHost
        val config = host?.alertConfig()
        val alerts = config?.alerts
        if (host == null || config == null || alerts == null) {
            AlertDispatcher.record(context, intent, firedAt, AlertOutcome.NO_SETTINGS); return
        }
        if (intent.action == AlertScheduler.ACTION_TEST) {
            val at = Instant.ofEpochMilli(intent.getLongExtra(AlertScheduler.EXTRA_AT, firedAt))
            AlertDispatcher.dispatch(context, config, alerts, prayer, date, at, alerts.alert(prayer).copy(enabled = true), test = true, firedAtMs = firedAt)
            return
        }
        when (val d = AlertPlanner.onFire(config, alerts, host.alertDevice, prayer, date, Instant.ofEpochMilli(firedAt))) {
            is FireDecision.Alert -> AlertDispatcher.dispatch(context, config, alerts, d.prayer, d.date, d.at, d.alert, test = false, firedAtMs = firedAt)
            is FireDecision.Late -> {
                AlertNotifications.postLate(context, config, d.prayer, d.date, d.at)
                AlertDispatcher.record(context, intent, firedAt, AlertOutcome.LATE, dueAtMs = d.at.toEpochMilli())
            }
            // Moved, turned off or moved to the other device since the alarm was armed: nothing is due, nothing to record.
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

    /** Why an audible alert cannot sound. */
    enum class Quiet {
        /** Do Not Disturb is on and the user chose to stay quiet then. */
        DND,
        /** The ringer is on silent or vibrate and the user chose to stay quiet then. */
        RINGER_SILENT,
        IN_CALL,
        NO_NOTIFICATIONS,
        /** Do Not Disturb is set to block alarms as well ("total silence", or alarms not allowed). */
        DND_BLOCKS_ALARMS,
    }

    fun quietReason(context: Context, alerts: AlertSettings): Quiet? {
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(AudioManager::class.java)
        if (am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION)) return Quiet.IN_CALL
        if (!AlertNotifications.canPost(context, AlertNotifications.CHANNEL_ADHAN)) return Quiet.NO_NOTIFICATIONS
        if (alerts.quietWhenSilent) {
            if (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return Quiet.DND
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return Quiet.RINGER_SILENT
        } else if (nm != null && dndBlocksAlarms(nm)) {
            // The Adhan plays like an alarm, so vibrate and silent do not stop it, but a Do Not Disturb that blocks alarms
            // mutes the alarm stream: playing would show a "playing" notification with no sound. Say so instead.
            return Quiet.DND_BLOCKS_ALARMS
        }
        return null
    }

    /** True when Do Not Disturb is on in a mode that does not let alarms through. */
    fun dndBlocksAlarms(nm: NotificationManager): Boolean = when (nm.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_NONE -> true
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Build.VERSION.SDK_INT >= 28 && runCatching {
            nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
        }.getOrDefault(false)
        else -> false
    }

    /** A history entry with this device's state now (exact alarms, alarm volume, ringer). */
    fun recordFor(context: Context, prayer: Prayer?, test: Boolean, dueAtMs: Long, firedAtMs: Long, outcome: AlertOutcome,
                  sound: String? = null, detail: String? = null): AlertRecord {
        val am = context.getSystemService(AudioManager::class.java)
        return AlertRecord(
            id = firedAtMs * 16 + (prayer?.ordinal ?: 15),
            prayer = prayer, test = test, dueAtMs = dueAtMs, firedAtMs = firedAtMs, outcome = outcome, sound = sound,
            exactAlarms = AlertScheduler.canScheduleExact(context),
            alarmVolumePercent = Math.round(AlertPlayer.alarmVolume(context) * 100),
            ringerMode = am?.ringerMode ?: -1,
            detail = detail,
        )
    }

    /** Records an alert that ended before a prayer could be chosen (no settings, late, crash) straight from the alarm. */
    fun record(context: Context, intent: Intent, firedAtMs: Long, outcome: AlertOutcome, dueAtMs: Long? = null, detail: String? = null) {
        val prayer = intent.getStringExtra(AlertScheduler.EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        val due = dueAtMs ?: intent.getLongExtra(AlertScheduler.EXTRA_AT, firedAtMs)
        AlertLog.add(context, recordFor(context, prayer, intent.action == AlertScheduler.ACTION_TEST, due, firedAtMs, outcome, detail = detail))
    }

    fun dispatch(context: Context, config: WatchConfig, alerts: AlertSettings, prayer: Prayer, date: LocalDate, at: Instant,
                 alert: PrayerAlert, test: Boolean, firedAtMs: Long = System.currentTimeMillis()) {
        AlertNotifications.ensureChannels(context)
        // The prayer has come: its reminder has done its job.
        AlertNotifications.cancelReminder(context, prayer)
        val text = AlertText(context, config)
        val title = text.title(prayer, date)
        val body = text.body(prayer, at)
        val id = AlertNotifications.idFor(prayer)
        val testNote = if (test) text.string(R.string.noor_alert_test_note) else null
        val chosen = alert.soundId ?: alert.mode.name
        fun log(outcome: AlertOutcome, detail: String? = null): AlertRecord =
            recordFor(context, prayer, test, at.toEpochMilli(), firedAtMs, outcome, chosen, detail).also { AlertLog.add(context, it) }
        fun notes(vararg n: String?) = listOfNotNull(*n).joinToString("\n").ifEmpty { null }

        when (alert.mode) {
            AlertMode.NOTIFICATION -> {
                AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at, testNote); log(AlertOutcome.NOTIFICATION); return
            }
            AlertMode.SILENT -> {
                AlertNotifications.post(context, id, AlertNotifications.CHANNEL_SILENT, title, body, at, testNote); log(AlertOutcome.SILENT); return
            }
            else -> Unit
        }

        val quiet = quietReason(context, alerts)
        if (quiet != null) {
            if (quiet == Quiet.NO_NOTIFICATIONS) {
                // Nothing can be shown, so nothing may play either: a sound must always have a Stop button.
                log(AlertOutcome.NO_NOTIFICATIONS); return
            }
            val (note, outcome) = when (quiet) {
                Quiet.DND -> R.string.noor_alert_quiet_dnd to AlertOutcome.QUIET_BY_CHOICE
                Quiet.RINGER_SILENT -> R.string.noor_alert_quiet_ringer to AlertOutcome.QUIET_BY_CHOICE
                Quiet.IN_CALL -> R.string.noor_alert_quiet_call to AlertOutcome.IN_CALL
                Quiet.DND_BLOCKS_ALARMS -> R.string.noor_alert_dnd_blocks_alarms to AlertOutcome.DND_BLOCKS_ALARMS
                Quiet.NO_NOTIFICATIONS -> error("handled above")
            }
            val am = context.getSystemService(AudioManager::class.java)
            // Silent ringer still means "vibrate" when the user set the phone to vibrate and wants vibration.
            if (quiet == Quiet.RINGER_SILENT && alert.vibrate && am?.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AdhanService.vibrate(context)
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_SILENT, title, body, at, notes(text.string(note), testNote))
            log(outcome); return
        }

        val custom = CustomSounds(context)
        val library = SoundLibrary.get(context)
        val resolved = library.resolve(prayer, alert, custom, alerts.favourites, date)
        val isAdhan = alert.mode == AlertMode.ADHAN || alert.mode == AlertMode.ADHAN_SHORT
        if (resolved == null) {
            // No sound file at all on this device: the backup Adhan (played by Android) is still better than nothing.
            if (isAdhan) {
                AlertNotifications.postBackup(context, id, title, body, at, notes(text.string(R.string.noor_alert_fallback_played), testNote))
                log(AlertOutcome.BACKUP_SOUND, "no sound file")
            } else {
                AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at, notes(text.string(R.string.noor_alert_failed), testNote))
                log(AlertOutcome.FAILED, "no sound file")
            }
            return
        }
        val detail = notes(describe(text, resolved), substitutionNote(text, resolved.substitution), testNote)
        // For "Random from favourites", the history names the recording that actually plays.
        val record = log(AlertOutcome.STARTING).let { r -> resolved.entry?.id?.takeIf { alert.soundId == com.noor.prayer.alerts.SoundIds.RANDOM }
            ?.let { picked -> r.copy(sound = picked).also { AlertLog.add(context, it) } } ?: r }
        val req = PlayRequest(id, title, body, at.toEpochMilli(), detail, text.string(R.string.noor_alert_stop), resolved.source,
            alert.volumePercent, alerts.gradualSeconds, alert.maxSeconds, alert.vibrate, keepNotification = true,
            logId = record.id, fallback = library.emergencySource(resolved.source),
            fallbackNote = text.string(R.string.noor_alert_fallback_played), failedNote = text.string(R.string.noor_alert_failed))
        try {
            AdhanService.start(context, req)
        } catch (e: Exception) {
            // Android 12+ refuses to start playback from the background when the alarm was not exact (no permission).
            val blocked = Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException
            val why = "${e.javaClass.simpleName}: ${e.message}"
            if (isAdhan) {
                // Android itself plays the short Adhan of the backup channel, which needs no permission to start.
                AlertNotifications.postBackup(context, id, title, body, at, notes(text.string(R.string.noor_alert_backup_played), testNote))
                AlertLog.update(context, record.id) { it.copy(outcome = AlertOutcome.BACKUP_SOUND, detail = why) }
            } else {
                AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, at,
                    notes(if (blocked) text.string(R.string.noor_alert_blocked_background) else text.string(R.string.noor_alert_failed), testNote))
                AlertLog.update(context, record.id) { it.copy(outcome = AlertOutcome.FAILED, detail = why) }
            }
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

/** 0.17: turns one of the user's alarms into a ringing alarm (or, for a notification-only alarm, a notification). */
internal object AlarmDispatcher {
    fun ring(context: Context, config: WatchConfig, alerts: AlertSettings, p: PlannedAlarm, tried: String? = null) {
        AlertNotifications.ensureChannels(context)
        val text = AlertText(context, config)
        val a = p.alarm
        val title = text.alarmTitle(a, p.date)
        val body = text.alarmBody(a, p.date, p.prayerAt)
        val id = AlertNotifications.alarmId(a.id)
        if (a.sound == AlarmSound.NOTIFICATION) { AlertNotifications.postAlarmNote(context, a.id, title, body, p.at); return }

        // The same rules as the Adhan: never during a call, never without a notification to stop it, and quiet in
        // silent mode only if the user chose that.
        when (val quiet = AlertDispatcher.quietReason(context, alerts)) {
            null -> Unit
            AlertDispatcher.Quiet.NO_NOTIFICATIONS -> return
            else -> {
                val am = context.getSystemService(AudioManager::class.java)
                if (quiet == AlertDispatcher.Quiet.RINGER_SILENT && a.vibrate && am?.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AdhanService.vibrate(context)
                val note = when (quiet) {
                    AlertDispatcher.Quiet.DND -> R.string.noor_alert_quiet_dnd
                    AlertDispatcher.Quiet.RINGER_SILENT -> R.string.noor_alert_quiet_ringer
                    AlertDispatcher.Quiet.IN_CALL -> R.string.noor_alert_quiet_call
                    else -> R.string.noor_alert_dnd_blocks_alarms
                }
                AlertNotifications.post(context, id, AlertNotifications.CHANNEL_SILENT, title, body, p.at, text.string(note))
                return
            }
        }

        val phone = phoneAlarmSound(context)
        val own = if (a.sound == AlarmSound.CUSTOM) a.customId?.let { cid -> CustomSounds(context).let { c -> c.get(cid)?.let { c.file(it) } } } else null
        val source: SoundSource? = own?.let { SoundSource.LocalFile(it) } ?: phone
        val fallback: SoundSource? = if (own != null) phone else SoundSource.System(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString()).takeIf { it != source }
        if (source == null) {
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, p.at, text.string(R.string.noor_alarm_failed)); return
        }
        val req = PlayRequest(id, title, body, p.at.toEpochMilli(), null, text.string(R.string.noor_alert_stop), source,
            volumePercent = 100, gradualSeconds = alerts.gradualSeconds, maxSeconds = RING_SECONDS, vibrate = a.vibrate, keepNotification = false,
            fallback = fallback, failedNote = text.string(R.string.noor_alarm_failed),
            alarmId = a.id, snoozeMinutes = a.snoozeMinutes, loop = true, alarmDate = p.date.toString(),
            snoozeLabel = if (a.snoozeMinutes > 0) text.string(R.string.noor_alarm_snooze, text.number(a.snoozeMinutes)) else null,
            alarmText = tried)
        try {
            AdhanService.start(context, req)
        } catch (e: Exception) {
            Log.e("NoorAlert", "Alarm could not ring", e)
            AlertNotifications.post(context, id, AlertNotifications.CHANNEL_NOTIFY, title, body, p.at, text.string(R.string.noor_alarm_failed))
        }
    }

    /** An alarm left ringing stops by itself after ten minutes, as the Clock app's do. */
    const val RING_SECONDS = 600

    /** The phone's alarm sound (or, if it has none, its ringtone), as the Clock app would ring with. */
    fun phoneAlarmSound(context: Context): SoundSource? = runCatching {
        (android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_ALARM)
            ?: android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE))
            ?.let { SoundSource.System(it.toString()) }
    }.getOrNull() ?: SoundSource.System(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString())
}
