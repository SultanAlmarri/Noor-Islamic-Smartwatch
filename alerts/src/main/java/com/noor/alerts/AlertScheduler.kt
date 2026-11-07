package com.noor.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertPlanner
import com.noor.prayer.alerts.PlannedAlert
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
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_DATE = "date"
    const val EXTRA_AT = "at"

    @Volatile var lastStatus: ScheduleStatus? = null
        private set

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    suspend fun reschedule(context: Context): ScheduleStatus {
        val host = context.alertHost
        val config = runCatching { host?.alertConfig() }.getOrNull()
        return apply(context, config, host?.let { config?.alerts?.let { a -> AlertPlanner.plan(config, a, it.alertDevice, Instant.now()) } }.orEmpty())
    }

    internal fun apply(context: Context, config: WatchConfig?, plan: List<PlannedAlert>): ScheduleStatus {
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
