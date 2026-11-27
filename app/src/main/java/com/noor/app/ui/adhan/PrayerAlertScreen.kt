package com.noor.app.ui.adhan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSound
import com.noor.alerts.SoundEntry
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.IconTile
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertSettings

/** A preview never plays on after its page is left or the app goes to the background. */
@Composable
internal fun StopPreviewOnLeave(vm: AdhanViewModel) {
    LifecycleStartEffect(vm) { onStopOrDispose { vm.stopPreview() } }
}

/**
 * Everything about one prayer's alert, on one calm page (0.26; the owner, 27 November 2026: "I should be able to
 * control well, if I want it to vibrate, if I want it to start gradually ... the specific volume for each prayer time
 * Adhan, what days it works, what days it doesn't"). Top to bottom: the prayer as the home screen draws it, with its
 * switch and today's time; what it plays (one row, opening the sound picker), its volume (heard as it is set), Start
 * softly, Vibrate and Stop after; its days; and a real test. Until 0.25 the page opened on a list of six "types".
 */
@Composable
fun PrayerAlertScreen(
    contentPadding: PaddingValues, prayer: Prayer, onBack: () -> Unit, onOpenMySounds: () -> Unit, vm: AdhanViewModel = viewModel(),
    onOpenLibrary: () -> Unit = {},
    /** The sound picker for this prayer (the route of the voices page, which 0.26 made the picker of everything it can play). */
    onOpenVoice: () -> Unit = {},
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val alerts = s.alerts
    val a = alerts.alert(prayer)
    val custom by vm.custom.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val rings by vm.rings.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val snackbar = remember { SnackbarHostState() }
    var stopDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    StopPreviewOnLeave(vm)
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val notifPermission = com.noor.app.ui.components.rememberNotificationRequest { vm.refresh() }

    NoorPage(f.prayerName(prayer), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        // All alerts are off: said here, with the switch for it here (0.14: the page sent one to the Adhan tab to find
        // it, which from the home screen's rows is a long way round).
        if (!alerts.enabled) SettingsSection("") {
            WarningRow(stringResource(R.string.adhan_master_off_here), stringResource(R.string.adhan_turn_on), {
                vm.setEnabled(true)
                if (ready.canRequestNotifications && alerts.device.includes(AlertDevice.PHONE)) notifPermission()
            })
        }
        // (0.15: the switch says what will happen: off while all alerts are off. Turned on then, it turns the alerts on
        // for this prayer alone, as on the Adhan tab.)
        val on = alerts.enabled && a.enabled
        PrayerHead(prayer, stringResource(R.string.adhan_alert_for, f.prayerName(prayer)),
            rings.today[prayer]?.let { stringResource(R.string.a026_today_at, f.timeAt(it, rings.zone).together()) }, on) { v ->
            if (v && !alerts.enabled) {
                vm.enableOnly(prayer)
                if (ready.canRequestNotifications && alerts.device.includes(AlertDevice.PHONE)) notifPermission()
            } else vm.setPrayer(prayer) { it.copy(enabled = v) }
        }

        // 0.25: everything under the switch is there whether it is on or off, so the page keeps its shape; while it is
        // off (this prayer's alert, or all alerts) it is shown at 38% and takes no touch.
        OffWhen(!on) { Column(verticalArrangement = Arrangement.spacedBy(com.noor.app.ui.components.Page.GroupGap)) {
            SettingsSection("", footer = if (prayer == Prayer.SHURUQ) stringResource(R.string.adhan_sunrise_note) else null) {
                SoundSettings(prayer, alerts, vm, custom, f, ar, onOpenSound = onOpenVoice, stopAfter = stopLabel(a.maxSeconds, f) to { stopDialog = true })
            }
            // (0.17: alarms before and after a prayer are on the Adhan tab, under Alarms.)
            SettingsSection(stringResource(R.string.alarm_repeat), footer = daysLabel(a.days, f)) {
                DaysRow(a.days, f) { d -> if (d != 0) vm.setDays(prayer, d) }
            }
            NoorButton(stringResource(R.string.adhan_test), { vm.test(prayer) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
        } }
    }

    if (stopDialog) {
        ChoiceDialog(stringResource(R.string.adhan_stop_after), listOf(0, 30, 60, 120, 180).map { it to stopLabel(it, f) }, a.maxSeconds,
            onSelect = { sec -> vm.setPrayer(prayer) { it.copy(maxSeconds = sec) } }, onDismiss = { stopDialog = false })
    }
}

/**
 * The prayer at the head of its page (0.26): its sky as on the home screen, "Alert at Fajr" and today's time, and the
 * switch; the whole card is the switch.
 */
@Composable
private fun PrayerHead(p: Prayer, title: String, sub: String?, on: Boolean, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 24.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(on, role = Role.Switch, onValueChange = com.noor.app.ui.components.withTick(onToggle))
                .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.noor.app.ui.today.SkyDisc(p, 46.dp, passed = !on)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = c.textPrimary)
                if (sub != null) Text(sub, style = FootnoteStyle, color = c.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            NoorSwitch(on, null)
        }
    }
}

