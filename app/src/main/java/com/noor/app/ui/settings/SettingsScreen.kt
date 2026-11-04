package com.noor.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.BuildConfigInfo
import com.noor.app.R
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.AsrSchool
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Places
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles

private enum class Dialog { NONE, LOCATION_MODE, AUTHORITY, ASR, HIGH_LAT, OFFSETS, LANGUAGE, NUMERALS, TIME, THEME, HIJRI, BACKGROUND }

@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenCities: () -> Unit, onOpenAccuracy: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    var dialog by remember { mutableStateOf(Dialog.NONE) }

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setLocationMode(LocationMode.AUTO)
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setBackgroundUpdates(granted)
    }

    val autoName = stringResource(R.string.authority_auto)
    val profileName = s.profileId?.let { Profiles.byId(it) }?.let { if (ar) it.nameAr else it.nameEn } ?: autoName

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        SettingsSection(stringResource(R.string.settings_location)) {
            val modeLabel = if (s.locationMode == LocationMode.AUTO) stringResource(R.string.location_auto)
            else s.manualCityId?.let { Places.byId(it) }?.let { if (ar) it.nameAr else it.nameEn } ?: stringResource(R.string.location_manual)
            SettingsItem(Icons.Rounded.LocationOn, stringResource(R.string.settings_location_mode), modeLabel, onClick = { dialog = Dialog.LOCATION_MODE })
            if (s.locationMode == LocationMode.AUTO) {
                SettingsDivider()
                val bgOn = s.backgroundUpdates && vm.hasBackgroundPermission()
                SettingsItem(
                    Icons.Rounded.Update, stringResource(R.string.background_updates),
                    stringResource(if (bgOn) R.string.background_updates_on else R.string.background_updates_off), null,
                ) { NoorSwitch(bgOn) { on -> if (on) dialog = Dialog.BACKGROUND else vm.setBackgroundUpdates(false) } }
                SettingsDivider()
                SettingsItem(Icons.Rounded.MyLocation, stringResource(R.string.refresh_location), null, { vm.refreshLocation() })
            }
        }

        SettingsSection(stringResource(R.string.settings_calculation)) {
            SettingsItem(Icons.Rounded.Public, stringResource(R.string.authority), profileName, onClick = { dialog = Dialog.AUTHORITY })
            SettingsDivider()
            val asrLabel = when (s.asrSchool) {
                null -> stringResource(R.string.asr_profile_default)
                AsrSchool.STANDARD -> stringResource(R.string.asr_standard)
                AsrSchool.HANAFI -> stringResource(R.string.asr_hanafi)
            }
            SettingsItem(Icons.Rounded.Schedule, stringResource(R.string.asr_method), asrLabel, onClick = { dialog = Dialog.ASR })
            SettingsDivider()
            SettingsItem(Icons.Rounded.Explore, stringResource(R.string.high_latitude), highLatLabel(s.highLatitudeRule), onClick = { dialog = Dialog.HIGH_LAT })
            SettingsDivider()
            val adj = s.offsets.entries.joinToString("  ") { (p, m) -> "${f.prayerName(p)} ${f.digits(if (m > 0) "+$m" else "$m")}" }
            SettingsItem(Icons.Rounded.Tune, stringResource(R.string.adjustments), adj.ifEmpty { stringResource(R.string.adjustments_body) }, onClick = { dialog = Dialog.OFFSETS })
        }

        SettingsSection(stringResource(R.string.settings_display)) {
            SettingsItem(Icons.Rounded.Language, stringResource(R.string.language), languageLabel(s.language), onClick = { dialog = Dialog.LANGUAGE })
            SettingsDivider()
            SettingsItem(Icons.Rounded.Numbers, stringResource(R.string.numerals),
                stringResource(if (s.numerals == Numerals.WESTERN) R.string.numerals_western else R.string.numerals_arabic), onClick = { dialog = Dialog.NUMERALS })
            SettingsDivider()
            SettingsItem(Icons.Rounded.AccessTime, stringResource(R.string.time_format),
                stringResource(if (s.timeFormat == TimeFormat.H12) R.string.time_12 else R.string.time_24), onClick = { dialog = Dialog.TIME })
            SettingsDivider()
            SettingsItem(Icons.Rounded.Brightness6, stringResource(R.string.theme), themeLabel(s.theme), onClick = { dialog = Dialog.THEME })
            SettingsDivider()
            SettingsItem(Icons.Rounded.CalendarMonth, stringResource(R.string.hijri_adjust), stringResource(R.string.hijri_adjust_value, f.digits(if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}")), null) {
                Stepper(f.digits(if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}"), { vm.setHijriAdjust(s.hijriAdjust - 1) }, { vm.setHijriAdjust(s.hijriAdjust + 1) })
            }
        }

        SettingsSection(stringResource(R.string.settings_about)) {
            SettingsItem(Icons.Rounded.Verified, stringResource(R.string.about_accuracy), null, onOpenAccuracy)
            SettingsDivider()
            SettingsItem(Icons.Rounded.Info, stringResource(R.string.about_version, BuildConfigInfo.versionName), null, null)
        }
        Spacer(Modifier.height(12.dp))
    }

    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.LOCATION_MODE -> ChoiceDialog(
            stringResource(R.string.settings_location_mode),
            listOf(LocationMode.AUTO to stringResource(R.string.location_auto), LocationMode.MANUAL to stringResource(R.string.location_manual)),
            s.locationMode,
            onSelect = { m ->
                if (m == LocationMode.MANUAL) onOpenCities()
                else if (vm.hasForegroundPermission()) vm.setLocationMode(LocationMode.AUTO)
                else foreground.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUTHORITY -> ChoiceDialog(
            stringResource(R.string.authority),
            listOf<Pair<String?, String>>(null to autoName) + Profiles.ALL.map { it.id to if (ar) it.nameAr else it.nameEn },
            s.profileId, onSelect = { vm.setProfile(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ASR -> ChoiceDialog(
            stringResource(R.string.asr_method),
            listOf<Pair<AsrSchool?, String>>(null to stringResource(R.string.asr_profile_default),
                AsrSchool.STANDARD to stringResource(R.string.asr_standard), AsrSchool.HANAFI to stringResource(R.string.asr_hanafi)),
            s.asrSchool, onSelect = { vm.setAsr(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.HIGH_LAT -> ChoiceDialog(
            stringResource(R.string.high_latitude),
            HighLatitudeRule.entries.map { it to highLatLabelStatic(it) }.map { (r, id) -> r to stringResource(id) },
            s.highLatitudeRule, onSelect = { vm.setHighLatitude(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LANGUAGE -> ChoiceDialog(
            stringResource(R.string.language),
            Language.entries.map { it to languageLabel(it) }, s.language,
            onSelect = { vm.setLanguage(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NUMERALS -> ChoiceDialog(
            stringResource(R.string.numerals),
            listOf(Numerals.WESTERN to stringResource(R.string.numerals_western), Numerals.ARABIC_INDIC to stringResource(R.string.numerals_arabic)),
            s.numerals, onSelect = { vm.setNumerals(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.TIME -> ChoiceDialog(
            stringResource(R.string.time_format),
            listOf(TimeFormat.H12 to stringResource(R.string.time_12), TimeFormat.H24 to stringResource(R.string.time_24)),
            s.timeFormat, onSelect = { vm.setTimeFormat(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.THEME -> ChoiceDialog(
            stringResource(R.string.theme), ThemeMode.entries.map { it to themeLabel(it) }, s.theme,
            onSelect = { vm.setTheme(it) }, onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.HIJRI -> Unit
        Dialog.OFFSETS -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.adjustments)) },
            text = {
                Column {
                    for (p in Prayer.entries) {
                        val m = s.offsets[p] ?: 0
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.prayerName(p), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Stepper(f.digits(if (m > 0) "+$m" else "$m"), { vm.setOffset(p, m - 1) }, { vm.setOffset(p, m + 1) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.done), color = c.gold) } },
            dismissButton = { TextButton(onClick = { vm.resetOffsets() }) { Text(stringResource(R.string.reset), color = c.textSecondary) } },
        )
        Dialog.BACKGROUND -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.bg_perm_title)) },
            text = { Text(stringResource(R.string.bg_perm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = Dialog.NONE
                    if (vm.hasBackgroundPermission()) vm.setBackgroundUpdates(true)
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.bg_perm_continue), color = c.gold) }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.bg_perm_not_now), color = c.textSecondary) } },
        )
    }
}

private fun highLatLabelStatic(r: HighLatitudeRule): Int = when (r) {
    HighLatitudeRule.ANGLE_BASED -> R.string.hl_angle
    HighLatitudeRule.MIDDLE_OF_NIGHT -> R.string.hl_middle
    HighLatitudeRule.ONE_SEVENTH -> R.string.hl_seventh
    HighLatitudeRule.NONE -> R.string.hl_none
}

@Composable private fun highLatLabel(r: HighLatitudeRule) = stringResource(highLatLabelStatic(r))

@Composable private fun languageLabel(l: Language) = stringResource(
    when (l) { Language.SYSTEM -> R.string.lang_system; Language.ENGLISH -> R.string.lang_en; Language.ARABIC -> R.string.lang_ar },
)

@Composable private fun themeLabel(t: ThemeMode) = stringResource(
    when (t) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.LIGHT -> R.string.theme_light },
)
