package com.noor.app.ui.adhan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.SoundLibrary
import com.noor.app.R
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.HeroCard
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.RecordVoiceOver
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SectionFooter
import com.noor.app.ui.settings.SectionHeader
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert

/** Something that stands between the user's choices and an alert that sounds, and the one action that fixes it. */
/** [blocking]: it stops the alert altogether (not just makes it late): said on the hero card too, in the danger colour. */
private class Issue(val text: String, val action: String?, val onAction: (() -> Unit)?, val icon: ImageVector = Icons.Rounded.WarningAmber, val blocking: Boolean = false)

/**
 * Adhan and alerts (0.14), top to bottom in the order of use: the one switch for all alerts and what will happen
 * next; whatever needs the user's attention, in a group of its own (and only then); the prayers, each with its switch
 * and its sound one tap away; the reminder; which device alerts; how the sound behaves; the library; and the record
 * of what happened.
 */
@Composable
fun AdhanScreen(
    contentPadding: PaddingValues,
    onOpenPrayer: (Prayer) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMySounds: () -> Unit,
    onOpenHistory: () -> Unit = {},
    vm: AdhanViewModel = viewModel(),
    onOpenLocation: () -> Unit = {},
    /** 0.16: the voices, for every prayer. */
    onOpenVoice: () -> Unit = {},
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val a = s.alerts
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val hasLocation by vm.hasLocation.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val context = LocalContext.current
    var gradualDialog by rememberSaveable { mutableStateOf(false) }
    var reminderDialog by rememberSaveable { mutableStateOf(false) }
    var afterAllDialog by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val phone = a.enabled && a.device.includes(AlertDevice.PHONE)
    val watch = a.enabled && a.device.includes(AlertDevice.WATCH)

    // 0.15.2: what is used often on the first page (the next Adhan and its switch, what needs fixing, the prayers,
    // the test); what is set once and left (the reminder, phone or watch, how the sound behaves, the library, the
    // history) one level down, on "Alert options" (the owner: "the more used features should get the priority").
    var page by rememberSaveable { mutableStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = page != 0) { page = 0 }
    val reselect = com.noor.app.LocalTabReselect.current
    var seenReselect by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(reselect) { if (reselect != 0 && reselect != seenReselect) page = 0; seenReselect = reselect }
    androidx.compose.animation.AnimatedContent(
        targetState = page, label = "adhanPage",
        transitionSpec = {
            val dir = if (targetState > initialState) androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start else androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End
            (slideIntoContainer(dir, androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Standard, easing = com.noor.app.ui.components.NoorMotion.Ease)) { it / 5 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Standard))) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Quick))
        },
    ) { shown ->
    if (shown == 0) NoorPage(stringResource(R.string.adhan_title), contentPadding) {
        // What needs attention, in a group of its own: there only when something does.
        // (Without a place there are no times to alert at: that comes first, with the way to set one. 0.14: the page
        // said so before, but offered no way there.)
        val noPlace = if (!hasLocation) listOf(Issue(stringResource(R.string.adhan_no_location), stringResource(R.string.set_location), onOpenLocation)) else emptyList()
        val issues = noPlace + (if (phone) phoneIssues(ready, a, context) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) } else emptyList()) +
            (if (watch) watches.mapNotNull { w -> watchIssue(w) { vm.openNoorOnWatch(w.node.id) } } else emptyList())
        // (0.15: what stops an alert altogether comes first, before what only makes it late.)
            .sortedByDescending { it.blocking }
        // 0.15: the page opens on what happens next, in big type: the next Adhan and its sound, and the one switch;
        // or, when something would stop it, that it will not sound.
        run {
            val n = next.takeIf { hasLocation && a.enabled && a.device != AlertDevice.WATCH }
            val blocked = a.enabled && issues.any { it.blocking }
            val status = when {
                !hasLocation -> stringResource(R.string.adhan_no_location)
                blocked -> stringResource(R.string.adhan_hero_blocked)
                !a.enabled -> stringResource(R.string.adhan_master_off)
                a.device == AlertDevice.WATCH -> stringResource(R.string.adhan_on_watch_only)
                n != null -> summary(a.alert(n.planned.prayer), n.planned.prayer, vm.library, custom, ar)
                else -> stringResource(R.string.adhan_next_none)
            }
            val toggle: (Boolean) -> Unit = { on ->
                vm.setEnabled(on)
                // The phone needs the permission only when it alerts itself (the watch asks on the watch).
                if (on && ready.canRequestNotifications && a.device.includes(AlertDevice.PHONE)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            HeroCard(
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.NotificationsActive),
                overline = if (n != null) stringResource(R.string.adhan_hero_next) else null,
                title = if (n != null) f.prayerName(n.planned.prayer, n.planned.date) + "  " + f.timeAt(n.planned.at, n.zone) else stringResource(R.string.adhan_master),
                subtitle = status, subtitleColor = if (blocked) NoorTheme.colors.danger else null,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(30.dp)).toggleable(a.enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = toggle),
                trailing = { com.noor.app.ui.settings.NoorSwitch(a.enabled, null) },
                // 0.15: while off, the one thing to do, as a button: not only a switch in a corner.
                below = if (!a.enabled && hasLocation) ({
                    com.noor.app.ui.components.NoorButton(stringResource(R.string.adhan_turn_on_all), { toggle(true) }, Modifier.fillMaxWidth())
                }) else null,
            )
        }

        if (issues.isNotEmpty()) SettingsSection(stringResource(R.string.adhan_attention)) {
            issues.forEachIndexed { i, issue ->
                if (i > 0) SettingsDivider()
                WarningRow(issue.text, issue.action, issue.onAction, issue.icon, danger = issue.blocking)
            }
        }

        // 0.15: the prayers are always there, so what would happen can be seen and set before anything is on. While
        // the alerts are off their rows are quiet and their switches off; turning one on turns the alerts on, for that
        // prayer alone (it is what was asked for).
        // 0.16: the voice, for every prayer at once (as on the owner's Al-Fajr clock: one dial, Makkah to Random).
        SettingsSection("") {
            val voices = Prayer.entries.filter { it != Prayer.FAJR && it != Prayer.SHURUQ && a.alert(it).mode in VOICE_MODES }.map { a.alert(it).soundId }.distinct()
            SettingsItem(Icons.Rounded.RecordVoiceOver, stringResource(R.string.adhan_voice), null, onClick = onOpenVoice,
                detail = if (voices.size == 1) voiceName(voices[0], vm, custom, ar) else if (voices.isEmpty()) null else stringResource(R.string.voice_mixed))
        }
        SettingsSection(stringResource(R.string.adhan_prayers), footer = stringResource(if (a.enabled) R.string.adhan_prayers_footer else R.string.adhan_prayers_footer_off)) {
            Prayer.entries.forEachIndexed { i, p ->
                if (i > 0) SettingsDivider()
                // (While off, what it would play, plainly as what it would: "Off · would play Adhan · ♪ Makkah".)
                val what = summary(a.alert(p), p, vm.library, custom, ar)
                PrayerAlertRow(p, a.alert(p), if (!a.enabled && a.alert(p).enabled) stringResource(R.string.adhan_off_would, what) else what, f, live = a.enabled,
                    onOpen = { onOpenPrayer(p) }, onToggle = { on ->
                        if (on && !a.enabled) {
                            vm.enableOnly(p)
                            if (ready.canRequestNotifications && a.device.includes(AlertDevice.PHONE)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.setPrayer(p) { it.copy(enabled = on) }
                    })
            }
        }

        SettingsSection("", footer = if (a.enabled) stringResource(R.string.adhan_test_footer) else null) {
            // 0.15: "will it really sound tonight?" answered where the question arises: a real alert in ten seconds,
            // through the whole path, with the next prayer's own sound (its result shows in the history).
            if (a.enabled) {
                SettingsItem(Icons.Rounded.PlayCircle, stringResource(R.string.history_test, f.number(10)), null, onClick = {
                    vm.testNext()
                    android.widget.Toast.makeText(context, context.getString(R.string.adhan_test_sent), android.widget.Toast.LENGTH_SHORT).show()
                })
                SettingsDivider()
            }
            SettingsItem(Icons.Rounded.Tune, stringResource(R.string.adhan_alert_options),
                listOf(stringResource(when (a.device) { AlertDevice.PHONE -> R.string.adhan_device_phone; AlertDevice.WATCH -> R.string.adhan_device_watch; AlertDevice.BOTH -> R.string.adhan_device_both }),
                    reminderLabel(a.reminderMinutes, f)).joinToString(" · "),
                onClick = { page = 1 })
        }
    } else NoorPage(stringResource(R.string.adhan_alert_options), contentPadding, onBack = { page = 0 }) {
        run {
            // 0.9.9: a reminder a few minutes before every prayer that alerts.
            SettingsSection("") {
                // 0.16: before and after every Adhan at once (each prayer can have its own, on its page).
                val before = Prayer.entries.filter { a.alert(it).enabled }.map { a.beforeOf(it) }.distinct()
                val after = Prayer.entries.filter { a.alert(it).enabled }.map { a.alert(it).afterMinutes }.distinct()
                SettingsItem(Icons.Rounded.AlarmAdd, stringResource(R.string.before_all), null, onClick = { reminderDialog = true },
                    detail = if (before.size <= 1) minutesLabel(before.firstOrNull() ?: 0, f) else stringResource(R.string.voice_mixed))
                SettingsDivider()
                SettingsItem(Icons.Rounded.Groups, stringResource(R.string.after_all), null, onClick = { afterAllDialog = true },
                    detail = if (after.size <= 1) minutesLabel(after.firstOrNull() ?: 0, f) else stringResource(R.string.voice_mixed))
            }

            Column {
                SectionHeader(stringResource(R.string.adhan_device_title))
                Segmented(
                    listOf(AlertDevice.PHONE to stringResource(R.string.adhan_device_phone), AlertDevice.WATCH to stringResource(R.string.adhan_device_watch),
                        AlertDevice.BOTH to stringResource(R.string.adhan_device_both)),
                    a.device, vm::setDevice,
                )
                SectionFooter(stringResource(when (a.device) {
                    AlertDevice.PHONE -> R.string.adhan_device_phone_desc
                    AlertDevice.WATCH -> R.string.adhan_device_watch_desc
                    AlertDevice.BOTH -> R.string.adhan_device_both_desc
                }))
                // Each watch that will alert, and how many alerts it has set (or that it has not said yet).
                if (watch) watches.filter { watchIssue(it) {} == null }.forEach { w ->
                    val r = w.report
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.Watch, null, tint = NoorTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (r?.notificationsAllowed == null) stringResource(R.string.adhan_watch_unknown, w.node.name)
                            else stringResource(R.string.adhan_watch_ready, w.node.name, f.number(r.alertsArmed ?: 0)),
                            style = FootnoteStyle, color = NoorTheme.colors.textSecondary)
                    }
                }
            }

            SettingsSection(stringResource(R.string.adhan_sound_section), footer = stringResource(R.string.adhan_volume_note)) {
                SettingsItem(Icons.Rounded.GraphicEq, stringResource(R.string.adhan_gradual),
                    if (a.gradualSeconds == 0) stringResource(R.string.adhan_gradual_off) else stringResource(R.string.adhan_gradual_value, f.number(a.gradualSeconds)),
                    onClick = { gradualDialog = true })
                SettingsDivider()
                SettingsSwitch(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.adhan_dnd),
                    stringResource(if (!a.quietWhenSilent) R.string.adhan_dnd_on else R.string.adhan_dnd_off), !a.quietWhenSilent, onChange = vm::setPlayInSilentMode)
            }
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

        // What really happened at each alert, and the one piece of advice that is not a fault of the set-up.
        SettingsSection("") {
            // 0.15: "will it really sound tonight?" answered where the question arises: a real alert in ten seconds,
            // through the whole path, with the next prayer's own sound (its result shows in the history below).
            val last = history.firstOrNull()
            SettingsItem(Icons.Rounded.History, stringResource(R.string.history_title),
                last?.let { r -> (r.prayer?.let { f.prayerName(it) } ?: "") + " · " + outcomeText(r, f) } ?: stringResource(R.string.history_row_empty),
                onClick = onOpenHistory)
            if (phone && ready.batteryRestricted) {
                SettingsDivider()
                WarningRow(stringResource(R.string.adhan_warn_battery), stringResource(R.string.adhan_action_open),
                    { context.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, icon = Icons.Rounded.BatterySaver)
            }
        }
    }
    }

    if (gradualDialog) {
        ChoiceDialog(stringResource(R.string.adhan_gradual),
            listOf(0, 5, 10, 20, 30, 60).map { it to if (it == 0) stringResource(R.string.adhan_gradual_off) else stringResource(R.string.adhan_gradual_value, f.number(it)) },
            a.gradualSeconds, onSelect = vm::setGradual, onDismiss = { gradualDialog = false })
    }
    if (reminderDialog) {
        ChoiceDialog(stringResource(R.string.before_all),
            com.noor.prayer.alerts.PrayerAlert.BEFORE_CHOICES.map { it to minutesLabel(it, f) },
            Prayer.entries.filter { a.alert(it).enabled }.map { a.beforeOf(it) }.distinct().singleOrNull() ?: -1,
            onSelect = vm::setBeforeAll, onDismiss = { reminderDialog = false })
    }
    if (afterAllDialog) {
        ChoiceDialog(stringResource(R.string.after_all),
            com.noor.prayer.alerts.PrayerAlert.AFTER_CHOICES.map { it to minutesLabel(it, f) },
            Prayer.entries.filter { a.alert(it).enabled }.map { a.alert(it).afterMinutes }.distinct().singleOrNull() ?: -1,
            onSelect = vm::setAfterAll, onDismiss = { afterAllDialog = false })
    }
}

