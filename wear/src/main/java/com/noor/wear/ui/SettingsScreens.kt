package com.noor.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.prayer.AsrSchool
import com.noor.prayer.LocalSetup
import com.noor.prayer.Places
import com.noor.prayer.Prayer
import com.noor.prayer.SetupPlace
import com.noor.prayer.WatchAlertStyle
import com.noor.prayer.geo.Flags
import com.noor.prayer.geo.GazPlace
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.data.ConfigSource
import com.noor.wear.data.PhoneLink
import com.noor.wear.data.WatchState

/** Progress of a one-off action started from a button. */
enum class Progress { IDLE, WORKING, DONE, FAILED }

/** Finding the watch's own position for set-up. */
sealed interface LocateStatus {
    data object Idle : LocateStatus
    data object Working : LocateStatus
    data object NoPermission : LocateStatus
    data object Unavailable : LocateStatus
}

data class SettingsUi(
    val state: WatchState,
    val phone: PhoneLink.Phone? = null,
    val sync: Progress = Progress.IDLE,
    val versionName: String = "",
    val now: Long = System.currentTimeMillis(),
)

data class SettingsActions(
    val onSync: () -> Unit = {},
    val onUseWatch: (Boolean) -> Unit = {},
    val onLocation: () -> Unit = {},
    val onSetup: ((LocalSetup) -> LocalSetup) -> Unit = {},
    val onPrayers: () -> Unit = {},
    val onGetPhoneApp: () -> Unit = {},
    val showFace: Boolean = true,
    /** 0.18: opens a settings page ([Dest]). */
    val onOpen: (String) -> Unit = {},
    /** What the watch face row says ("Minhad Studio · In use"). */
    val faceSummary: String = "",
)

/**
 * Settings (0.18): one short page of what matters, each row saying its current state, one tap from the details:
 * the watch face, where the times come from (with Sync right here), prayer alerts, and language and numbers when the
 * watch is set up on its own. The crown or bezel scrolls it (ScalingLazyColumn's rotary support).
 */
@Composable
fun SettingsScreen(ui: SettingsUi, t: WearText, a: SettingsActions) {
    val s = ui.state
    val setup = s.setup ?: LocalSetup()
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.settings)) }

            if (a.showFace) item {
                NoorChip(stringResource(R.string.settings_face), R.drawable.ic_watch, { a.onOpen(Dest.FACES) }, secondary = a.faceSummary.ifEmpty { null }, iconTint = Tint.Indigo)
            }

            // Where the times come from: the row opens the details; Sync stays one tap away.
            if (s.source == ConfigSource.PHONE) {
                item {
                    NoorChip(stringResource(R.string.source_phone), R.drawable.ic_phone, { a.onOpen(Dest.SETTINGS_TIMES) }, iconTint = Tint.Blue,
                        secondary = s.phoneReceivedAt?.let { ago(ui.now - it, t) })
                }
                item { SyncChip(ui.sync, a.onSync) }
            } else {
                item {
                    NoorChip(stringResource(R.string.source_watch), R.drawable.ic_location, { a.onOpen(Dest.SETTINGS_TIMES) }, iconTint = Tint.Blue,
                        secondary = s.config?.let { t.placeWithFlag(it) })
                }
            }

            item {
                NoorChip(stringResource(R.string.alerts_title), R.drawable.ic_bell, { a.onOpen(Dest.SETTINGS_ALERTS) }, iconTint = Tint.Red,
                    secondary = when {
                        s.source == ConfigSource.WATCH && !setup.alertsOn -> stringResource(R.string.settings_alerts_off)
                        s.source == ConfigSource.WATCH -> styleName(setup.alertStyle)
                        else -> stringResource(R.string.settings_alerts_from_phone)
                    })
            }

            if (s.source == ConfigSource.WATCH) {
                item {
                    NoorChip(stringResource(R.string.settings_display), R.drawable.ic_language, { a.onOpen(Dest.SETTINGS_DISPLAY) }, iconTint = Tint.Teal,
                        secondary = listOf(languageName(setup.language), stringResource(if (setup.arabicDigits) R.string.display_digits_arabic else R.string.display_digits_western))
                            .joinToString(" · "))
                }
            }

            if (!s.hasPhoneConfig && ui.phone != PhoneLink.Phone.CONNECTED) {
                item { NoorChip(stringResource(R.string.get_phone_app), R.drawable.ic_phone, a.onGetPhoneApp, secondary = stringResource(R.string.get_phone_app_body), iconTint = Tint.Gray, labelLines = 2, secondaryLines = 3) }
            }

            item {
                val c = s.config
                Footnote(listOfNotNull(
                    c?.let { (if (t.arabic) it.profile.nameAr else it.profile.nameEn) },
                    stringResource(R.string.offline_note),
                    stringResource(R.string.version, ui.versionName),
                ).joinToString("\n"))
            }
        }
    }
}

