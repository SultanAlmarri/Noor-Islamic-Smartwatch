package com.noor.app.ui.adhan

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.AlertOutcome
import com.noor.alerts.AlertRecord
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * What actually happened at each alert on this phone: played (and for how long), replaced, made quiet and why, late.
 * This is the evidence that the Adhan sounds on a real phone, and what the owner shares when it does not.
 * 0.25: the page opens on the history itself (a test button led it; the test is on the Adhan tab, one step back, and
 * its result comes here); under it, sharing the report, and the one piece of advice that is not a fault of the set-up.
 */
@Composable
fun AlertHistoryScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: AdhanViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val history by vm.history.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    // (Not twice on entry: the view model has just done it; once each time the page comes back.)
    LifecycleResumeEffect(Unit) { vm.onShown(); onPauseOrDispose { } }
    val message by vm.message.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(message) { message?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show(); vm.consumeMessage() } }

    com.noor.app.ui.components.NoorPage(stringResource(R.string.history_title), contentPadding, onBack = onBack) {
        if (history.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), padding = 18.dp, corner = 24.dp) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        } else {
            GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 24.dp) {
                Column {
                    history.forEachIndexed { i, r ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 40.dp), thickness = 0.5.dp, color = c.separator)
                        // (0.15: records of the last two days say how to fix what went wrong; older ones only tell.)
                        HistoryRow(r, f, zone, fixable = System.currentTimeMillis() - r.dueAtMs < 48L * 3600_000L)
                    }
                }
            }
        }
        com.noor.app.ui.components.NoorButton(stringResource(R.string.history_share), {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Minhad alert report")
                .putExtra(Intent.EXTRA_TEXT, vm.report())
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth(), kind = com.noor.app.ui.components.ButtonKind.Tonal)
        // (Until 0.24 at the foot of "More settings".)
        val ready by vm.readiness.collectAsStateWithLifecycle()
        if (s.alerts.enabled && s.alerts.device.includes(com.noor.prayer.alerts.AlertDevice.PHONE) && ready.batteryRestricted) {
            com.noor.app.ui.settings.SettingsSection("") {
                WarningRow(stringResource(R.string.adhan_warn_battery), stringResource(R.string.adhan_action_open),
                    { context.open(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, icon = androidx.compose.material.icons.Icons.Rounded.BatterySaver)
            }
        }
    }
}

/** Green when sound came out as chosen, gold when something else was heard or the user chose quiet, red when nothing was heard. */
@Composable
internal fun outcomeColor(o: AlertOutcome): Color {
    val c = NoorTheme.colors
    return when (o) {
        AlertOutcome.PLAYED, AlertOutcome.NOTIFICATION, AlertOutcome.SILENT, AlertOutcome.QUIET_BY_CHOICE -> c.success
        AlertOutcome.PLAYED_FALLBACK, AlertOutcome.BACKUP_SOUND, AlertOutcome.STARTING, AlertOutcome.IN_CALL -> c.warning
        else -> c.danger
    }
}

@Composable
internal fun outcomeText(r: AlertRecord, f: Formatter): String = when (r.outcome) {
    AlertOutcome.STARTING -> stringResource(R.string.history_o_starting)
    AlertOutcome.PLAYED -> if (r.stoppedByUser) stringResource(R.string.history_o_stopped, f.duration(r.playedMs / 1000.0))
        else stringResource(R.string.history_o_played, f.duration(r.playedMs / 1000.0))
    AlertOutcome.PLAYED_FALLBACK -> stringResource(R.string.history_o_fallback, f.duration(r.playedMs / 1000.0))
    AlertOutcome.BACKUP_SOUND -> stringResource(R.string.history_o_backup)
    AlertOutcome.FAILED -> stringResource(R.string.history_o_failed)
    AlertOutcome.NOTIFICATION -> stringResource(R.string.history_o_notification)
    AlertOutcome.SILENT -> stringResource(R.string.history_o_silent)
    AlertOutcome.QUIET_BY_CHOICE -> stringResource(R.string.history_o_quiet)
    AlertOutcome.DND_BLOCKS_ALARMS -> stringResource(R.string.history_o_dnd)
    AlertOutcome.IN_CALL -> stringResource(R.string.history_o_call)
    AlertOutcome.NO_NOTIFICATIONS -> stringResource(R.string.history_o_no_notifications)
    AlertOutcome.LATE -> stringResource(R.string.history_o_late)
    AlertOutcome.NO_SETTINGS -> stringResource(R.string.history_o_no_settings)
}

@Composable
private fun HistoryRow(r: AlertRecord, f: Formatter, zone: ZoneId, fixable: Boolean = false) {
    val c = NoorTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(outcomeColor(r.outcome)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val due = Instant.ofEpochMilli(r.dueAtMs)
            val day = due.atZone(zone).toLocalDate()
            val name = r.prayer?.let { f.prayerName(it, day) } ?: "—"
            Text(if (r.test) "$name · ${stringResource(R.string.history_test_tag)}" else name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(outcomeText(r, f), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            val late = (r.delayMs / 60_000.0).roundToInt()
            val timing = if (late <= 0) stringResource(R.string.history_on_time) else pluralStringResource(R.plurals.history_late, late, f.number(late))
            // (Second review: each fact whole on its line, the line breaking before a fact, never after a dot; the time
            // with its AM or PM in one piece.)
            val extras = buildList {
                add(if (day == LocalDate.now(zone)) stringResource(R.string.history_today) else f.gregorianDate(day)); add(f.timeAt(due, zone).together()); add(timing)
                if (!r.exactAlarms) add(stringResource(R.string.history_exact_off))
                if (r.alarmVolumePercent == 0) add(stringResource(R.string.history_volume_zero))
            }
            FactsLine(extras, com.noor.app.ui.settings.FootnoteStyle, c.textMuted)
            // What went wrong, with its fix (0.15): a way to the one setting that caused it.
            val fix: Pair<Int, () -> Unit>? = if (!fixable) null else when {
                r.outcome == AlertOutcome.DND_BLOCKS_ALARMS -> R.string.history_fix_dnd to {
                    context.open(android.content.Intent("android.settings.ZEN_MODE_SETTINGS"))
                }
                r.outcome == AlertOutcome.NO_NOTIFICATIONS -> R.string.adhan_action_notifications to { context.open(appNotificationSettings(context)) }
                !r.exactAlarms && android.os.Build.VERSION.SDK_INT >= 31 -> R.string.adhan_action_exact to {
                    context.open(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)))
                }
                r.alarmVolumePercent == 0 -> R.string.adhan_action_volume to { context.open(android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
                else -> null
            }
            // (Second review: the app's second kind of button, small, as the Fix sheet's; it was a line of gold text.)
            if (fix != null) com.noor.app.ui.components.NoorButton(stringResource(fix.first), fix.second, Modifier.padding(top = 4.dp),
                kind = com.noor.app.ui.components.ButtonKind.Tonal, compact = true)
        }
    }
}