@Composable
private fun reminderLabel(minutes: Int, f: Formatter): String =
    if (minutes == 0) stringResource(R.string.adhan_reminder_off) else pluralStringResource(R.plurals.adhan_reminder_value, minutes, f.number(minutes))

/** What on this phone would stop an alert, most serious first. (The battery advice is not here: it is not a fault.) */
@Composable
private fun phoneIssues(ready: Readiness, a: AlertSettings, context: Context, onRequestNotifications: () -> Unit): List<Issue> = buildList {
    val audible = Prayer.entries.any { a.alert(it).enabled && a.alert(it).mode.playsAudio }
    if (!ready.notificationsAllowed) {
        add(Issue(stringResource(R.string.adhan_warn_notifications), stringResource(R.string.adhan_action_notifications), {
            if (ready.canRequestNotifications) onRequestNotifications() else context.open(appNotificationSettings(context))
        }, blocking = true))
    } else if (ready.adhanChannelBlocked && audible) {
        add(Issue(stringResource(R.string.adhan_warn_channel), stringResource(R.string.adhan_action_channel), {
            context.open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, com.noor.alerts.AlertNotifications.CHANNEL_ADHAN))
        }, blocking = true))
    }
    if (!ready.exactAlarms && Build.VERSION.SDK_INT >= 31) {
        add(Issue(stringResource(R.string.adhan_warn_exact), stringResource(R.string.adhan_action_exact), {
            context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
        }))
    }
    if (audible && ready.alarmVolume <= 0f) {
        add(Issue(stringResource(R.string.adhan_warn_volume), stringResource(R.string.adhan_action_volume), { context.open(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            icon = Icons.AutoMirrored.Rounded.VolumeUp, blocking = true))
    }
}

/** What on a watch would stop its alerts; null when it is ready, or has not said yet. */
@Composable
private fun watchIssue(w: WatchAlertState, onOpen: () -> Unit): Issue? {
    val r = w.report
    return when {
        r?.notificationsAllowed == false -> Issue(stringResource(R.string.adhan_warn_watch_notifications, w.node.name), stringResource(R.string.adhan_action_open_watch), onOpen, blocking = true)
        r?.notificationsAllowed != null && r.exactAlarms == false -> Issue(stringResource(R.string.adhan_warn_watch_exact, w.node.name), stringResource(R.string.adhan_action_open_watch), onOpen)
        else -> null
    }
}

/** One prayer: the row opens its alert (type, sound, volume); the switch turns it on or off without leaving the page. */
@Composable
private fun PrayerAlertRow(p: Prayer, alert: PrayerAlert, summary: String, f: Formatter, live: Boolean = true, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    // (With the alerts off nothing alerts: the switch says so, and the row is quieter.)
    val on = live && alert.enabled
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onOpen).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (on) c.gold else c.textMuted
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = if (c.isDark) 0.15f else 0.13f)),
            contentAlignment = Alignment.Center) { PrayerIcon(p, 19.dp, tint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (on) 1f else 0.7f)) {
            Text(f.prayerName(p), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(summary, style = FootnoteStyle, color = c.textSecondary)
        }
        // 0.16: what else is set, as small signs: a reminder before, one after, some days only.
        val sign = c.textMuted
        if (on && alert.beforeMinutes > 0) androidx.compose.material3.Icon(Icons.Rounded.AlarmAdd, null, tint = sign, modifier = Modifier.padding(start = 6.dp).size(15.dp))
        if (on && alert.afterMinutes > 0) androidx.compose.material3.Icon(Icons.Rounded.Groups, null, tint = sign, modifier = Modifier.padding(start = 6.dp).size(15.dp))
        if (on && alert.days != com.noor.prayer.alerts.PrayerAlert.ALL_DAYS) androidx.compose.material3.Icon(Icons.Rounded.EventRepeat, null, tint = sign, modifier = Modifier.padding(start = 6.dp).size(15.dp))
        Spacer(Modifier.width(12.dp))
        NoorSwitch(on, onToggle)
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
    // (A note before the recording's name: most are named for where they were recorded, and "Adhan · Malmö" read as
    // if the times were Malmö's. 0.15.)
    return if (sound != null) "$mode · \u266A $sound" else mode
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
