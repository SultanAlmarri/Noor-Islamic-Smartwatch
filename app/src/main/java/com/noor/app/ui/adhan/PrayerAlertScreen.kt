package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSounds
import com.noor.alerts.SoundEntry
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
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
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box

/** A preview never plays on after its page is left or the app goes to the background. */
@Composable
internal fun StopPreviewOnLeave(vm: AdhanViewModel) {
    LifecycleStartEffect(vm) { onStopOrDispose { vm.stopPreview() } }
}

/**
 * Everything about one prayer's alert (0.14): on or off; what kind, as six tiles one can see at once; the sound, each
 * with a preview; volume, vibration and length; and a real test.
 */
@Composable
fun PrayerAlertScreen(
    contentPadding: PaddingValues, prayer: Prayer, onBack: () -> Unit, onOpenMySounds: () -> Unit, vm: AdhanViewModel = viewModel(),
    onOpenLibrary: () -> Unit = {},
    /** 0.16: the voices, for this prayer. */
    onOpenVoice: () -> Unit = {},
) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val alerts = s.alerts
    val a = alerts.alert(prayer)
    val preview by vm.preview.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    val lib = vm.library
    val snackbar = remember { SnackbarHostState() }
    var stopDialog by rememberSaveable { mutableStateOf(false) }
    val previewLabel = stringResource(R.string.adhan_preview)

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    StopPreviewOnLeave(vm)
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSound(uri, forPrayer = prayer) }

    NoorPage(f.prayerName(prayer), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        // All alerts are off: said here, with the switch for it here (0.14: the page sent one to the Adhan tab to find
        // it, which from the home screen's rows is a long way round).
        if (!alerts.enabled) SettingsSection("") {
            WarningRow(stringResource(R.string.adhan_master_off_here), stringResource(R.string.adhan_turn_on), {
                vm.setEnabled(true)
                if (ready.canRequestNotifications && alerts.device.includes(AlertDevice.PHONE)) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        SettingsSection("", footer = if (prayer == Prayer.SHURUQ) stringResource(R.string.adhan_sunrise_note) else null) {
            // (0.15: the switch says what will happen: off while all alerts are off. Turned on then, it turns the
            // alerts on for this prayer alone, as on the Adhan tab.)
            SettingsSwitch(Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_alert_for, f.prayerName(prayer)), null, alerts.enabled && a.enabled) { on ->
                if (on && !alerts.enabled) {
                    vm.enableOnly(prayer)
                    if (ready.canRequestNotifications && alerts.device.includes(AlertDevice.PHONE)) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else vm.setPrayer(prayer) { it.copy(enabled = on) }
            }
        }

        if (a.enabled) {
            // 0.16: what kind of alert (no tones any more); then the voice, as one row; then the days.
            // 0.17: four kinds for a prayer, two by two: the Adhan, its opening, a notification, silently. The user's own
            // recording is one of the voices (the voice row), so it is no longer a kind of its own here; one chosen
            // there shows as the Adhan.
            Column {
                SectionHeader(stringResource(R.string.adhan_type))
                val modes = if (prayer == Prayer.SHURUQ) listOf(AlertMode.CUSTOM, AlertMode.NOTIFICATION, AlertMode.SILENT)
                else listOf(AlertMode.ADHAN, AlertMode.ADHAN_SHORT, AlertMode.NOTIFICATION, AlertMode.SILENT)
                ModeTiles(modes, if (prayer != Prayer.SHURUQ && a.mode == AlertMode.CUSTOM) AlertMode.ADHAN else a.mode) { vm.setMode(prayer, it) }
            }

            if (a.mode in VOICE_MODES) {
                val chosen = lib.byId(a.soundId)
                val fajrWarn = if (prayer == Prayer.FAJR && chosen != null && !chosen.fajr) stringResource(R.string.adhan_fajr_warn) else null
                SettingsSection("", footer = fajrWarn) {
                    SettingsItem(Icons.Rounded.RecordVoiceOver, stringResource(R.string.adhan_voice), null, onClick = onOpenVoice,
                        detail = voiceName(a.soundId, vm, custom, ar) ?: stringResource(R.string.voice_add))
                }
            }

            // (0.17: alarms before and after a prayer are on the Adhan tab, under Alarms.)
            SettingsSection(stringResource(R.string.alarm_repeat), footer = daysLabel(a.days, f)) {
                DaysRow(a.days, f) { d -> if (d != 0) vm.setDays(prayer, d) }
            }

            if (a.mode.playsAudio) {
                SettingsSection(stringResource(R.string.adhan_options)) {
                    var vol by remember(a.volumePercent) { mutableFloatStateOf(a.volumePercent.toFloat()) }
                    val volumeLabel = stringResource(R.string.adhan_volume)
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.noor.app.ui.settings.IconTile(Icons.AutoMirrored.Rounded.VolumeUp)
                            Spacer(Modifier.width(14.dp))
                            Text(volumeLabel, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(f.number(vol.toInt()) + if (ar) "٪" else "%", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        }
                        Slider(value = vol, onValueChange = { vol = it }, valueRange = 5f..100f,
                            onValueChangeFinished = { vm.setPrayer(prayer) { it.copy(volumePercent = vol.toInt()) } },
                            modifier = Modifier.semantics { contentDescription = volumeLabel },
                            colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.textMuted.copy(alpha = 0.25f)))
                    }
                    SettingsDivider()
                    SettingsSwitch(Icons.Rounded.Vibration, stringResource(R.string.adhan_vibrate), null, a.vibrate) { on -> vm.setPrayer(prayer) { it.copy(vibrate = on) } }
                    SettingsDivider()
                    SettingsItem(Icons.Rounded.Timer, stringResource(R.string.adhan_stop_after), null, onClick = { stopDialog = true }, detail = stopLabel(a.maxSeconds, f))
                }
            }

            Column {
                NoorButton(stringResource(R.string.adhan_test), { vm.test(prayer) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
                SectionFooter(stringResource(R.string.adhan_test_desc))
            }
        }
    }

    if (stopDialog) {
        ChoiceDialog(stringResource(R.string.adhan_stop_after), listOf(0, 30, 60, 120, 180).map { it to stopLabel(it, f) }, a.maxSeconds,
            onSelect = { sec -> vm.setPrayer(prayer) { it.copy(maxSeconds = sec) } }, onDismiss = { stopDialog = false })
    }
}

private fun modeIcon(m: AlertMode): ImageVector = when (m) {
    AlertMode.ADHAN -> Icons.Rounded.Mosque
    AlertMode.ADHAN_SHORT -> Icons.Rounded.Timelapse
    AlertMode.SOUND -> Icons.Rounded.MusicNote
    AlertMode.CUSTOM -> Icons.Rounded.AudioFile
    AlertMode.NOTIFICATION -> Icons.Rounded.NotificationsNone
    AlertMode.SILENT -> Icons.Rounded.NotificationsOff
}

/**
 * The kinds of alert as tiles, three to a row, all visible at once (0.14; the chips before wrapped into a ragged
 * second line). The chosen one wears the accent; choosing answers with a tick.
 */
@Composable
private fun ModeTiles(modes: List<AlertMode>, selected: AlertMode, onSelect: (AlertMode) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(if (modes.size == 4) 2 else 3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val on = m == selected
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).heightIn(min = 76.dp).fillMaxHeight().clip(shape)
                            .background(if (on) c.gold.copy(alpha = if (c.isDark) 0.16f else 0.13f) else c.glass)
                            .border(if (on) 1.5.dp else 0.75.dp, if (on) c.gold else c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape)
                            .clickable(role = Role.RadioButton) { if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(m) }
                            .semantics { this.selected = on }.padding(horizontal = 6.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(modeIcon(m), null, tint = if (on) c.gold else c.textSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(modeLabel(m)), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp, lineHeight = 18.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (on) c.goldText else c.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
                        // 0.15: what each kind is, in three words, so "Sound", "Notification" and "Silent" need not be tried.
                        Text(stringResource(modeHint(m)), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, letterSpacing = 0.sp, lineHeight = 14.sp),
                            color = c.textSecondary, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** A kind of alert in three words (0.15). */
internal fun modeHint(m: AlertMode): Int = when (m) {
    AlertMode.ADHAN -> R.string.adhan_hint_adhan
    AlertMode.ADHAN_SHORT -> R.string.adhan_hint_short
    AlertMode.SOUND -> R.string.adhan_hint_sound
    AlertMode.CUSTOM -> R.string.adhan_hint_custom
    AlertMode.NOTIFICATION -> R.string.adhan_hint_notification
    AlertMode.SILENT -> R.string.adhan_hint_silent
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
 * lit when on. At least one stays on (no days at all is the prayer's switch, above).
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
                    .background(if (on) c.gold else c.glassRaised)
                    .toggleable(on, role = Role.Checkbox) { onChange(if (it) days or bit else days and bit.inv()) }
                    .semantics { contentDescription = d.getDisplayName(java.time.format.TextStyle.FULL, f.locale) },
                contentAlignment = Alignment.Center,
            ) {
                // (Arabic: the day's one-letter form, ح ن ث ر خ ج س; English: two letters.)
                Text(if (f.arabic) d.getDisplayName(java.time.format.TextStyle.NARROW, f.locale) else f.weekdayShort(d).take(2),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = if (f.arabic) 14.sp else 12.sp),
                    color = if (on) c.onGold else c.textSecondary, maxLines = 1)
            }
        }
    }
}
