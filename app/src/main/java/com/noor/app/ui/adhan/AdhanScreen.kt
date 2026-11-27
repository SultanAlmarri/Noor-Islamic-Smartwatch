package com.noor.app.ui.adhan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.HeroCard
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.NoorSheet
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.withTick
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.IconTile
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SectionFooter
import com.noor.app.ui.settings.SectionHeader
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlert

/**
 * Something that stands between the user's choices and an alert that sounds, and the one action that fixes it.
 * [blocking]: it stops the alert altogether (not just makes it late): said on the hero card too, in the danger colour.
 */
internal class Issue(val text: String, val action: String?, val onAction: (() -> Unit)?, val icon: ImageVector = Icons.Rounded.WarningAmber, val blocking: Boolean = false)

/**
 * The Adhan tab (0.25), organised round the person, not round the app's problems and options. Two pages under one
 * switch, Prayers and Alarms. Prayers, top to bottom: the one switch for all prayer alerts, always under the same name,
 * with what happens next under it (or Off, or that it will not sound); one line with one button for whatever needs
 * fixing, and only then; the voice and the volume of every prayer; the six prayers, each with today's time and its
 * switch; then the test, the record of what happened, and what is set once (which device, how the sound behaves, the
 * library).
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
    /** 0.17: an alarm (0: a new one). */
    onOpenAlarm: (Int) -> Unit = {},
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val a = s.alerts
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val next by vm.next.collectAsStateWithLifecycle()
    val hasLocation by vm.hasLocation.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val rings by vm.rings.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val context = LocalContext.current


    // (Not twice on entry: the view model has just done it; once each time the page comes back.)
    LifecycleResumeEffect(Unit) { vm.onShown(); onPauseOrDispose { } }
    // (Messages from the page's actions, such as the test's "in 10 seconds", or that it could not be set.)
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show(); vm.consumeMessage() } }
    val notifPermission = com.noor.app.ui.components.rememberNotificationRequest { vm.refresh() }
    val phone = a.enabled && a.device.includes(AlertDevice.PHONE)
    val watch = a.enabled && a.device.includes(AlertDevice.WATCH)

    // 0.15.2: what is used often on the first page (the switch and what comes next, what needs fixing, the voice and
    // the prayers, the test and the history); what is set once and left (phone or watch, how the sound behaves, the
    // library) one level down (the owner: "the more used features should get the priority"). 0.25: that page is named
    // for what it holds, "Sound and devices" ("More settings" was the name of a drawer).
    var page by rememberSaveable { mutableStateOf(0) }
    /** 0.17.1: 0 the prayers' Adhan, 1 the alarms. */
    var section by rememberSaveable { mutableStateOf(0) }
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
    // (0.25: the page has the tab's name. It was "Adhan & alerts" under a tab "Adhan" with a first segment "Adhan".)
    if (shown == 0) NoorPage(stringResource(R.string.tab_adhan), contentPadding) {
        // What needs fixing, for each of the two pages its own: what would stop or delay the prayers' alerts, and what
        // would stop or delay an alarm (0.25: until 0.24 one list of everything stood, in full, on both).
        // (Without a place there are no times to alert at: that comes first, with the way to set one.)
        val noPlace = if (!hasLocation) listOf(Issue(stringResource(R.string.adhan_no_location), stringResource(R.string.set_location), onOpenLocation, blocking = true)) else emptyList()
        // (0.15: what stops an alert altogether comes first, before what only makes it late.)
        val adhanIssues = (noPlace + (if (phone) phoneIssues(ready, a, context, forAlarms = false) { notifPermission() } else emptyList()) +
            (if (watch) watches.mapNotNull { w -> watchIssue(w) { vm.openNoorOnWatch(w.node.id) } } else emptyList())).sortedByDescending { it.blocking }
        val alarmIssues = (noPlace + (if (a.alarms.any { it.enabled }) phoneIssues(ready, a, context, forAlarms = true) { notifPermission() } else emptyList()))
            .sortedByDescending { it.blocking }
        // 0.17.1: the Adhan and the alarms, each a page of its own under one switch (the owner: "like the clock app,
        // but specialised for prayer times").
        Segmented(listOf(0 to stringResource(R.string.adhan_prayers), 1 to stringResource(R.string.alarms_title)), section, { section = it })
        if (section == 1) {
            if (alarmIssues.isNotEmpty()) FixCard(alarmIssues, if (alarmIssues.any { it.blocking }) stringResource(R.string.alarms_fix_blocked) else null, f)
            AlarmsList(a, rings, f, onOpen = onOpenAlarm, onToggle = { id, on -> vm.setAlarmOn(id, on) })
        } else {
        // The one switch, always under the same name (0.25: switched on, the card read "NEXT ADHAN / Fajr 5:20 AM"
        // beside a switch with no name). Under it what happens next; or Off; or, in the danger colour, that it will not
        // sound (what to do about it is the line under the card).
        run {
            val n = next.takeIf { hasLocation && a.enabled && a.device != AlertDevice.WATCH }
            val blocked = a.enabled && adhanIssues.any { it.blocking }
            val status = when {
                !a.enabled -> stringResource(R.string.adhan_master_off)
                blocked -> stringResource(R.string.adhan_hero_blocked)
                a.device == AlertDevice.WATCH -> stringResource(R.string.adhan_on_watch_only)
                // (0.21: the time and its AM or PM kept together: on a 360 dp phone "PM" went down a line by itself.)
                // (Second review: the prayer and its time in one piece too, "Fajr 5:20 AM"; the string joins them with a
                // no-break space.)
                n != null -> stringResource(R.string.adhan_hero_next_at, f.prayerName(n.planned.prayer, n.planned.date).together(), f.timeAt(n.planned.at, n.zone).together())
                else -> stringResource(R.string.adhan_next_none)
            }
            val toggle: (Boolean) -> Unit = { on ->
                vm.setEnabled(on)
                // The phone needs the permission only when it alerts itself (the watch asks on the watch).
                if (on && ready.canRequestNotifications && a.device.includes(AlertDevice.PHONE)) notifPermission()
            }
            HeroCard(
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.NotificationsActive),
                title = stringResource(R.string.adhan_master), titleSize = 20.sp,
                subtitle = status, subtitleColor = if (blocked) NoorTheme.colors.danger else null,
                modifier = Modifier.clip(RoundedCornerShape(30.dp)).toggleable(a.enabled, role = Role.Switch, onValueChange = withTick(toggle)),
                // (0.18: the switch alone; the button under it that did the same was one thing too many.)
                trailing = { NoorSwitch(a.enabled, null) },
            )
        }

        if (adhanIssues.isNotEmpty()) FixCard(adhanIssues, null, f)

        // 0.26: the six prayers first, each as the home screen draws it (the owner: "use the icons you used on the home
        // screen ... it looks nice"): a small window onto its own sky, its name, today's time, what it will do in a word
        // or two, and its switch. The row opens the prayer. (0.15: listed also while the alerts are off, quiet and
        // switched off; a prayer's switch turned on then turns the alerts on for that prayer alone.)
        SettingsSection("") {
            Prayer.entries.forEachIndexed { i, p ->
                if (i > 0) RowDivider(66.dp)
                val x = a.alert(p)
                val on = a.enabled && x.enabled
                val facts = if (!on) listOf(stringResource(R.string.adhan_off))
                    else playsFacts(playsOf(p, x, vm.library, custom), vm.library, custom, ar) + listOfNotNull(daysLabel(x.days, f).takeIf { x.days != PrayerAlert.ALL_DAYS })
                PrayerRow(p, f.prayerName(p), rings.today[p]?.let { f.clockAt(it, rings.zone) }, facts, on = on, live = a.enabled,
                    onOpen = { onOpenPrayer(p) }, onToggle = { v ->
                        if (v && !a.enabled) {
                            vm.enableOnly(p)
                            if (ready.canRequestNotifications && a.device.includes(AlertDevice.PHONE)) notifPermission()
                        } else vm.setPrayer(p) { it.copy(enabled = v) }
                    })
            }
        }

        // 0.26: what every prayer plays and how, set at once (the owner: "control the audio of all the Adhans at the same
        // time, or if you want, individual audio levels for each Adhan"); each prayer's page can then make it its own.
        // Where the prayers differ the row says so ("Mixed", "On for some prayers"), and a change here sets them all.
        // With the alerts off it looks off.
        OffWhen(!a.enabled) {
            SettingsSection(stringResource(R.string.a026_every_prayer), footer = stringResource(R.string.a026_every_prayer_footer)) {
                SoundSettings(null, a, vm, custom, f, ar, onOpenSound = onOpenVoice)
            }
        }
        StopPreviewOnLeave(vm)

        SettingsSection("") {
            // 0.15: "will it really sound tonight?" answered where the question arises: a real alert in ten seconds,
            // through the whole path, with the next prayer's own sound; what came of it is the row under it.
            // (Not while the alerts are on the watch alone: the test would sound on the phone.)
            if (a.enabled && a.device != AlertDevice.WATCH) {
                SettingsItem(Icons.Rounded.PlayCircle, stringResource(R.string.history_test, f.number(10)), stringResource(R.string.history_test_hint), onClick = { vm.testNext() },
                    trailing = { })
                SettingsDivider()
            }
            // What really happened at each alert.
            SettingsItem(Icons.Rounded.History, stringResource(R.string.history_title), null, onClick = onOpenHistory)
            SettingsDivider()
            // 0.26: named for what it holds now that the rise is each prayer's: which device, and silent mode.
            // (Which device, as a line under the name: beside it the name broke over two lines.)
            SettingsItem(Icons.Rounded.Tune, stringResource(R.string.a026_devices),
                stringResource(when (a.device) { AlertDevice.PHONE -> R.string.adhan_device_phone; AlertDevice.WATCH -> R.string.adhan_device_watch; AlertDevice.BOTH -> R.string.adhan_device_both }),
                onClick = { page = 1 })
        }
    }
    } else NoorPage(stringResource(R.string.a026_devices), contentPadding, onBack = { page = 0 }) {
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

        // (0.26: the gradual rise left this page: it is each prayer's "Start softly", on the Adhan tab.)
        SettingsSection("", footer = stringResource(R.string.adhan_volume_note)) {
            SettingsSwitch(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.adhan_dnd),
                stringResource(if (!a.quietWhenSilent) R.string.adhan_dnd_on else R.string.adhan_dnd_off), !a.quietWhenSilent, onChange = vm::setPlayInSilentMode)
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
    }
    }
}

