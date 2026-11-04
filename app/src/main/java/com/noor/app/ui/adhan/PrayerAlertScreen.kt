package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSounds
import com.noor.alerts.SoundEntry
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
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

/** Everything about one prayer's alert: type, sound, volume, vibration, length, and a real test. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerAlertScreen(contentPadding: PaddingValues, prayer: Prayer, onBack: () -> Unit, onOpenMySounds: () -> Unit, vm: AdhanViewModel = viewModel()) {
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
    var stopDialog by remember { mutableStateOf(false) }
    val previewLabel = stringResource(R.string.adhan_preview)

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSound(uri, forPrayer = prayer) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BackHeader(f.prayerName(prayer), onBack)

        GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
            SettingsItem(Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_alert_for, f.prayerName(prayer)),
                if (!alerts.enabled) stringResource(R.string.adhan_master_off_short) else null, null) {
                NoorSwitch(a.enabled) { on -> vm.setPrayer(prayer) { it.copy(enabled = on) } }
            }
        }
        if (prayer == Prayer.SHURUQ) Footnote(stringResource(R.string.adhan_sunrise_note))

        if (a.enabled) {
            Column {
                Label(stringResource(R.string.adhan_type))
                val modes = if (prayer == Prayer.SHURUQ) listOf(AlertMode.SOUND, AlertMode.CUSTOM, AlertMode.NOTIFICATION, AlertMode.SILENT)
                else AlertMode.entries
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEach { m ->
                        FilterChip(
                            selected = a.mode == m, onClick = { vm.setMode(prayer, m) },
                            label = { Text(stringResource(modeLabel(m))) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.gold, selectedLabelColor = c.onGold, labelColor = c.textSecondary),
                        )
                    }
                }
            }

            when (a.mode) {
                AlertMode.ADHAN, AlertMode.ADHAN_SHORT -> {
                    val short = a.mode == AlertMode.ADHAN_SHORT
                    val list = lib.adhansFor(prayer).filter { short || it.hasFullRecording }
                    if (prayer == Prayer.FAJR) {
                        val chosen = lib.byId(a.soundId)
                        Footnote(stringResource(if (chosen != null && !chosen.fajr) R.string.adhan_fajr_warn else R.string.adhan_fajr_note))
                    }
                    GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                        list.forEachIndexed { i, e ->
                            if (i > 0) SettingsDivider()
                            SoundRow(
                                title = e.title(ar), detail = adhanDetail(e, short, f, ar),
                                selected = a.soundId == e.id, playing = preview == PreviewKey(e.id, short), previewLabel = previewLabel,
                                onSelect = { vm.setPrayer(prayer) { it.copy(soundId = e.id) } },
                                onPreview = { vm.previewLibrary(e.id, short, a.volumePercent) },
                                badge = if (e.fajr) stringResource(R.string.adhan_badge_fajr) else null,
                            )
                        }
                    }
                    if (!short && alerts.device.includes(AlertDevice.WATCH)) Note(Icons.Rounded.Watch, stringResource(R.string.adhan_watch_receives))
                }
                AlertMode.SOUND -> {
                    val groups = listOf("brief" to R.string.adhan_group_brief, "short" to R.string.adhan_group_short, "long" to R.string.adhan_group_long)
                    groups.forEach { (cat, label) ->
                        val items = lib.sounds.filter { cat in it.categories }
                        if (items.isEmpty()) return@forEach
                        Column {
                            Label(stringResource(label))
                            GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                                items.forEachIndexed { i, e ->
                                    if (i > 0) SettingsDivider()
                                    SoundRow(e.title(ar), f.duration(e.seconds), a.soundId == e.id, preview == PreviewKey(e.id, false), previewLabel,
                                        onSelect = { vm.setPrayer(prayer) { it.copy(soundId = e.id) } },
                                        onPreview = { vm.previewLibrary(e.id, false, a.volumePercent) })
                                }
                            }
                        }
                    }
                }
                AlertMode.CUSTOM -> {
                    GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                        if (custom.isEmpty()) Footnote(stringResource(R.string.adhan_custom_empty), Modifier.padding(12.dp))
                        custom.forEachIndexed { i, cs ->
                            if (i > 0) SettingsDivider()
                            SoundRow(cs.name, f.duration(cs.seconds), a.soundId == cs.id, preview == PreviewKey(cs.id, false), previewLabel,
                                onSelect = { vm.setPrayer(prayer) { it.copy(soundId = cs.id) } },
                                onPreview = { vm.previewCustom(cs.id, a.volumePercent) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { picker.launch(CustomSounds.PICKER_TYPES) }, colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onGold)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.adhan_import))
                        }
                        if (custom.isNotEmpty()) OutlinedButton(onClick = onOpenMySounds) { Text(stringResource(R.string.adhan_manage), color = c.goldText) }
                    }
                    if (alerts.device.includes(AlertDevice.WATCH)) Note(Icons.Rounded.Watch, stringResource(R.string.adhan_watch_receives))
                }
                AlertMode.NOTIFICATION -> Note(Icons.Rounded.Info, stringResource(R.string.adhan_notification_desc))
                AlertMode.SILENT -> Note(Icons.Rounded.Info, stringResource(R.string.adhan_silent_desc))
            }

            if (a.mode.playsAudio) {
                SettingsSection(stringResource(R.string.adhan_options)) {
                    var vol by remember(a.volumePercent) { mutableFloatStateOf(a.volumePercent.toFloat()) }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = c.textSecondary)
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(R.string.adhan_volume), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(f.number(vol.toInt()) + if (ar) "٪" else "%", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                        Slider(value = vol, onValueChange = { vol = it }, valueRange = 5f..100f,
                            onValueChangeFinished = { vm.setPrayer(prayer) { it.copy(volumePercent = vol.toInt()) } },
                            colors = SliderDefaults.colors(thumbColor = c.gold, activeTrackColor = c.gold, inactiveTrackColor = c.surfaceRaised))
                    }
                    SettingsDivider()
                    SettingsItem(Icons.Rounded.Vibration, stringResource(R.string.adhan_vibrate), null, null) {
                        NoorSwitch(a.vibrate) { on -> vm.setPrayer(prayer) { it.copy(vibrate = on) } }
                    }
                    SettingsDivider()
                    SettingsItem(Icons.Rounded.Timer, stringResource(R.string.adhan_stop_after), stopLabel(a.maxSeconds, f), onClick = { stopDialog = true })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.test(prayer) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.adhan_test), color = c.goldText)
                }
                Footnote(stringResource(R.string.adhan_test_desc))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding))
    }

    if (stopDialog) {
        ChoiceDialog(stringResource(R.string.adhan_stop_after), listOf(0, 30, 60, 120, 180).map { it to stopLabel(it, f) }, a.maxSeconds,
            onSelect = { sec -> vm.setPrayer(prayer) { it.copy(maxSeconds = sec) } }, onDismiss = { stopDialog = false })
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = NoorTheme.colors.gold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = NoorTheme.colors
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = c.textMuted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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
