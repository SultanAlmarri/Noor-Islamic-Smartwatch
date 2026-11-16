package com.noor.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.BuildConfigInfo
import com.noor.app.R
import com.noor.app.Routes
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.PrayerProvider
import com.noor.app.data.Scenery
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.NoorSearchField
import com.noor.app.ui.components.NoorSheet
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.AsrSchool
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import kotlinx.coroutines.launch

private enum class Dialog { NONE, AUTHORITY, ASR, HIGH_LAT, OFFSETS, LANGUAGE, NUMERALS, TIME, THEME, BACKGROUND }

/**
 * One row of Settings: a link (a chevron), a choice (its value at the end of the row), a switch (the whole row
 * toggles) or a row that holds a control. [keywords] are the words it can be found by, in the language of the screen.
 */
private data class SettingRow(
    val group: Int, val icon: ImageVector, val title: String, val value: String?, val keywords: String, val onClick: (() -> Unit)?,
    val detail: String? = null, val toggle: Pair<Boolean, (Boolean) -> Unit>? = null,
    val listed: Boolean = true, val page: SettingsPage = SettingsPage.ROOT, val trailing: (@Composable () -> Unit)? = null,
)

/** Groups with no title (a row whose name says it all), by number. */
private const val UNTITLED_2 = -2
private const val UNTITLED_3 = -3

/** The pages of Settings (0.15): the first, and two it opens. */
private enum class SettingsPage { ROOT, TIMES, LANGUAGE }