/**
 * What needs fixing, in one line with one button (0.25; until 0.24 a list of up to four paragraphs, each with its own
 * button, stood between the switch and everything else, on both pages). One thing: it is said, and Fix does it. Several:
 * how many ([headline] over it where the page has not said what is wrong), and Fix opens them in a sheet, the most
 * serious first, each with its own way out; a thing fixed leaves the list, and the line leaves with the last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixCard(issues: List<Issue>, headline: String?, f: Formatter) {
    val c = NoorTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val one = issues.singleOrNull()
    val count = pluralStringResource(R.plurals.adhan_fix_count, issues.size, f.number(issues.size))
    val fix = stringResource(R.string.adhan_fix)
    val act: () -> Unit = { if (one != null) one.onAction?.invoke() else open = true }
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 24.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = fix, onClick = withTick(act))
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(one?.icon ?: Icons.Rounded.WarningAmber, if (issues.any { it.blocking }) Tints.Red else Tints.Orange)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (one != null) Text(one.text, style = FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.textPrimary)
                else {
                    Text(headline ?: count, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = c.textPrimary)
                    if (headline != null) Text(count, style = FootnoteStyle, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(fix, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold), color = c.onGold, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.goldFill).padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
    // (The sheet, once open, stays until it is closed or nothing is left: also with one thing left of several.)
    if (open) NoorSheet({ open = false }, rememberModalBottomSheetState(skipPartiallyExpanded = true)) { FixList(issues, count) }
}

/** The things to fix, as the sheet lists them: each what is wrong in a sentence, and the one action that fixes it. */
@Composable
internal fun FixList(issues: List<Issue>, title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = NoorTheme.colors.textPrimary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 6.dp).semantics { heading() })
    Column(Modifier.padding(horizontal = 8.dp)) {
        issues.forEachIndexed { i, issue ->
            if (i > 0) SettingsDivider()
            WarningRow(issue.text, issue.action, issue.onAction, issue.icon, danger = issue.blocking)
        }
    }
    Spacer(Modifier.height(20.dp))
}

