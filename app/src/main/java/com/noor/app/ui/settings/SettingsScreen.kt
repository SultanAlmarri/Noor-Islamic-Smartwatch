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
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
fun SettingsScreen(contentPadding: PaddingValues, onOpenCities: () -> Unit, onOpenAccuracy: () -> Unit, vm: SettingsViewModel = viewModel(),
                   onOpenRoute: (String) -> Unit = {}) {
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
    // The persistent notification needs the notification permission (Android 13+); it turns on only once granted.
    val statusPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setStatusNotification(true)
    }

    val currentPlace = remember(s) { com.noor.app.data.PrayerProvider.place(s.copy(profileId = null)) }
    val autoAuthority = currentPlace?.let { com.noor.app.data.PrayerProvider.authority(s.copy(profileId = null), it) }
    val autoName = stringResource(R.string.authority_auto) + (autoAuthority?.let { ": " + if (ar) it.nameAr else it.nameEn } ?: "")
    val context = androidx.compose.ui.platform.LocalContext.current
    val profileName = s.profileId?.let { Profiles.byId(it) }?.let { if (ar) it.nameAr else it.nameEn } ?: autoName

    // Where today's times come from (shown here since 0.9.2, when it left the home screen).
    val app = context.applicationContext as com.noor.app.NoorApplication
    val basis = remember(s) {
        val place = com.noor.app.data.PrayerProvider.place(s) ?: return@remember null
        val profile = com.noor.app.data.PrayerProvider.profile(s, place)
        val auth = com.noor.app.data.PrayerProvider.authority(s, place)
        val official = runCatching { com.noor.app.data.PrayerProvider.resolve(java.time.LocalDate.now(place.zone), place, profile, s, app.container.timetables).official }.getOrDefault(false)
        Triple(auth, profile, official)
    }
    val basisText = basis?.let { (auth, profile, official) ->
        val name = auth?.let { if (ar) it.nameAr else it.nameEn } ?: if (ar) profile.nameAr else profile.nameEn
        when {
            official -> stringResource(R.string.source_official, name)
            auth == null -> stringResource(R.string.source_manual_method)
            auth.kind == com.noor.prayer.authority.SourceKind.VALIDATED_CALCULATION -> stringResource(R.string.source_validated, name)
            auth.kind == com.noor.prayer.authority.SourceKind.AUTHORITY_PARAMETERS -> stringResource(R.string.source_parameters, name)
            else -> stringResource(R.string.source_recognised)
        }
    }

    // Every row once, in order, with its group and the words it can be found by (0.9.4: groups and search).
    class Row(val group: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val value: String?,
              val keywords: String, val onClick: (() -> Unit)?, val trailing: (@Composable () -> Unit)? = null)
    val modeLabel = if (s.locationMode == LocationMode.AUTO) stringResource(R.string.location_auto)
        else s.manualPlace?.let { if (ar) it.placeNameAr ?: it.placeName else it.placeName }
            ?: s.manualCityId?.let { Places.byId(it) }?.let { if (ar) it.nameAr else it.nameEn } ?: stringResource(R.string.location_manual)
    val bgOn = s.backgroundUpdates && vm.hasBackgroundPermission()
    val asrLabel = when (s.asrSchool) {
        null -> stringResource(R.string.asr_profile_default)
        AsrSchool.STANDARD -> stringResource(R.string.asr_standard)
        AsrSchool.HANAFI -> stringResource(R.string.asr_hanafi)
    }
    val adj = s.offsets.entries.joinToString("  ") { (p, m) -> "${f.prayerName(p)} ${f.digits(if (m > 0) "+$m" else "$m")}" }
    val hijriValue = f.digits(if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}")
    val rows = buildList {
        add(Row(R.string.settings_location, Icons.Rounded.LocationOn, stringResource(R.string.settings_location_mode), modeLabel, stringResource(R.string.kw_location), { dialog = Dialog.LOCATION_MODE }))
        if (s.locationMode == LocationMode.AUTO) {
            add(Row(R.string.settings_location, Icons.Rounded.Update, stringResource(R.string.background_updates),
                stringResource(if (bgOn) R.string.background_updates_on else R.string.background_updates_off), stringResource(R.string.kw_location), null) {
                NoorSwitch(bgOn) { on -> if (on) dialog = Dialog.BACKGROUND else vm.setBackgroundUpdates(false) }
            })
            val precise = vm.hasPrecise()
            add(Row(R.string.settings_location, Icons.Rounded.GpsFixed, stringResource(R.string.location_precision),
                stringResource(if (precise) R.string.location_precise else R.string.location_approximate), stringResource(R.string.kw_location), {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }))
            add(Row(R.string.settings_location, Icons.Rounded.MyLocation, stringResource(R.string.refresh_location), null, stringResource(R.string.kw_location), { vm.refreshLocation() }))
        }
        add(Row(R.string.settings_calculation, Icons.Rounded.Public, stringResource(R.string.authority), profileName, stringResource(R.string.kw_authority), { dialog = Dialog.AUTHORITY }))
        add(Row(R.string.settings_calculation, Icons.Rounded.Schedule, stringResource(R.string.asr_method), asrLabel, stringResource(R.string.kw_asr), { dialog = Dialog.ASR }))
        add(Row(R.string.settings_calculation, Icons.Rounded.Explore, stringResource(R.string.high_latitude), highLatLabel(s.highLatitudeRule), stringResource(R.string.kw_high_latitude), { dialog = Dialog.HIGH_LAT }))
        add(Row(R.string.settings_calculation, Icons.Rounded.Tune, stringResource(R.string.adjustments), adj.ifEmpty { stringResource(R.string.adjustments_body) }, stringResource(R.string.kw_adjust), { dialog = Dialog.OFFSETS }))
        add(Row(R.string.settings_alerts, Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_title),
            stringResource(if (s.alerts.enabled) R.string.settings_on else R.string.settings_off), stringResource(R.string.kw_alerts), { onOpenRoute(com.noor.app.Routes.ADHAN) }))
        add(Row(R.string.settings_alerts, Icons.Rounded.History, stringResource(R.string.history_title), null, stringResource(R.string.kw_alerts), { onOpenRoute(com.noor.app.Routes.ADHAN_HISTORY) }))
        add(Row(R.string.settings_notification_bar, Icons.Rounded.Notifications, stringResource(R.string.status_setting), stringResource(R.string.status_setting_desc), stringResource(R.string.kw_status), null) {
            NoorSwitch(s.statusNotification) { on ->
                val needs = on && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needs) statusPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else vm.setStatusNotification(on)
            }
        })
        add(Row(R.string.settings_calendar, Icons.Rounded.CalendarMonth, stringResource(R.string.hijri_adjust), stringResource(R.string.hijri_adjust_value, hijriValue), stringResource(R.string.kw_hijri), null) {
            Stepper(hijriValue, { vm.setHijriAdjust(s.hijriAdjust - 1) }, { vm.setHijriAdjust(s.hijriAdjust + 1) })
        })
        add(Row(R.string.settings_display, Icons.Rounded.Language, stringResource(R.string.language), languageLabel(s.language), stringResource(R.string.kw_language), { dialog = Dialog.LANGUAGE }))
        add(Row(R.string.settings_display, Icons.Rounded.Numbers, stringResource(R.string.numerals),
            stringResource(if (s.numerals == Numerals.WESTERN) R.string.numerals_western else R.string.numerals_arabic), stringResource(R.string.kw_numerals), { dialog = Dialog.NUMERALS }))
        add(Row(R.string.settings_display, Icons.Rounded.AccessTime, stringResource(R.string.time_format),
            stringResource(if (s.timeFormat == TimeFormat.H12) R.string.time_12 else R.string.time_24), stringResource(R.string.kw_time), { dialog = Dialog.TIME }))
        add(Row(R.string.settings_display, Icons.Rounded.Brightness6, stringResource(R.string.theme), themeLabel(s.theme), stringResource(R.string.kw_theme), { dialog = Dialog.THEME }))
        add(Row(R.string.settings_watch, Icons.Rounded.Watch, stringResource(R.string.settings_watch_row), null, stringResource(R.string.kw_watch), { onOpenRoute(com.noor.app.Routes.WATCH) }))
        add(Row(R.string.settings_about, Icons.Rounded.Verified, stringResource(R.string.about_accuracy), null, stringResource(R.string.kw_accuracy), onOpenAccuracy))
        add(Row(R.string.settings_about, Icons.Rounded.Info, stringResource(R.string.about_version, BuildConfigInfo.versionName), null, "", null))
    }
    val footers = mapOf(
        R.string.settings_calculation to basisText,
        R.string.settings_notification_bar to stringResource(R.string.status_setting_note, f.number(14)),
        R.string.settings_calendar to stringResource(R.string.calendar_hijri_note),
    )
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        com.noor.app.ui.components.ScreenTitle(stringResource(R.string.tab_settings))
        SettingsSearch(query) { query = it }

        @Composable
        fun item(r: Row) = SettingsItem(r.icon, r.title, r.value, r.onClick, r.trailing)
        val q = query.trim()
        if (q.isNotEmpty()) {
            // Every word typed must appear in the row's title, value, group or keywords (in the current language).
            val words = q.lowercase().split(Regex("\\s+"))
            val found = rows.filter { r ->
                val hay = listOf(r.title, r.value.orEmpty(), stringResource(r.group), r.keywords).joinToString(" ").lowercase()
                words.all { it in hay }
            }
            if (found.isEmpty()) Text(stringResource(R.string.settings_no_results, q), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp))
            else SettingsSection(stringResource(R.string.settings_results)) {
                found.forEachIndexed { i, r -> if (i > 0) SettingsDivider(); item(r) }
            }
        } else {
            rows.groupBy { it.group }.forEach { (group, list) ->
                SettingsSection(stringResource(group), footer = footers[group]) {
                    list.forEachIndexed { i, r -> if (i > 0) SettingsDivider(); item(r) }
                }
            }
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
            listOf<Pair<String?, String>>(null to autoName) + Profiles.ALL.map { p ->
                val auth = com.noor.prayer.authority.Authorities.forProfile(p.id)
                val kind = when {
                    auth?.timetableId != null -> R.string.kind_official_and_validated
                    auth?.kind == com.noor.prayer.authority.SourceKind.VALIDATED_CALCULATION -> R.string.kind_validated
                    auth?.kind == com.noor.prayer.authority.SourceKind.AUTHORITY_PARAMETERS -> R.string.kind_parameters
                    else -> R.string.kind_recognised
                }
                p.id to (if (ar) p.nameAr else p.nameEn) + "\n" + stringResource(kind)
            },
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
    when (t) { ThemeMode.SUN -> R.string.theme_sun; ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.DARK -> R.string.theme_dark; ThemeMode.LIGHT -> R.string.theme_light },
)

/** Search over every setting, in the language of the screen. */
@Composable
private fun SettingsSearch(query: String, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    androidx.compose.material3.OutlinedTextField(
        value = query, onValueChange = onChange, singleLine = true,
        placeholder = { Text(stringResource(R.string.settings_search), color = c.textMuted) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.textMuted) },
        trailingIcon = { if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { onChange("") }) {
            Icon(Icons.Rounded.Close, stringResource(R.string.settings_search_clear), tint = c.textMuted) } },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.gold, unfocusedBorderColor = c.outline, cursorColor = c.gold,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

