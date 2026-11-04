package com.noor.app.ui.adhan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.SoundLibrary
import com.noor.app.R
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert

@Composable
fun AdhanScreen(
    contentPadding: PaddingValues,
    onOpenPrayer: (Prayer) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMySounds: () -> Unit,
    vm: AdhanViewModel = viewModel(),
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val a = s.alerts
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val hasLocation by vm.hasLocation.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val context = LocalContext.current
    var gradualDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.adhan_title), style = MaterialTheme.typography.headlineMedium, color = NoorTheme.colors.textPrimary)

        SettingsSection(stringResource(R.string.adhan_master)) {
            val status = when {
                !hasLocation -> stringResource(R.string.adhan_no_location)
                !a.enabled -> stringResource(R.string.adhan_master_off)
                a.device == AlertDevice.WATCH -> stringResource(R.string.adhan_on_watch_only)
                next != null -> next!!.let { n -> stringResource(R.string.adhan_next, f.prayerName(n.planned.prayer, n.planned.date), f.timeAt(n.planned.at, n.zone)) }
                else -> stringResource(R.string.adhan_next_none)
            }
            SettingsItem(Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_master), status, null) {
                NoorSwitch(a.enabled) { on ->
                    vm.setEnabled(on)
                    // The phone needs the permission only when it alerts itself (the watch asks on the watch).
                    if (on && ready.canRequestNotifications && a.device.includes(AlertDevice.PHONE)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (a.enabled && a.device.includes(AlertDevice.PHONE)) {
                PhoneWarnings(ready, a, context, onRequestNotifications = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
            if (a.enabled && a.device.includes(AlertDevice.WATCH)) {
                watches.forEach { w -> WatchStatusRow(w, f, onOpen = { vm.openNoorOnWatch(w.node.id) }) }
            }
        }

        if (a.enabled) {
            Column {
                SectionLabel(stringResource(R.string.adhan_device_title))
                Segmented(
                    listOf(AlertDevice.PHONE to stringResource(R.string.adhan_device_phone), AlertDevice.WATCH to stringResource(R.string.adhan_device_watch),
                        AlertDevice.BOTH to stringResource(R.string.adhan_device_both)),
                    a.device, vm::setDevice,
                )
                Spacer(Modifier.height(8.dp))
                Footnote(stringResource(when (a.device) {
                    AlertDevice.PHONE -> R.string.adhan_device_phone_desc
                    AlertDevice.WATCH -> R.string.adhan_device_watch_desc
                    AlertDevice.BOTH -> R.string.adhan_device_both_desc
                }))
            }

            SettingsSection(stringResource(R.string.adhan_prayers)) {
                Prayer.entries.forEachIndexed { i, p ->
                    if (i > 0) SettingsDivider()
                    PrayerAlertRow(p, a.alert(p), summary(a.alert(p), p, vm.library, custom, ar), f,
                        onOpen = { onOpenPrayer(p) }, onToggle = { on -> vm.setPrayer(p) { it.copy(enabled = on) } })
                }
            }

            SettingsSection(stringResource(R.string.adhan_sound_section)) {
                SettingsItem(Icons.Rounded.GraphicEq, stringResource(R.string.adhan_gradual),
                    if (a.gradualSeconds == 0) stringResource(R.string.adhan_gradual_off) else stringResource(R.string.adhan_gradual_value, f.number(a.gradualSeconds)),
                    onClick = { gradualDialog = true })
                SettingsDivider()
                SettingsItem(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.adhan_dnd),
                    stringResource(if (a.playInDnd) R.string.adhan_dnd_on else R.string.adhan_dnd_off), null) {
                    NoorSwitch(a.playInDnd, vm::setPlayInDnd)
                }
            }
            Footnote(stringResource(R.string.adhan_volume_note))
        }

        SettingsSection(stringResource(R.string.adhan_library_section)) {
            val lib = vm.library
            SettingsItem(Icons.Rounded.LibraryMusic, stringResource(R.string.adhan_library),
                stringResource(R.string.adhan_library_desc, f.number(lib.adhans.size), f.number(lib.sounds.size)), onClick = onOpenLibrary)
            SettingsDivider()
            SettingsItem(Icons.Rounded.AudioFile, stringResource(R.string.adhan_my_sounds),
                if (custom.isEmpty()) stringResource(R.string.adhan_my_sounds_desc_empty) else stringResource(R.string.adhan_my_sounds_desc, f.number(custom.size)),
                onClick = onOpenMySounds)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (gradualDialog) {
        ChoiceDialog(stringResource(R.string.adhan_gradual),
            listOf(0, 5, 10, 20, 30, 60).map { it to if (it == 0) stringResource(R.string.adhan_gradual_off) else stringResource(R.string.adhan_gradual_value, f.number(it)) },
            a.gradualSeconds, onSelect = vm::setGradual, onDismiss = { gradualDialog = false })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = NoorTheme.colors.gold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
}

@Composable
private fun PhoneWarnings(ready: Readiness, a: AlertSettings, context: Context, onRequestNotifications: () -> Unit) {
    val audible = Prayer.entries.any { a.alert(it).enabled && a.alert(it).mode.playsAudio }
    if (!ready.notificationsAllowed) {
        WarningRow(stringResource(R.string.adhan_warn_notifications), stringResource(R.string.adhan_action_allow), {
            if (ready.canRequestNotifications) onRequestNotifications() else context.open(appNotificationSettings(context))
        })
    } else if (ready.adhanChannelBlocked && audible) {
        WarningRow(stringResource(R.string.adhan_warn_channel), stringResource(R.string.adhan_action_open), {
            context.open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, com.noor.alerts.AlertNotifications.CHANNEL_ADHAN))
        })
    }
    if (!ready.exactAlarms && Build.VERSION.SDK_INT >= 31) {
        WarningRow(stringResource(R.string.adhan_warn_exact), stringResource(R.string.adhan_action_allow), {
            context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
        })
    }
    if (audible && ready.alarmVolume <= 0f) {
        WarningRow(stringResource(R.string.adhan_warn_volume), stringResource(R.string.adhan_action_open), { context.open(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            icon = Icons.AutoMirrored.Rounded.VolumeUp)
    }
    if (ready.batteryRestricted) {
        WarningRow(stringResource(R.string.adhan_warn_battery), stringResource(R.string.adhan_action_open),
            { context.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, icon = Icons.Rounded.Info)
    }
}

@Composable
private fun WatchStatusRow(w: WatchAlertState, f: Formatter, onOpen: () -> Unit) {
    val r = w.report
    when {
        r?.notificationsAllowed == null -> WarningRow(stringResource(R.string.adhan_watch_unknown, w.node.name), null, null, icon = Icons.Rounded.Watch)
        r.notificationsAllowed == false -> WarningRow(stringResource(R.string.adhan_warn_watch_notifications, w.node.name), stringResource(R.string.adhan_action_open_watch), onOpen)
        r.exactAlarms == false -> WarningRow(stringResource(R.string.adhan_warn_watch_exact, w.node.name), stringResource(R.string.adhan_action_open_watch), onOpen)
        else -> WarningRow(stringResource(R.string.adhan_watch_ready, w.node.name, f.number(r.alertsArmed ?: 0)), null, null, icon = Icons.Rounded.Watch)
    }
}

@Composable
private fun PrayerAlertRow(p: Prayer, alert: PrayerAlert, summary: String, f: Formatter, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrayerIcon(p, 22.dp, if (alert.enabled) c.gold else c.textMuted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (alert.enabled) 1f else 0.7f)) {
            Text(f.prayerName(p), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        NoorSwitch(alert.enabled, onToggle)
    }
}

/** "Adhan · Makkah", "Sound · Soft chime", "Off"… */
@Composable
internal fun summary(a: PrayerAlert, p: Prayer, lib: SoundLibrary, custom: List<com.noor.alerts.CustomSound>, ar: Boolean): String {
    if (!a.enabled) return stringResource(R.string.adhan_off)
    val mode = stringResource(modeLabel(a.mode))
    val sound = when (a.mode) {
        AlertMode.ADHAN, AlertMode.ADHAN_SHORT, AlertMode.SOUND -> lib.byId(a.soundId)?.title(ar)
        AlertMode.CUSTOM -> custom.firstOrNull { it.id == a.soundId }?.name
        else -> null
    }
    return if (sound != null) "$mode · $sound" else mode
}

internal fun modeLabel(m: AlertMode) = when (m) {
    AlertMode.ADHAN -> R.string.adhan_mode_adhan
    AlertMode.ADHAN_SHORT -> R.string.adhan_mode_short
    AlertMode.SOUND -> R.string.adhan_mode_sound
    AlertMode.CUSTOM -> R.string.adhan_mode_custom
    AlertMode.NOTIFICATION -> R.string.adhan_mode_notification
    AlertMode.SILENT -> R.string.adhan_mode_silent
}

internal fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

internal fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        // Some devices lack a specific settings page; fall back to the app's own settings page.
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