@Composable
private fun SyncChip(sync: Progress, onSync: () -> Unit) = NoorChip(
    when (sync) {
        Progress.WORKING -> stringResource(R.string.sync_working)
        Progress.DONE -> stringResource(R.string.sync_done)
        Progress.FAILED -> stringResource(R.string.sync_failed)
        Progress.IDLE -> stringResource(R.string.sync_now)
    },
    R.drawable.ic_sync, onSync, enabled = sync != Progress.WORKING,
    iconTint = when (sync) { Progress.FAILED -> Tint.Orange; Progress.DONE -> Tint.Green; else -> Tint.Gray },
    secondary = if (sync == Progress.FAILED) stringResource(R.string.sync_failed_body) else null,
    labelLines = 2, secondaryLines = 4,
)

@Composable
private fun styleName(s: WatchAlertStyle) = stringResource(when (s) {
    WatchAlertStyle.ADHAN_SHORT -> R.string.alert_style_adhan
    WatchAlertStyle.CHIME -> R.string.alert_style_chime
    WatchAlertStyle.NOTIFICATION -> R.string.alert_style_notification
})

@Composable
private fun languageName(l: String) = stringResource(when (l) { "en" -> R.string.display_en; "ar" -> R.string.display_ar; else -> R.string.display_follow })

/** Where the times come from, in full: the phone or this watch, the place, Asr and the Hijri date. */
@Composable
fun TimesSettingsScreen(ui: SettingsUi, t: WearText, a: SettingsActions) {
    val s = ui.state
    val setup = s.setup ?: LocalSetup()
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.settings_times)) }
            if (s.source == ConfigSource.PHONE) {
                item { NoorChip(stringResource(R.string.source_phone), R.drawable.ic_phone, a.onSync, secondary = s.phoneReceivedAt?.let { ago(ui.now - it, t) }, iconTint = Tint.Blue) }
                item { SyncChip(ui.sync, a.onSync) }
            }
            if (s.hasPhoneConfig && s.setup?.place != null) {
                item {
                    NoorSwitch(s.source == ConfigSource.WATCH, a.onUseWatch, stringResource(R.string.use_watch_settings),
                        secondary = stringResource(R.string.use_watch_settings_body))
                }
            }
            if (s.source == ConfigSource.PHONE) {
                item { Footnote(stringResource(R.string.settings_from_phone_note)) }
            } else {
                item { NoorChip(stringResource(R.string.location), R.drawable.ic_location, a.onLocation, iconTint = Tint.Blue, secondary = s.config?.let { t.placeWithFlag(it) } ?: stringResource(R.string.location_change)) }
                item {
                    NoorChip(stringResource(R.string.asr), R.drawable.ic_prayer_asr, { a.onSetup { it.copy(asrSchool = nextAsr(it.asrSchool)) } }, iconTint = Tint.Orange,
                        secondary = when (setup.asrSchool) {
                            null -> stringResource(R.string.asr_default)
                            AsrSchool.STANDARD -> stringResource(R.string.asr_standard)
                            AsrSchool.HANAFI -> stringResource(R.string.asr_hanafi)
                        })
                }
                item {
                    NoorChip(stringResource(R.string.hijri_adjust), R.drawable.ic_calendar, { a.onSetup { it.copy(hijriAdjust = nextHijri(it.hijriAdjust)) } }, iconTint = Tint.Indigo,
                        secondary = if (setup.hijriAdjust == 0) stringResource(R.string.hijri_adjust_none)
                        else stringResource(R.string.hijri_adjust_days, t.digits((if (setup.hijriAdjust > 0) "+" else "−") + kotlin.math.abs(setup.hijriAdjust))))
                }
            }
            item {
                Footnote(listOfNotNull(s.config?.let { if (t.arabic) it.profile.nameAr else it.profile.nameEn }, stringResource(R.string.offline_note)).joinToString("\n"))
            }
        }
    }
}

