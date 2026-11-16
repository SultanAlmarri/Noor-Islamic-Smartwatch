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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * What actually happened at each alert on this phone: played (and for how long), replaced, made quiet and why, late.
 * This is the evidence that the Adhan sounds on a real phone, and what the owner shares when it does not.
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
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }

    com.noor.app.ui.components.NoorPage(stringResource(R.string.history_title), contentPadding, onBack = onBack) {
        Footnote(stringResource(R.string.history_intro))

        Column {
            com.noor.app.ui.components.NoorButton(stringResource(R.string.history_test, f.number(10)), { vm.testNext() }, Modifier.fillMaxWidth())
            com.noor.app.ui.settings.SectionFooter(stringResource(R.string.history_test_hint))
        }

        if (history.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), padding = 18.dp, corner = 20.dp) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        } else {
            GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 20.dp) {
                Column {
                    history.forEachIndexed { i, r ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 40.dp), thickness = 0.5.dp, color = c.separator)
                        // (0.15: the newest record says how to fix what went wrong; older ones only tell.)
                        HistoryRow(r, f, zone, fixable = i == 0)
                    }
                }
            }
        }
        com.noor.app.ui.components.NoorButton(stringResource(R.string.history_share), {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Noor alert report")
                .putExtra(Intent.EXTRA_TEXT, vm.report())
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth(), kind = com.noor.app.ui.components.ButtonKind.Tonal)
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
            val whenText = (if (day == LocalDate.now(zone)) stringResource(R.string.history_today) else f.gregorianDate(day)) +
                " · " + f.timeAt(due, zone)
            val name = r.prayer?.let { f.prayerName(it, day) } ?: "—"
            Text(if (r.test) "$name · ${stringResource(R.string.history_test_tag)}" else name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(outcomeText(r, f), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            val late = (r.delayMs / 60_000.0).roundToInt()
            val timing = if (late <= 0) stringResource(R.string.history_on_time) else pluralStringResource(R.plurals.history_late, late, f.number(late))
            val extras = buildList {
                add(whenText); add(timing)
                if (!r.exactAlarms) add(stringResource(R.string.history_exact_off))
                if (r.alarmVolumePercent == 0) add(stringResource(R.string.history_volume_zero))
            }
            Text(extras.joinToString(" · "), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textMuted)
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
            if (fix != null) Text(stringResource(fix.first), style = MaterialTheme.typography.labelLarge, color = c.goldText,
                modifier = Modifier.padding(top = 4.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = fix.second))
        }
    }
}
