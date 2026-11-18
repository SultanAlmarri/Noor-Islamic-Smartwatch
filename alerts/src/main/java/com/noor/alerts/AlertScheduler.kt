package com.noor.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlarmPlanner
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
import com.noor.prayer.alerts.PlannedAlert
import com.noor.prayer.alerts.PlannedReminder
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** What is armed right now, for the Adhan screen ("Next: Asr at 3:24 PM, exact"). */
data class ScheduleStatus(val next: PlannedAlert?, val count: Int, val exact: Boolean, val updatedAtMs: Long)

/**
 * Arms one alarm per prayer (its next occurrence) with AlarmManager. Re-run whenever anything that affects the times or
 * the choices changes, after every alarm, after a restart and after clock or time-zone changes; it is idempotent.
 */
object AlertScheduler {
    private const val REQUEST_BASE = 7100
    const val ACTION_FIRE = "com.noor.alerts.FIRE"
    const val ACTION_TEST = "com.noor.alerts.TEST"
    /** 0.9.9: a reminder before a prayer. */
    const val ACTION_REMIND = "com.noor.alerts.REMIND"
    /** Reminder alarms use request codes REQUEST_BASE + 20 + prayer (the test alarm uses + 50). */
    private const val REMINDER_OFFSET = 20
    /** 0.16: reminders after a prayer (the iqamah) use REQUEST_BASE + 30 + prayer, with the same action. */
    private const val AFTER_OFFSET = 30
    const val EXTRA_AFTER = "after"
    /** 0.17: the user's alarms before and after prayers: REQUEST_BASE + 100 + id; snoozed, + 200 + id; tried, + 60. */
    const val ACTION_ALARM = "com.noor.alerts.ALARM"
    /** 0.17: "Dismiss" on a snoozed alarm's note. */
    const val ACTION_ALARM_DISMISS = "com.noor.alerts.ALARM_DISMISS"
    private const val ALARM_OFFSET = 100
    private const val SNOOZE_OFFSET = 200
    const val EXTRA_ALARM = "alarm"
    const val EXTRA_SNOOZE = "snooze"
    /** An alarm being tried: the alarm itself (it rings whether or not it is saved). */
    const val EXTRA_ALARM_TEXT = "alarmText"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_DATE = "date"
    const val EXTRA_AT = "at"

    @Volatile var lastStatus: ScheduleStatus? = null
        private set

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /**
     * The times and choices the alarms follow: the app's own; before the phone's first unlock after a restart (Direct
     * Boot, 0.22), when the app's settings cannot be read, the copy kept with the alarms the last time they were armed.
     */
    internal suspend fun configFor(context: Context): WatchConfig? {
        if (!AlertStore.unlocked(context)) return AlertStore.snapshot(context)
        return runCatching { context.alertHost?.alertConfig() }.getOrElse { AlertStore.snapshot(context) }
    }

    suspend fun reschedule(context: Context): ScheduleStatus {
        val host = context.alertHost
        val unlocked = AlertStore.unlocked(context)
        val config = configFor(context)
        // (Kept for Direct Boot: what the alarms need, outside the app's credential-protected files.)
        if (unlocked && host != null) runCatching { AlertStore.saveSnapshot(context, config) }
        val now = Instant.now()
        val alerts = config?.alerts
        // (0.22: a prayer of a day already dealt with is never planned again; one armed and due but not yet delivered is
        // kept, not replaced by the next day's.)
        val plan = if (host != null && alerts != null) AlertPlanner.plan(config, alerts, host.alertDevice, now,
            AlertStore.handled(context), AlertStore.pending(context)) else emptyList()
        val reminders = if (host != null && alerts != null) AlertPlanner.reminders(config, alerts, host.alertDevice, now) +
            AlertPlanner.afterReminders(config, alerts, host.alertDevice, now) else emptyList()
        // (0.17: the alarms ring on the phone only, whichever device the Adhan is on, and whether or not it is on.)
        val alarms = if (host?.ringsAlarms == true && config != null && alerts != null) AlarmPlanner.plan(config, alerts, now) else emptyList()
        applyAlarms(context, alarms)
        if (host?.ringsAlarms == true) runCatching { rearmSnoozes(context) }
        return apply(context, config, plan, reminders)
    }

