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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSound(uri, forPrayer = prayer) }

    NoorPage(f.prayerName(prayer), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        SettingsSection("", footer = if (prayer == Prayer.SHURUQ) stringResource(R.string.adhan_sunrise_note) else null) {
            SettingsSwitch(Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_alert_for, f.prayerName(prayer)),
                if (!alerts.enabled) stringResource(R.string.adhan_master_off_short) else null, a.enabled) { on -> vm.setPrayer(prayer) { it.copy(enabled = on) } }
        }

        if (a.enabled) {
            Column {
                SectionHeader(stringResource(R.string.adhan_type))
                val modes = if (prayer == Prayer.SHURUQ) listOf(AlertMode.SOUND, AlertMode.CUSTOM, AlertMode.NOTIFICATION, AlertMode.SILENT)
                else AlertMode.entries
                ModeTiles(modes, a.mode) { vm.setMode(prayer, it) }
                when (a.mode) {
                    AlertMode.NOTIFICATION -> SectionFooter(stringResource(R.string.adhan_notification_desc))
                    AlertMode.SILENT -> SectionFooter(stringResource(R.string.adhan_silent_desc))
                    else -> Unit
                }
            }

            when (a.mode) {
                AlertMode.ADHAN, AlertMode.ADHAN_SHORT -> {
                    val short = a.mode == AlertMode.ADHAN_SHORT
                    val list = lib.adhansFor(prayer).filter { short || it.hasFullRecording }
                    val fajrNote = if (prayer == Prayer.FAJR) {
                        val chosen = lib.byId(a.soundId)
                        stringResource(if (chosen != null && !chosen.fajr) R.string.adhan_fajr_warn else R.string.adhan_fajr_note)
                    } else null
                    val watchNote = if (!short && alerts.device.includes(AlertDevice.WATCH)) stringResource(R.string.adhan_watch_receives) else null
                    SettingsSection(stringResource(modeLabel(a.mode)), footer = listOfNotNull(fajrNote, watchNote).joinToString("\n\n").ifEmpty { null }) {
                        // Random from favourites (0.9.5): a different favourite each time, none twice in a row.
                        val usable = alerts.favourites.mapNotNull { lib.byId(it) }.filter { e -> (short || e.hasFullRecording) && (prayer == Prayer.FAJR || !e.fajr) }
                        val randomDetail = if (usable.size >= 2) stringResource(R.string.adhan_random_detail, f.number(usable.size))
                            else stringResource(R.string.adhan_random_needs)
                        SoundRow(
                            title = stringResource(R.string.adhan_random), detail = randomDetail,
                            selected = a.soundId == com.noor.prayer.alerts.SoundIds.RANDOM, playing = false, previewLabel = previewLabel,
                            // (Without two favourites there is nothing to take turns: the row leads to where they are marked.)
                            onSelect = { if (usable.size >= 2) vm.setPrayer(prayer) { it.copy(soundId = com.noor.prayer.alerts.SoundIds.RANDOM) } else onOpenLibrary() },
                            onPreview = null,
                        )
                        list.forEach { e ->
                            SettingsDivider()
                            SoundRow(
                                title = e.title(ar), detail = adhanDetail(e, short, f, ar),
                                selected = a.soundId == e.id, playing = preview == PreviewKey(e.id, short), previewLabel = previewLabel,
                                onSelect = { vm.setPrayer(prayer) { it.copy(soundId = e.id) } },
                                onPreview = { vm.previewLibrary(e.id, short, a.volumePercent) },
                                badge = if (e.fajr) stringResource(R.string.adhan_badge_fajr) else null,
                            )
                        }
                    }
                }
                AlertMode.SOUND -> {
                    val groups = listOf("brief" to R.string.adhan_group_brief, "short" to R.string.adhan_group_short, "long" to R.string.adhan_group_long)
                    groups.forEach { (cat, label) ->
                        val items = lib.sounds.filter { cat in it.categories }
                        if (items.isEmpty()) return@forEach
                        SettingsSection(stringResource(label)) {
                            items.forEachIndexed { i, e ->
                                if (i > 0) SettingsDivider()
                                SoundRow(e.title(ar), f.duration(e.seconds), a.soundId == e.id, preview == PreviewKey(e.id, false), previewLabel,
                                    onSelect = { vm.setPrayer(prayer) { it.copy(soundId = e.id) } },
                                    onPreview = { vm.previewLibrary(e.id, false, a.volumePercent) })
                            }
                        }
                    }
                }
                AlertMode.CUSTOM -> {
                    SettingsSection(stringResource(R.string.adhan_mode_custom),
                        footer = if (alerts.device.includes(AlertDevice.WATCH)) stringResource(R.string.adhan_watch_receives) else null) {
                        if (custom.isEmpty()) Text(stringResource(R.string.adhan_custom_empty), style = FootnoteStyle, color = c.textSecondary, modifier = Modifier.padding(16.dp))
                        custom.forEachIndexed { i, cs ->
                            if (i > 0) SettingsDivider()
                            SoundRow(cs.name, f.duration(cs.seconds), a.soundId == cs.id, preview == PreviewKey(cs.id, false), previewLabel,
                                onSelect = { vm.setPrayer(prayer) { it.copy(soundId = cs.id) } },
                                onPreview = { vm.previewCustom(cs.id, a.volumePercent) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NoorButton(stringResource(R.string.adhan_import), { picker.launch(CustomSounds.PICKER_TYPES) }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                        if (custom.isNotEmpty()) NoorButton(stringResource(R.string.adhan_manage), onOpenMySounds, Modifier.weight(1f), kind = ButtonKind.Tonal)
                    }
                }
                AlertMode.NOTIFICATION, AlertMode.SILENT -> Unit
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
                            .background(if (on) c.gold.copy(alpha = if (c.isDark) 0.16f else 0.13f) else c.surface)
                            .border(if (on) 1.5.dp else 0.5.dp, if (on) c.gold else c.separator, shape)
                            .clickable(role = Role.RadioButton) { if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(m) }
                            .semantics { this.selected = on }.padding(horizontal = 6.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(modeIcon(m), null, tint = if (on) c.gold else c.textSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(modeLabel(m)), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp, lineHeight = 18.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (on) c.goldText else c.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
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
