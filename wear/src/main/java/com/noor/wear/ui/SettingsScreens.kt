package com.noor.wear.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
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
)

@Composable
fun SettingsScreen(ui: SettingsUi, t: WearText, a: SettingsActions) {
    val s = ui.state
    val setup = s.setup ?: LocalSetup()
    val list = rememberScalingLazyListState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.settings)) }

            // Where the times come from.
            if (s.source == ConfigSource.PHONE) {
                item {
                    NoorChip(stringResource(R.string.source_phone), R.drawable.ic_phone, a.onSync,
                        secondary = s.phoneReceivedAt?.let { ago(ui.now - it, t) })
                }
                item {
                    NoorChip(
                        when (ui.sync) {
                            Progress.WORKING -> stringResource(R.string.sync_working)
                            Progress.DONE -> stringResource(R.string.sync_done)
                            Progress.FAILED -> stringResource(R.string.sync_failed)
                            Progress.IDLE -> stringResource(R.string.sync_now)
                        },
                        R.drawable.ic_sync, a.onSync, enabled = ui.sync != Progress.WORKING, iconTint = Mist,
                        secondary = if (ui.sync == Progress.FAILED) stringResource(R.string.sync_failed_body) else null,
                        labelLines = 2, secondaryLines = 4,
                    )
                }
            } else {
                item {
                    NoorChip(stringResource(R.string.source_watch), R.drawable.ic_watch, a.onLocation,
                        secondary = s.config?.let { t.placeWithFlag(it) })
                }
            }
            if (s.hasPhoneConfig && s.setup?.place != null) {
                item {
                    SwitchButton(checked = s.source == ConfigSource.WATCH, onCheckedChange = a.onUseWatch, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.use_watch_settings)) },
                        secondaryLabel = { Text(stringResource(R.string.use_watch_settings_body)) })
                }
            }

            if (s.source == ConfigSource.WATCH) {
                item { NoorChip(stringResource(R.string.location), R.drawable.ic_location, a.onLocation, secondary = stringResource(R.string.location_change)) }
                item {
                    NoorChip(stringResource(R.string.asr), null, { a.onSetup { it.copy(asrSchool = nextAsr(it.asrSchool)) } },
                        secondary = when (setup.asrSchool) {
                            null -> stringResource(R.string.asr_default)
                            AsrSchool.STANDARD -> stringResource(R.string.asr_standard)
                            AsrSchool.HANAFI -> stringResource(R.string.asr_hanafi)
                        })
                }
                item { Header(stringResource(R.string.alerts_title)) }
                item {
                    SwitchButton(checked = setup.alertsOn, modifier = Modifier.fillMaxWidth(),
                        onCheckedChange = { on ->
                            if (on && android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            a.onSetup { it.copy(alertsOn = on) }
                        },
                        icon = { Icon(painterResource(R.drawable.ic_bell), null) },
                        label = { Text(stringResource(R.string.alerts_on_watch)) })
                }
                if (setup.alertsOn) {
                    item {
                        NoorChip(stringResource(R.string.alert_sound), null, { a.onSetup { it.copy(alertStyle = nextStyle(it.alertStyle)) } },
                            secondary = when (setup.alertStyle) {
                                WatchAlertStyle.ADHAN_SHORT -> stringResource(R.string.alert_style_adhan)
                                WatchAlertStyle.CHIME -> stringResource(R.string.alert_style_chime)
                                WatchAlertStyle.NOTIFICATION -> stringResource(R.string.alert_style_notification)
                            })
                    }
                    item {
                        NoorChip(stringResource(R.string.alert_prayers), null, a.onPrayers,
                            secondary = Prayer.entries.filter { it in setup.alertPrayers }.joinToString(if (t.arabic) "، " else ", ") { t.name(it) }
                                .ifEmpty { stringResource(R.string.alert_prayers_none) })
                    }
                    s.config?.let { c -> item { AlertSection(c, t) } }
                }
            }

            if (a.showFace) item { FaceSection(autoStart = false) }

            if (!s.hasPhoneConfig && ui.phone != PhoneLink.Phone.CONNECTED) {
                item { NoorChip(stringResource(R.string.get_phone_app), R.drawable.ic_phone, a.onGetPhoneApp, secondary = stringResource(R.string.get_phone_app_body), iconTint = Mist, labelLines = 2, secondaryLines = 3) }
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
                    SwitchButton(checked = p in setup.alertPrayers, onCheckedChange = { onToggle(p, it) }, modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(com.noor.wear.complication.ComplicationBuilder.iconRes(p)), null) },
                        label = { Text(t.name(p)) })
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
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(52.dp), tint = Color.Unspecified) }
            item { Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleMedium, color = Gold, textAlign = TextAlign.Center) }
            item {
                Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            item { LocateChip(a.locate, a.onUseLocation) }
            item { NoorChip(stringResource(R.string.choose_city), R.drawable.ic_city, a.onChooseCity) }
            item { Footnote(stringResource(if (a.phoneConnected) R.string.welcome_phone_waiting else R.string.welcome_phone)) }
        }
    }
}

@Composable
internal fun LocateChip(status: LocateStatus, onClick: () -> Unit) {
    when (status) {
        LocateStatus.Working -> NoorChip(stringResource(R.string.locating), null, {}, enabled = false, secondary = stringResource(R.string.locating_body))
        else -> NoorChip(
            stringResource(R.string.use_my_location), R.drawable.ic_location, onClick, secondaryLines = 3,
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
            item { NoorChip(stringResource(R.string.search_city), R.drawable.ic_search, onSearch, iconTint = Mist) }
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
            item { Header(stringResource(R.string.cities)) }
            Places.ALL.forEach { c ->
                item {
                    NoorChip((Flags.emoji(c.countryCode)?.let { "$it " } ?: "") + (if (t.arabic) c.nameAr else c.nameEn), null,
                        { onPick(SetupPlace(c.point, c.zoneId, c.nameEn, c.nameAr, c.countryCode, c.region, fromGps = false)) })
                }
            }
        }
    }
}