/**
 * Settings (0.15). The first page holds what people come for, in the order they need it, and no more than a phone's
 * screen and a half: the place (a hero card that opens the location page), then Prayer (how the times are worked out,
 * the Adhan, the notification bar), Appearance (theme, the home screen, language and region), the watch, about.
 * What most people set once and never again (the calculation method, Asr, the high-latitude rule, adjustments;
 * language, numerals, time format, the Hijri date) is one level down, on a page of its own, named for what it holds
 * (progressive disclosure: two levels, never three). Search finds every setting on every page, by its name, its
 * value or the words people use for it, says which page it is on, and works it from the results. Choices open in a
 * sheet from the bottom.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenCities: () -> Unit, onOpenAccuracy: () -> Unit, vm: SettingsViewModel = viewModel(),
                   onOpenRoute: (String) -> Unit = {}) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    // (Saved: a sheet that was open is open again after the system recreates the screen, e.g. on a theme change.)
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    // The Settings tab tapped again: back to the first page, as on iOS.
    val reselect = com.noor.app.LocalTabReselect.current
    var seenReselect by rememberSaveable { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(reselect) {
        if (reselect != 0 && reselect != seenReselect) page = SettingsPage.ROOT
        seenReselect = reselect
    }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setBackgroundUpdates(granted)
    }
    // The persistent notification needs the notification permission (Android 13+); it turns on only once granted.
    val statusPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setStatusNotification(true)
    }

    val currentPlace = remember(s) { PrayerProvider.place(s.copy(profileId = null)) }
    val autoAuthority = currentPlace?.let { PrayerProvider.authority(s.copy(profileId = null), it) }
    val autoName = stringResource(R.string.authority_auto) + (autoAuthority?.let { ": " + if (ar) it.nameAr else it.nameEn } ?: "")
    val context = androidx.compose.ui.platform.LocalContext.current
    val profileName = s.profileId?.let { Profiles.byId(it) }?.let { if (ar) it.nameAr else it.nameEn } ?: autoName
    // The name of what the times follow, short: the chosen method, or the authority chosen for the place.
    val timesName = s.profileId?.let { Profiles.byId(it) }?.let { if (ar) it.nameAr else it.nameEn }
        ?: autoAuthority?.let { if (ar) it.nameAr else it.nameEn } ?: stringResource(R.string.authority_auto)

    // Where today's times come from (shown here since 0.9.2, when it left the home screen).
    val app = context.applicationContext as com.noor.app.NoorApplication
    val basis = remember(s) {
        val place = PrayerProvider.place(s) ?: return@remember null
        val profile = PrayerProvider.profile(s, place)
        val auth = PrayerProvider.authority(s, place)
        val official = runCatching { PrayerProvider.resolve(java.time.LocalDate.now(place.zone), place, profile, s, app.container.timetables).official }.getOrDefault(false)
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

    // Permissions can change while Noor is in the background (the user comes back from Android's settings): read again.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumed++; onPauseOrDispose { } }
    val bgOn = remember(s, resumed) { s.backgroundUpdates && vm.hasBackgroundPermission() }
    val precise = remember(s, resumed) { vm.hasPrecise() }

    val placeTitle = currentPlace?.let { if (ar) it.nameAr ?: it.nameEn else it.nameEn } ?: stringResource(R.string.location_unknown)
    val asrLabel = when (s.asrSchool) {
        null -> stringResource(R.string.asr_profile_default)
        AsrSchool.STANDARD -> stringResource(R.string.asr_standard)
        AsrSchool.HANAFI -> stringResource(R.string.asr_hanafi)
    }
    val adj = s.offsets.entries.joinToString("  ") { (p, m) -> "${f.prayerName(p)} ${f.digits(if (m > 0) "+$m" else "$m")}" }
    val hijriValue = f.digits(if (s.hijriAdjust > 0) "+${s.hijriAdjust}" else "${s.hijriAdjust}")
    val kwLocation = stringResource(R.string.kw_location)

    // Every row once, with the page it lives on and its group there (0.9.4: groups and search; 0.15: pages).
    // [listed] = false: found by search only (it has a home elsewhere: the hero card, the Adhan tab).
    val rows = buildList {
        // ---- The first page: what most people come here for, in the order they need it.
        // 0.9.7: one screen for every way of choosing the location; 0.15: the hero card is its entry, this row its
        // name for search.
        add(SettingRow(R.string.settings_location, Icons.Rounded.LocationOn, placeTitle,
            stringResource(if (s.locationMode == LocationMode.AUTO) R.string.loc_label_auto else R.string.loc_label_manual),
            kwLocation + " " + stringResource(R.string.change_location), onOpenCities, listed = false))
        if (s.locationMode == LocationMode.AUTO) {
            add(SettingRow(R.string.settings_location, Icons.Rounded.Update, stringResource(R.string.background_updates),
                stringResource(if (bgOn) R.string.background_updates_on else R.string.background_updates_off), kwLocation, null,
                toggle = bgOn to { on -> if (on) dialog = Dialog.BACKGROUND else vm.setBackgroundUpdates(false) }))
            add(SettingRow(R.string.settings_location, Icons.Rounded.GpsFixed, stringResource(R.string.location_precision),
                stringResource(if (precise) R.string.location_precise else R.string.location_approximate), kwLocation, {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }))
            add(SettingRow(R.string.settings_location, Icons.Rounded.MyLocation, stringResource(R.string.refresh_location), null, kwLocation, { vm.refreshLocation() }))
        }
        add(SettingRow(R.string.settings_group_prayer, Icons.Rounded.Schedule, stringResource(R.string.settings_calculation), timesName,
            stringResource(R.string.kw_authority) + " " + stringResource(R.string.kw_adjust), { page = SettingsPage.TIMES }))
        add(SettingRow(R.string.settings_group_prayer, Icons.Rounded.NotificationsActive, stringResource(R.string.adhan_title), null, stringResource(R.string.kw_alerts), { onOpenRoute(Routes.ADHAN) },
            detail = stringResource(if (s.alerts.enabled) R.string.settings_on else R.string.settings_off)))
        add(SettingRow(R.string.settings_group_prayer, Icons.Rounded.Notifications, stringResource(R.string.status_setting), stringResource(R.string.status_setting_desc), stringResource(R.string.kw_status), null,
            toggle = s.statusNotification to { on ->
                val needs = on && Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needs) statusPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setStatusNotification(on)
            }))
        add(SettingRow(R.string.settings_alerts, Icons.Rounded.History, stringResource(R.string.history_title), null, stringResource(R.string.kw_alerts), { onOpenRoute(Routes.ADHAN_HISTORY) }, listed = false))
        add(SettingRow(R.string.settings_appearance, Icons.Rounded.Brightness6, stringResource(R.string.theme), null, stringResource(R.string.kw_theme), { dialog = Dialog.THEME },
            // (At the end of the row the short name; the sheet says what following the sun means.)
            detail = if (s.theme == ThemeMode.SUN) stringResource(R.string.theme_sun_short) else themeLabel(s.theme)))
        // 0.9.8: the landscape under the home screen's sky; 0.15: the home screen's page (scenery, sky, countdown).
        run {
            val place = PrayerProvider.place(s)
            val t = com.noor.app.sky.Biome.of(s.scenery, place?.countryCode, place?.point?.elevationM, place?.point?.latitude)
            val name = stringResource(sceneryName(t))
            add(SettingRow(R.string.settings_appearance, Icons.Rounded.Landscape, stringResource(R.string.scenery_title),
                if (s.scenery == Scenery.AUTO) stringResource(R.string.scenery_auto_short, name) else name,
                stringResource(R.string.kw_scenery), { onOpenRoute(Routes.SCENERY) }))
            // (It lives on the home screen's page, where the countdown is seen; search finds it from here.)
            add(SettingRow(R.string.countdown_group, Icons.Rounded.Timer, stringResource(R.string.elapsed_setting), stringResource(R.string.elapsed_setting_desc),
                stringResource(R.string.kw_status), null, toggle = s.elapsedAfterPrayer to { on -> vm.setElapsedAfterPrayer(on) }, listed = false))
        }
        add(SettingRow(R.string.settings_appearance, Icons.Rounded.Language, stringResource(R.string.settings_language),
            listOf(languageLabel(s.language), stringResource(if (s.timeFormat == TimeFormat.H12) R.string.time_12 else R.string.time_24)).joinToString(" · "),
            stringResource(R.string.kw_language) + " " + stringResource(R.string.kw_numerals) + " " + stringResource(R.string.kw_time) + " " + stringResource(R.string.kw_hijri),
            { page = SettingsPage.LANGUAGE }))
        add(SettingRow(R.string.settings_watch, Icons.Rounded.Watch, stringResource(R.string.watch_title), null, stringResource(R.string.kw_watch), { onOpenRoute(Routes.WATCH) }))
        add(SettingRow(R.string.settings_about, Icons.Rounded.Verified, stringResource(R.string.about_accuracy), null, stringResource(R.string.kw_accuracy), onOpenAccuracy))
        add(SettingRow(R.string.settings_about, Icons.Rounded.Info, stringResource(R.string.about_version, BuildConfigInfo.versionName), null, "", null))
        // Testing only (0.14): to be removed before release, with com.noor.app.testing (the owner's word).
        add(SettingRow(R.string.settings_testing, Icons.Rounded.Tune, stringResource(R.string.testing_row), stringResource(R.string.testing_row_note),
            stringResource(R.string.kw_testing), { onOpenRoute(Routes.TESTING) }))

        // ---- Prayer times: how they are worked out. Set once by most people, so a page of its own (progressive
        // disclosure), opened from the first page.
        val times = SettingsPage.TIMES
        add(SettingRow(0, Icons.Rounded.Public, stringResource(R.string.authority), profileName, stringResource(R.string.kw_authority), { dialog = Dialog.AUTHORITY }, page = times))
        add(SettingRow(0, Icons.Rounded.Schedule, stringResource(R.string.asr_method), asrLabel, stringResource(R.string.kw_asr), { dialog = Dialog.ASR }, page = times))
        add(SettingRow(0, Icons.Rounded.Tune, stringResource(R.string.adjustments), adj.ifEmpty { stringResource(R.string.adjustments_body) }, stringResource(R.string.kw_adjust), { dialog = Dialog.OFFSETS }, page = times))
        add(SettingRow(UNTITLED_2, Icons.Rounded.Explore, stringResource(R.string.high_latitude), highLatLabel(s.highLatitudeRule), stringResource(R.string.kw_high_latitude), { dialog = Dialog.HIGH_LAT }, page = times))
        add(SettingRow(UNTITLED_3, Icons.Rounded.Verified, stringResource(R.string.about_accuracy), stringResource(R.string.times_accuracy_note), stringResource(R.string.kw_accuracy), onOpenAccuracy, page = times))

        // ---- Language and region: as on iOS, the language, how numbers and times are written, and the calendar.
        val lang = SettingsPage.LANGUAGE
        add(SettingRow(0, Icons.Rounded.Language, stringResource(R.string.language), null, stringResource(R.string.kw_language), { dialog = Dialog.LANGUAGE }, detail = languageLabel(s.language), page = lang))
        add(SettingRow(0, Icons.Rounded.Numbers, stringResource(R.string.numerals), null, stringResource(R.string.kw_numerals), { dialog = Dialog.NUMERALS },
            detail = stringResource(if (s.numerals == Numerals.WESTERN) R.string.numerals_western else R.string.numerals_arabic), page = lang))
        add(SettingRow(0, Icons.Rounded.AccessTime, stringResource(R.string.time_format), null, stringResource(R.string.kw_time), { dialog = Dialog.TIME },
            detail = stringResource(if (s.timeFormat == TimeFormat.H12) R.string.time_12 else R.string.time_24), page = lang))
        add(SettingRow(R.string.settings_calendar, Icons.Rounded.CalendarMonth, stringResource(R.string.hijri_adjust), stringResource(R.string.hijri_adjust_value, hijriValue), stringResource(R.string.kw_hijri), null, page = lang) {
            Stepper(hijriValue, { vm.setHijriAdjust(s.hijriAdjust - 1) }, { vm.setHijriAdjust(s.hijriAdjust + 1) })
        })
    }
    val footers = mapOf(
        UNTITLED_2 to stringResource(R.string.high_latitude_note),
        R.string.settings_calendar to stringResource(R.string.calendar_hijri_note),
    )
    var query by rememberSaveable { mutableStateOf("") }

    @Composable
    fun item(r: SettingRow) {
        val t = r.toggle
        if (t != null) SettingsSwitch(r.icon, r.title, r.value, t.first, onChange = t.second)
        else SettingsItem(r.icon, r.title, r.value, r.onClick, detail = r.detail, trailing = r.trailing)
    }
    @Composable
    fun groups(of: SettingsPage) {
        rows.filter { it.page == of && it.listed }.groupBy { it.group }.forEach { (group, list) ->
            SettingsSection(if (group <= 0) "" else stringResource(group), footer = footers[group]) {
                list.forEachIndexed { i, r -> if (i > 0) SettingsDivider(); item(r) }
            }
        }
    }

    // A page of Settings opens on top of the first, as on iOS; Back (the chevron, the system's back) returns.
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }
    androidx.compose.animation.AnimatedContent(
        targetState = page, label = "settingsPage",
        transitionSpec = {
            val forward = targetState != SettingsPage.ROOT
            val dir = if (forward) androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start else androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End
            (slideIntoContainer(dir, androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Standard, easing = com.noor.app.ui.components.NoorMotion.Ease)) { it / 5 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Standard))) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(com.noor.app.ui.components.NoorMotion.Quick))
        },
    ) { shown ->
        when (shown) {
            SettingsPage.ROOT -> NoorPage(stringResource(R.string.tab_settings), contentPadding) {
                NoorSearchField(query, { query = it }, stringResource(R.string.settings_search))
                val q = query.trim()
                if (q.isNotEmpty()) {
                    // Every word typed must appear in the row's title, value, group, page or keywords (in the current
                    // language). A row from another page says which page it is on (a breadcrumb), and works from here.
                    val words = q.lowercase().split(Regex("\\s+"))
                    val pageName = mapOf(SettingsPage.TIMES to stringResource(R.string.settings_calculation), SettingsPage.LANGUAGE to stringResource(R.string.settings_language))
                    val home = stringResource(R.string.scenery_title)
                    val found = rows.filter { r ->
                        val hay = listOf(r.title, r.value.orEmpty(), r.detail.orEmpty(), if (r.group <= 0) "" else stringResource(r.group), pageName[r.page].orEmpty(), r.keywords).joinToString(" ").lowercase()
                        words.all { it in hay }
                    }
                    if (found.isEmpty()) Text(stringResource(R.string.settings_no_results, q), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    else SettingsSection(stringResource(R.string.settings_results)) {
                        found.forEachIndexed { i, r ->
                            if (i > 0) SettingsDivider()
                            val where = pageName[r.page] ?: if (r.group == R.string.countdown_group) home else null
                            item(if (where == null) r else r.copy(value = listOfNotNull(stringResource(R.string.settings_found_in, where), r.value).joinToString(" · ")))
                        }
                    }
                } else {
                    // 0.15: the page opens on the place the times are for, in big type, with the authority they
                    // follow; the card opens the location page.
                    com.noor.app.ui.components.HeroCard(
                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.LocationOn),
                        overline = stringResource(R.string.loc_now_for), title = placeTitle,
                        subtitle = stringResource(if (s.locationMode == LocationMode.AUTO) R.string.loc_label_auto else R.string.loc_label_manual),
                        onClick = onOpenCities, onClickLabel = stringResource(R.string.change_location),
                        trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(24.dp)) },
                    )
                    groups(SettingsPage.ROOT)
                }
            }
            SettingsPage.TIMES -> NoorPage(stringResource(R.string.settings_calculation), contentPadding, onBack = { page = SettingsPage.ROOT }) {
                // What the times follow now, and on what grounds (official timetable, checked calculation...).
                com.noor.app.ui.components.HeroCard(
                    icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Verified),
                    overline = stringResource(R.string.times_follow), title = timesName, subtitle = basisText,
                )
                groups(SettingsPage.TIMES)
            }
            SettingsPage.LANGUAGE -> NoorPage(stringResource(R.string.settings_language), contentPadding, onBack = { page = SettingsPage.ROOT }) {
                groups(SettingsPage.LANGUAGE)
            }
        }
    }

    when (dialog) {
        Dialog.NONE -> Unit
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
        Dialog.OFFSETS -> OffsetsSheet(s.offsets, f, onSet = { p, m -> vm.setOffset(p, m) }, onReset = { vm.resetOffsets() }, onDismiss = { dialog = Dialog.NONE })
        Dialog.BACKGROUND -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            containerColor = c.surface, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
            title = { Text(stringResource(R.string.bg_perm_title)) },
            text = { Text(stringResource(R.string.bg_perm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = Dialog.NONE
                    // (Before Android 10 the location permission already covers the background: there is nothing to ask.)
                    if (vm.hasBackgroundPermission() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) vm.setBackgroundUpdates(true)
                    else background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.bg_perm_continue), color = c.goldText) }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.bg_perm_not_now), color = c.textSecondary) } },
        )
    }
}

/** Personal adjustments (0.14: a sheet): minutes added to or taken from each time, and Reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffsetsSheet(offsets: Map<Prayer, Int>, f: Formatter, onSet: (Prayer, Int) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val c = NoorTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    NoorSheet(onDismiss, sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.adjustments), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.adjustments_body), style = FootnoteStyle, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            for (p in Prayer.entries) {
                val m = offsets[p] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.prayerName(p), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Stepper(f.digits(if (m > 0) "+$m" else "$m"), { onSet(p, m - 1) }, { onSet(p, m + 1) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NoorButton(stringResource(R.string.reset), onReset, Modifier.weight(1f), kind = ButtonKind.Tonal)
                NoorButton(stringResource(R.string.done), { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }, Modifier.weight(1f))
            }
        }
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