    /**
     * 0.17: one alarm-clock alarm for each of the user's alarms that is on (its next ring), as the Clock app sets them:
     * exact even in deep sleep, and shown by Android as the next alarm.
     */
    private fun applyAlarms(context: Context, alarms: List<PlannedAlarm>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = canScheduleExact(context)
        val byId = alarms.associateBy { it.alarm.id }
        for (id in 1..PrayerAlarm.MAX) {
            val p = byId[id]
            if (p == null) {
                alarmPending(context, id, null, PendingIntent.FLAG_NO_CREATE)?.let { pi -> am?.cancel(pi); pi.cancel() }
                continue
            }
            val pi = alarmPending(context, id, p, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            arm(context, am, p.at.toEpochMilli(), pi, exact)
        }
    }

    private fun alarmPending(context: Context, id: Int, p: PlannedAlarm?, flag: Int): PendingIntent? {
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_ALARM, id)
        if (p != null) intent.putExtra(EXTRA_DATE, p.date.toString()).putExtra(EXTRA_AT, p.at.toEpochMilli())
        return PendingIntent.getBroadcast(context, REQUEST_BASE + ALARM_OFFSET + id, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /** [slot]: the alarm's id, or 0 for a tried alarm (its snooze never replaces a real one's). */
    private fun snoozePending(context: Context, slot: Int, flag: Int, extras: (Intent) -> Unit = {}): PendingIntent? {
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_SNOOZE, true)
        extras(intent)
        return PendingIntent.getBroadcast(context, REQUEST_BASE + SNOOZE_OFFSET + slot, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /** 0.17: the alarm that was ringing ([req]) rings again in its snooze minutes; a note says until when. */
    internal fun snooze(context: Context, req: PlayRequest) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + req.snoozeMinutes * 60_000L
        val slot = if (req.alarmText != null) 0 else req.alarmId
        val date = req.alarmDate ?: LocalDate.now().toString()
        armSnooze(context, am, slot, req.alarmId, date, at, req.alarmText)
        // (0.18: kept, so a restart of the phone during the snooze arms it again.)
        snoozes(context).edit().putString(slot.toString(), listOf(req.alarmId, date, at, req.alarmText ?: "").joinToString("|")).apply()
        alertScope.launch {
            val config = runCatching { context.alertHost?.alertConfig() }.getOrNull() ?: return@launch
            val text = AlertText(context, config)
            AlertNotifications.postSnoozed(context, slot, req.title, text.string(R.string.noor_alarm_snoozed, text.time(Instant.ofEpochMilli(at))),
                Instant.ofEpochMilli(at), text.string(R.string.noor_alarm_dismiss))
        }
    }

    private fun armSnooze(context: Context, am: AlarmManager, slot: Int, alarmId: Int, date: String, at: Long, alarmText: String?) {
        val pi = snoozePending(context, slot, PendingIntent.FLAG_UPDATE_CURRENT) { i ->
            i.putExtra(EXTRA_ALARM, alarmId).putExtra(EXTRA_DATE, date).putExtra(EXTRA_AT, at)
            if (alarmText != null) i.putExtra(EXTRA_ALARM_TEXT, alarmText)
        } ?: return
        arm(context, am, at, pi, canScheduleExact(context))
    }

    /** 0.22: in device-protected storage (a snooze before the first unlock after a restart), moved there once from the app's own. */
    private fun snoozes(context: Context): android.content.SharedPreferences {
        val de = AlertStore.deviceContext(context)
        if (de !== context.applicationContext && AlertStore.unlocked(context) && !movedSnoozes) {
            movedSnoozes = true
            runCatching { de.moveSharedPreferencesFrom(context.applicationContext, "noor_snoozes") }
        }
        return de.getSharedPreferences("noor_snoozes", Context.MODE_PRIVATE)
    }
    @Volatile private var movedSnoozes = false

    /** 0.18: a snooze has rung (or was cancelled): forget it. */
    internal fun snoozeDone(context: Context, slot: Int) { snoozes(context).edit().remove(slot.toString()).apply() }

    /** 0.18: the snoozes still to come, armed again (after a restart, alarms set before it are gone). */
    private fun rearmSnoozes(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for ((key, v) in snoozes(context).all) {
            val f = (v as? String)?.split('|', limit = 4) ?: continue
            val slot = key.toIntOrNull() ?: continue
            val at = f.getOrNull(2)?.toLongOrNull() ?: continue
            if (at + AlarmPlanner.MAX_LATENESS.toMillis() < now) { snoozeDone(context, slot); continue }
            armSnooze(context, am, slot, f[0].toIntOrNull() ?: continue, f[1], at, f.getOrNull(3)?.ifEmpty { null })
        }
    }

    /** 0.17: a snoozed alarm will not ring again (its note's Dismiss, or the alarm changed, turned off or deleted); 0: a tried one. */
    fun cancelSnooze(context: Context, id: Int) {
        snoozeDone(context, id)
        snoozePending(context, id, PendingIntent.FLAG_NO_CREATE)?.let { pi -> context.getSystemService(AlarmManager::class.java)?.cancel(pi); pi.cancel() }
        AlertNotifications.cancel(context, AlertNotifications.snoozedId(id))
    }

    /**
     * 0.17: [alarm] rings in [seconds] exactly as it will (its sound, vibration and Snooze), saved or not: the same
     * kind of alarm, the same receiver and the same ringing screen.
     */
    fun tryAlarm(context: Context, alarm: PrayerAlarm, seconds: Int = 5): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        val at = System.currentTimeMillis() + seconds * 1000L
        val a = if (alarm.id in 1..PrayerAlarm.MAX) alarm else alarm.copy(id = PrayerAlarm.MAX)
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_ALARM, a.id)
            .putExtra(EXTRA_DATE, LocalDate.now().toString()).putExtra(EXTRA_AT, at).putExtra(EXTRA_ALARM_TEXT, a.normalized().encode())
        val pi = PendingIntent.getBroadcast(context, REQUEST_BASE + 60, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching { arm(context, am, at, pi, canScheduleExact(context)) }.isSuccess
    }

    internal fun apply(context: Context, config: WatchConfig?, plan: List<PlannedAlert>, reminders: List<PlannedReminder> = emptyList()): ScheduleStatus {
        applyReminders(context, reminders)
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = canScheduleExact(context)
        val byPrayer = plan.associateBy { it.prayer }
        for (p in Prayer.entries) {
            val planned = byPrayer[p]
            if (planned == null) {
                pending(context, p, null, PendingIntent.FLAG_NO_CREATE)?.let { pi -> am?.cancel(pi); pi.cancel() }
                continue
            }
            val pi = pending(context, p, planned, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            arm(context, am, planned.at.toEpochMilli(), pi, exact)
        }
        runCatching { AlertStore.setArmed(context, plan) }
        if (config != null) AlertNotifications.ensureChannels(context)
        return ScheduleStatus(plan.minByOrNull { it.at }, plan.size, exact, System.currentTimeMillis()).also { lastStatus = it }
    }

    /**
     * 0.9.7: prayer alarms are alarm-clock alarms (AlarmManager.setAlarmClock), as an alarm clock's are: delivered at
     * the exact minute even in deep sleep, never deferred by battery management, and the app may show its Adhan screen
     * and play while the phone is locked. Android 17 silences sound from apps in the background unless they are visible
     * or properly in the foreground; plain exact alarms were enough before it, not after (the Adhan stopped within a
     * second or two at real prayer times while the in-app test, started with the app open, played in full).
     * Side effect: Android shows its alarm icon and "next alarm" for the next prayer. Without the exact-alarm
     * permission, an inexact alarm is used and the backup Adhan takes over (see the dispatcher).
     */
    private fun arm(context: Context, am: AlarmManager?, at: Long, pi: PendingIntent, exact: Boolean) {
        runCatching {
            when {
                exact && context.alertHost?.alarmClockAlarms == true -> am?.setAlarmClock(AlarmManager.AlarmClockInfo(at, AlertNotifications.contentIntent(context)), pi)
                exact -> am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure {
            // The exact-alarm permission was revoked between the check and the call.
            runCatching { am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    /**
     * 0.9.9: one reminder alarm per prayer (its next reminder). Plain exact alarms, not alarm-clock ones: a reminder
     * only posts a notification (Android plays its sound), and Android's "next alarm" should stay the Adhan itself.
     */
    private fun applyReminders(context: Context, reminders: List<PlannedReminder>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = canScheduleExact(context)
        // (0.16: before and after each prayer, each its own alarm.)
        for (after in listOf(false, true)) for (p in Prayer.entries) {
            val r = reminders.firstOrNull { it.prayer == p && it.after == after }
            if (r == null) {
                reminderPending(context, p, null, PendingIntent.FLAG_NO_CREATE, after)?.let { pi -> am?.cancel(pi); pi.cancel() }
                continue
            }
            val pi = reminderPending(context, p, r, PendingIntent.FLAG_UPDATE_CURRENT, after) ?: continue
            val at = r.remindAt.toEpochMilli()
            runCatching {
                if (exact) am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }.onFailure { runCatching { am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } }
        }
    }

    private fun reminderPending(context: Context, p: Prayer, r: PlannedReminder?, flag: Int, after: Boolean = false): PendingIntent? {
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_PRAYER, p.name).putExtra(EXTRA_AFTER, after)
        if (r != null) intent.putExtra(EXTRA_DATE, r.date.toString()).putExtra(EXTRA_AT, r.at.toEpochMilli())
        return PendingIntent.getBroadcast(context, REQUEST_BASE + (if (after) AFTER_OFFSET else REMINDER_OFFSET) + p.ordinal, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pending(context: Context, p: Prayer, planned: PlannedAlert?, flag: Int): PendingIntent? {
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_PRAYER, p.name)
        if (planned != null) intent.putExtra(EXTRA_DATE, planned.date.toString()).putExtra(EXTRA_AT, planned.at.toEpochMilli())
        return PendingIntent.getBroadcast(context, REQUEST_BASE + p.ordinal, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Sends a real test alert through the whole path (alarm → receiver → player → notification) in [seconds],
     * using the current settings of [prayer]. This is what to use to check the Adhan on a real device.
     */
    fun scheduleTest(context: Context, prayer: Prayer, seconds: Int = 10): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        val at = System.currentTimeMillis() + seconds * 1000L
        val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_TEST).putExtra(EXTRA_PRAYER, prayer.name)
            .putExtra(EXTRA_DATE, LocalDate.now().toString()).putExtra(EXTRA_AT, at)
        val pi = PendingIntent.getBroadcast(context, REQUEST_BASE + 50, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The same kind of alarm as a real prayer, so the test goes the same way.
        return runCatching { arm(context, am, at, pi, canScheduleExact(context)) }.isSuccess
    }
}