/**
 * What on this phone would stop or delay the prayers' alerts, or ([forAlarms]) the alarms. (The battery advice is not
 * here: it is not a fault.)
 */
@Composable
private fun phoneIssues(ready: Readiness, a: AlertSettings, context: Context, forAlarms: Boolean, onRequestNotifications: () -> Unit): List<Issue> = buildList {
    // (What makes a sound: an alarm that rings, not one that only posts a notification; a prayer that plays one.)
    val audible = if (forAlarms) a.alarms.any { it.enabled && it.sound != AlarmSound.NOTIFICATION }
        else a.enabled && Prayer.entries.any { a.alert(it).enabled && a.alert(it).mode.playsAudio }
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
    if (!ready.fullScreenAllowed && Build.VERSION.SDK_INT >= 34 && audible) {
        add(Issue(stringResource(if (forAlarms) R.string.alarm_warn_fullscreen else R.string.adhan_warn_fullscreen), stringResource(R.string.adhan_action_allow), {
            context.open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName)))
        }))
    }
    if (!ready.exactAlarms && Build.VERSION.SDK_INT >= 31) {
        add(Issue(stringResource(R.string.adhan_warn_exact), stringResource(R.string.adhan_action_exact), {
            context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
        }))
    }
    if (audible && ready.alarmVolume <= 0f) {
        add(Issue(stringResource(if (forAlarms) R.string.alarm_warn_volume else R.string.adhan_warn_volume), stringResource(R.string.adhan_action_volume),
            { context.open(Intent(Settings.ACTION_SOUND_SETTINGS)) }, icon = Icons.AutoMirrored.Rounded.VolumeUp, blocking = true))
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

/**
 * A volume as one line (0.25): the sign, the slider, the figure. [value] is where it stands; [key] what, when it
 * changes, sets the slider back to [value]. [onSet] gets the percent when the finger lets go.
 */
@Composable
internal fun VolumeRow(value: Float, key: Any?, label: String, f: Formatter, onSet: (Int) -> Unit) {
    val c = NoorTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var vol by remember(key) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.AutoMirrored.Rounded.VolumeUp)
        Spacer(Modifier.width(12.dp))
        NoorSlider(vol, { v -> if ((v / 10).toInt() != (vol / 10).toInt()) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); vol = v },
            5f..100f, label, Modifier.weight(1f), onValueChangeFinished = { onSet(vol.toInt()) })
        Spacer(Modifier.width(10.dp))
        // (0.21: on one line; with large text "80%" broke into "80" over "%".)
        Text(f.number(vol.toInt()) + if (f.arabic) "٪" else "%", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
            modifier = Modifier.widthIn(min = 46.dp), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
}