private fun nextHijri(v: Int): Int = if (v >= 2) -2 else v + 1

/** Prayer alerts: set here when the watch is set up on its own; otherwise what the phone chose, and its readiness. */
@Composable
fun AlertSettingsScreen(ui: SettingsUi, t: WearText, a: SettingsActions) {
    val s = ui.state
    val setup = s.setup ?: LocalSetup()
    val list = rememberScalingLazyListState()
    val notifLauncher = rememberNotificationRequest()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.alerts_title)) }
            if (s.source == ConfigSource.WATCH) {
                item {
                    NoorSwitch(setup.alertsOn, { on ->
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) notifLauncher()
                        a.onSetup { it.copy(alertsOn = on) }
                    }, stringResource(R.string.alerts_on_watch), icon = R.drawable.ic_bell, iconTint = Tint.Red)
                }
                if (setup.alertsOn) {
                    item { NoorChip(stringResource(R.string.alert_sound), R.drawable.ic_sound, { a.onSetup { it.copy(alertStyle = nextStyle(it.alertStyle)) } }, secondary = styleName(setup.alertStyle), iconTint = Tint.Orange) }
                    item {
                        NoorChip(stringResource(R.string.alert_prayers), R.drawable.ic_prayer_dhuhr, a.onPrayers,
                            secondary = Prayer.entries.filter { it in setup.alertPrayers }.joinToString(if (t.arabic) "، " else ", ") { t.name(it) }
                                .ifEmpty { stringResource(R.string.alert_prayers_none) })
                    }
                    s.config?.let { c -> item { AlertSection(c, t) } }
                }
            } else {
                item { Footnote(stringResource(R.string.settings_alerts_phone)) }
                s.config?.takeIf { it.alerts != null }?.let { c -> item { AlertSection(c, t) } }
            }
        }
    }
}

/** Language, numbers and clock when the watch is set up on its own (otherwise they follow the phone). */
@Composable
fun DisplaySettingsScreen(ui: SettingsUi, t: WearText, a: SettingsActions) {
    val setup = ui.state.setup ?: LocalSetup()
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.settings_display)) }
            item {
                NoorChip(stringResource(R.string.display_language), R.drawable.ic_language, iconTint = Tint.Teal, onClick =
                    { a.onSetup { it.copy(language = when (it.language) { "" -> "en"; "en" -> "ar"; else -> "" }) } }, secondary = languageName(setup.language))
            }
            item {
                NoorChip(stringResource(R.string.display_digits), R.drawable.ic_numbers, { a.onSetup { it.copy(arabicDigits = !it.arabicDigits) } }, iconTint = Tint.Indigo,
                    secondary = stringResource(if (setup.arabicDigits) R.string.display_digits_arabic else R.string.display_digits_western))
            }
            item {
                NoorChip(stringResource(R.string.display_clock), R.drawable.ic_clock, iconTint = Tint.Blue, onClick =
                    { a.onSetup { it.copy(use24h = when (it.use24h) { null -> false; false -> true; true -> null }) } },
                    secondary = stringResource(when (setup.use24h) { null -> R.string.display_follow; false -> R.string.display_12; true -> R.string.display_24 }))
            }
        }
    }
}

private fun nextAsr(a: AsrSchool?): AsrSchool? = when (a) { null -> AsrSchool.STANDARD; AsrSchool.STANDARD -> AsrSchool.HANAFI; AsrSchool.HANAFI -> null }
private fun nextStyle(s: WatchAlertStyle): WatchAlertStyle = WatchAlertStyle.entries[(s.ordinal + 1) % WatchAlertStyle.entries.size]