/**
 * What plays and how (0.26), for one prayer ([target]) or for every prayer at once (null), the same rows in both places
 * so that one is learnt from the other: the sound (opening the picker), the volume, Start softly, Vibrate, and for a
 * prayer Stop after ([stopAfter]: its label and what opens its choice). For every prayer, where they differ the row
 * says so ("Mixed", "On for some prayers"), and a change sets them all. A prayer that plays nothing (a notification)
 * has no volume, rise or vibration of its own (its notification follows the phone's), so those rows are not shown.
 */
@Composable
internal fun SoundSettings(
    target: Prayer?, a: AlertSettings, vm: AdhanViewModel, custom: List<CustomSound>, f: Formatter, ar: Boolean,
    onOpenSound: () -> Unit, stopAfter: Pair<String, () -> Unit>? = null,
) {
    val lib = vm.library
    val plays = if (target != null) playsOf(target, a.alert(target), lib, custom) else sharedPlays(a, lib, custom)
    val (icon, tint) = playsSign(plays)
    SettingsItem(icon, stringResource(R.string.adhan_sound_section), null, onClick = onOpenSound, tint = tint,
        detail = plays?.let { playsFacts(it, lib, custom, ar).joinToString(" · ") } ?: stringResource(R.string.voice_mixed))
    val audible = (target?.let(::listOf) ?: Prayer.entries).filter { a.alert(it).mode.playsAudio }
    if (audible.isEmpty()) return
    // (For every prayer: the prayers that will sound; when none is on, those that would.)
    val counted = audible.filter { a.enabled && a.alert(it).enabled }.ifEmpty { audible }
    val some = stringResource(R.string.a026_some_prayers)

    SettingsDivider()
    val volumes = counted.map { a.alert(it).volumePercent }
    VolumeLine(volumes.average().toFloat(), volumes.distinct().size > 1, volumes, stringResource(R.string.adhan_volume), f) { vm.setVolume(target, it) }

    SettingsDivider()
    val rises = counted.map { a.alert(it).gradualSeconds }
    val soft = rises.all { it > 0 }
    SettingsSwitch(Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.a026_start_softly),
        when {
            soft -> stringResource(R.string.a026_soft_on, f.number(rises.max()))
            rises.any { it > 0 } -> some
            else -> stringResource(R.string.a026_soft_off)
        }, soft, tint = Tints.Purple) { vm.setSoftStart(target, it) }

    SettingsDivider()
    val vibrate = counted.map { a.alert(it).vibrate }
    SettingsSwitch(Icons.Rounded.Vibration, stringResource(R.string.adhan_vibrate), if (vibrate.distinct().size > 1) some else null, vibrate.all { it },
        tint = Tints.Indigo) { vm.setVibrate(target, it) }

    if (stopAfter != null) {
        SettingsDivider()
        SettingsItem(Icons.Rounded.Timer, stringResource(R.string.adhan_stop_after), null, onClick = stopAfter.second, detail = stopAfter.first)
    }
}

/** The sign of what plays: the mosque for the Adhan (and for prayers that differ), a bell for a sound or a notice. */
internal fun playsSign(p: Plays?): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> = when (p) {
    null, is Plays.Adhan -> Icons.Rounded.Mosque to Tints.Gold
    is Plays.Effect -> Icons.Rounded.NotificationsActive to Tints.Orange
    is Plays.Own -> Icons.Rounded.AudioFile to Tints.Purple
    Plays.Notification -> Icons.Rounded.NotificationsNone to Tints.Red
    Plays.Silent -> Icons.Rounded.NotificationsOff to Tints.Gray
}