/** A hairline between rows, from [start] (where the rows' words begin) to the end. */
@Composable
internal fun RowDivider(start: androidx.compose.ui.unit.Dp) =
    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = NoorTheme.colors.separator)

/**
 * One prayer on the Adhan tab (0.26): the home screen's sign for it, a small window onto its own sky (the owner: "use
 * the icons you used on the home screen"; 0.25 had drawn coloured tiles); its name, and under it what it will do in a word or two ("Adhan · Makkah", "Birds", "Notification", "Off"), and its days when not every
 * day; today's time, as the home screen writes it; and its switch. Touching the row (all but the switch) opens the prayer; a chevron says so. Off, the sky is dim and
 * the words grey, so the prayers that will sound stand out.
 */
@Composable
private fun PrayerRow(p: Prayer, name: String, time: Pair<String, String?>?, facts: List<String>, on: Boolean, live: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    val ink = if (on) c.textPrimary else c.textSecondary
    // (With large text the time grows by a tenth at most, and the chevron gives its place to the name: "Maghrib 5:32 PM"
    // still on one line on a 360 dp phone at 1.3.)
    val grow = com.noor.app.ui.components.limitedGrowth(1.1f)
    val roomy = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp).clickable(role = Role.Button, onClick = withTick(onOpen)).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.noor.app.ui.today.SkyDisc(p, 38.dp, passed = !on)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // (The name and the time on one line, the time at its end; what it will do under them, with the whole
                // width: with the time beside it, "Adhan · Makkah" broke over two lines on some rows and not others.)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
                    // (The time as the home screen writes it: light figures, the AM or PM small beside them.)
                    if (time != null) ClockText(time, MaterialTheme.typography.titleLarge.merge(com.noor.app.ui.theme.Tabular).copy(fontSize = 19.sp * grow, fontWeight = FontWeight.Light, letterSpacing = (-0.2).sp),
                        ink, markerStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp * grow, letterSpacing = 0.sp))
                }
                FactsLine(facts, FootnoteStyle, if (on) c.textSecondary else c.textMuted)
            }
            // (Second review of 0.25: something must say that the row opens a page. With large text the name needs its room.)
            if (roomy) androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
        Box(Modifier.padding(start = 6.dp, end = 14.dp)) { NoorSwitch(on, onToggle) }
    }
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