@Composable
internal fun ago(ms: Long, t: WearText): String {
    val min = ms / 60_000L
    return when {
        min < 1 -> stringResource(R.string.updated_now)
        min < 60 -> stringResource(R.string.updated_min, t.digits(min.toString()))
        min < 48 * 60 -> stringResource(R.string.updated_hours, t.digits((min / 60).toString()))
        else -> stringResource(R.string.updated_days, t.digits((min / 1440).toString()))
    }
}

/** Which prayers alert on the watch (when it is set up on its own). */
@Composable
fun AlertPrayersScreen(setup: LocalSetup, t: WearText, onToggle: (Prayer, Boolean) -> Unit) {
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.alert_prayers)) }
            Prayer.entries.forEach { p ->
                item {
                    NoorSwitch(p in setup.alertPrayers, { onToggle(p, it) }, t.name(p), icon = com.noor.wear.complication.ComplicationBuilder.iconRes(p))
                }
            }
        }
    }
}

/** Actions on the welcome screen (shown when the watch has no settings yet). */
data class WelcomeActions(
    val onUseLocation: () -> Unit = {},
    val onChooseCity: () -> Unit = {},
    val locate: LocateStatus = LocateStatus.Idle,
    val phoneConnected: Boolean = false,
)

@Composable
fun WelcomeScreen(a: WelcomeActions) {
    // 0.25: the first screenful is whole: Minhad's mark and name, one line of what it is, and the one thing to do.
    val list = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize(), autoCentering = AutoCenteringParams(itemIndex = 0)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp), tint = Color.Unspecified)
                        Text(stringResource(R.string.welcome_title), style = Type.title.copy(fontSize = 23.sp), color = Ink,
                            modifier = Modifier.height(34.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.welcome_body), style = Type.note, color = Quiet, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp))
                    LocateChip(a.locate, a.onUseLocation)
                }
            }
            item { NoorChip(stringResource(R.string.choose_city), R.drawable.ic_city, a.onChooseCity, iconTint = Tint.Gray) }
            item { Footnote(stringResource(if (a.phoneConnected) R.string.welcome_phone_waiting else R.string.welcome_phone)) }
        }
    }
}

@Composable
internal fun LocateChip(status: LocateStatus, onClick: () -> Unit) {
    when (status) {
        LocateStatus.Working -> NoorChip(stringResource(R.string.locating), R.drawable.ic_location, {}, enabled = false, secondary = stringResource(R.string.locating_body), prominent = true)
        else -> NoorChip(
            stringResource(R.string.use_my_location), R.drawable.ic_location, onClick, secondaryLines = 3, prominent = true,
            secondary = when (status) {
                LocateStatus.NoPermission -> stringResource(R.string.location_denied)
                LocateStatus.Unavailable -> stringResource(R.string.location_unavailable)
                else -> stringResource(R.string.use_my_location_body)
            },
        )
    }
}

/** Set the place on the watch: its own position, a search, or one of the cities Noor knows best. */
@Composable
fun LocationScreen(t: WearText, locate: LocateStatus, results: List<GazPlace>?, searching: Boolean, onLocate: () -> Unit, onSearch: () -> Unit, onPick: (SetupPlace) -> Unit) {
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.location)) }
            item { LocateChip(locate, onLocate) }
            item { NoorChip(stringResource(R.string.search_city), R.drawable.ic_search, onSearch, iconTint = Tint.Gray) }
            if (searching) item { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
            if (results != null) {
                if (results.isEmpty()) item { Footnote(stringResource(R.string.search_none)) }
                results.forEach { g ->
                    item {
                        NoorChip((Flags.emoji(g.countryCode)?.let { "$it " } ?: "") + (if (t.arabic) g.nameAr ?: g.name else g.name), null,
                            { onPick(SetupPlace(g.point, g.zoneId, g.name, g.nameAr, g.countryCode, g.region, fromGps = false)) },
                            secondary = g.admin1Name.ifBlank { null })
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
            item { Caption(stringResource(R.string.cities), t) }
            Places.ALL.forEach { c ->
                item {
                    NoorChip((Flags.emoji(c.countryCode)?.let { "$it " } ?: "") + (if (t.arabic) c.nameAr else c.nameEn), null,
                        { onPick(SetupPlace(c.point, c.zoneId, c.nameEn, c.nameAr, c.countryCode, c.region, fromGps = false)) })
                }
            }
        }
    }
}