/**
 * A volume as one line (0.26): the sign, the slider, the figure, or "Mixed" while the prayers it sets differ (moving
 * it sets them all). When the finger lets go, [onSet] gets the percent (and the view model plays a few seconds of the
 * sound at it, so the level is heard as it is chosen).
 */
@Composable
internal fun VolumeLine(value: Float, mixed: Boolean, key: Any?, label: String, f: Formatter, onSet: (Int) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    var vol by remember(key) { mutableFloatStateOf(value) }
    var moved by remember(key) { mutableStateOf(false) }
    // (0.26: named, with its figure, over a slider as wide as the row; 0.25's slider stood alone between a sign and a
    // figure, and the owner did not see what it set: "the master volume doesn't really make sense".)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp)) { IconTile(Icons.AutoMirrored.Rounded.VolumeUp, Tints.Pink) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(if (mixed && !moved) stringResource(R.string.voice_mixed) else f.number(vol.toInt()) + if (f.arabic) "٪" else "%",
                    style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
            }
            NoorSlider(vol, { v ->
                if ((v / 10).toInt() != (vol / 10).toInt()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vol = v; moved = true
            }, 5f..100f, label, Modifier.fillMaxWidth(), onValueChangeFinished = { onSet(vol.toInt()) })
        }
    }
}

@Composable
internal fun stopLabel(seconds: Int, f: Formatter): String = when {
    seconds == 0 -> stringResource(R.string.adhan_stop_full)
    seconds < 60 -> stringResource(R.string.adhan_stop_seconds, f.number(seconds))
    else -> stringResource(R.string.adhan_stop_minutes, f.number(seconds / 60))
}

@Composable
internal fun adhanDetail(e: SoundEntry, short: Boolean, f: Formatter, ar: Boolean): String {
    val len = if (short) e.shortSeconds ?: 0.0 else e.seconds
    val parts = listOfNotNull(e.place(ar)?.let(f::digits), f.duration(len), if (!e.hasFullRecording) stringResource(R.string.adhan_opening_only) else null)
    return parts.joinToString(" · ")
}

/** "15 min", or "Off". */
@Composable
internal fun minutesLabel(m: Int, f: Formatter): String = if (m <= 0) stringResource(R.string.adhan_off) else stringResource(R.string.minutes_short, f.number(m))

/**
 * The days of the week a prayer alerts on (0.16): seven round toggles, in the week's own order for the language,
 * lit when on. At least one stays on (no days at all is the prayer's switch, above). 0.25: a day that is on is a ring
 * of the accent round a tinted disc with its letters in the accent; seven solid gold discs were the loudest thing on
 * the page, louder than its one button.
 */
@Composable
internal fun DaysRow(days: Int, f: Formatter, onChange: (Int) -> Unit) {
    val c = NoorTheme.colors
    val first = java.time.temporal.WeekFields.of(f.locale).firstDayOfWeek
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (k in 0 until 7) {
            val d = first.plus(k.toLong())
            val bit = 1 shl (d.value - 1)
            val on = days and bit != 0
            Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (on) c.gold.copy(alpha = if (c.isDark) 0.20f else 0.16f) else c.glassRaised)
                    .then(if (on) Modifier.border(1.25.dp, c.gold.copy(alpha = 0.85f), androidx.compose.foundation.shape.CircleShape) else Modifier)
                    .toggleable(on, role = Role.Checkbox, onValueChange = com.noor.app.ui.components.withTick<Boolean> { onChange(if (it) days or bit else days and bit.inv()) })
                    .semantics { contentDescription = d.getDisplayName(java.time.format.TextStyle.FULL, f.locale) },
                contentAlignment = Alignment.Center,
            ) {
                // (Arabic: the day's one-letter form, ح ن ث ر خ ج س; English: two letters.)
                Text(if (f.arabic) d.getDisplayName(java.time.format.TextStyle.NARROW, f.locale) else f.weekdayShort(d).take(2),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = if (f.arabic) 14.sp else 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) c.goldText else c.textSecondary, maxLines = 1)
            }
        }
    }
}
