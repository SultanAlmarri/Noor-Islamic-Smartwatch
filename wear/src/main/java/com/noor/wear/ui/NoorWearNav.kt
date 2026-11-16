package com.noor.wear.ui

import android.Manifest
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.input.RemoteInputIntentHelper
import com.noor.prayer.dhikr.TasbihState
import com.noor.wear.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/** Screens of the watch app. Tiles and complications open some of them directly (see [MainActivity.EXTRA_DEST]). */
object Dest {
    const val HOME = "home"
    const val QIBLA = "qibla"
    const val TASBIH = "tasbih"
    const val TASBIH_OPTIONS = "tasbih/options"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val ALERT_PRAYERS = "settings/prayers"
    const val LOCATION = "location"
    const val FACE = "face"

    /** Destinations that may be opened from outside the app. */
    val external = setOf(QIBLA, TASBIH, CALENDAR, SETTINGS, FACE)
}

private const val SEARCH_KEY = "noor_city_query"

@Composable
fun NoorWearNav(vm: WearViewModel, pending: String?, onPendingHandled: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tasbih by vm.tasbih.collectAsStateWithLifecycle()
    val vibrate by vm.tasbihVibrate.collectAsStateWithLifecycle()
    val phone by vm.phone.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val locate by vm.locate.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val config = state?.config
    val nav = rememberSwipeDismissableNavController()

    // A minute clock for things that only change by the day (calendar, totals).
    val today by produceState(LocalDate.now()) { while (true) { delay(60_000L); value = LocalDate.now() } }

    LaunchedEffect(pending, state != null) {
        val dest = pending ?: return@LaunchedEffect
        if (state == null) return@LaunchedEffect
        if (dest in Dest.external && (config != null || dest == Dest.FACE)) nav.navigate(dest) { launchSingleTop = true }
        onPendingHandled()
    }
    LifecycleResumeEffect(Unit) { vm.refreshPhone(); onPauseOrDispose { } }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.locate { nav.popBackStack(Dest.HOME, inclusive = false) } else vm.locationDenied()
    }
    val locateNow: () -> Unit = {
        if (vm.hasLocationPermission()) vm.locate { nav.popBackStack(Dest.HOME, inclusive = false) }
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val searchLabel = stringResource(R.string.search_city)
    val searchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(SEARCH_KEY)?.toString() }?.let(vm::search)
    }
    val startSearch: () -> Unit = {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(SEARCH_KEY).setLabel(searchLabel).build()))
        runCatching { searchLauncher.launch(intent) }
    }

    NoorWearTheme(config) { t ->
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = Dest.HOME) {
                composable(Dest.HOME) {
                    val s = state
                    when {
                        s == null -> Unit // reading storage: a few milliseconds
                        s.config == null -> WelcomeScreen(WelcomeActions(locateNow, { nav.navigate(Dest.LOCATION) }, locate, phone == com.noor.wear.data.PhoneLink.Phone.CONNECTED))
                        else -> HomeScreen(s.config, t, Instant::now, HomeExtras(tasbih?.todayOn(today) ?: 0) { nav.navigate(it) { launchSingleTop = true } })
                    }
                }
                composable(Dest.QIBLA) { config?.let { QiblaScreen(it, t) } }
                composable(Dest.TASBIH) {
                    DisposableEffect(Unit) { onDispose { vm.tasbihLeft() } }
                    tasbih?.let { initial ->
                        TasbihScreen(initial, t, TasbihControls(vibrate = vibrate, today = { LocalDate.now() }, onChange = vm::saveTasbih,
                            onOpenPresets = { nav.navigate(Dest.TASBIH_OPTIONS) }))
                    }
                }
                composable(Dest.TASBIH_OPTIONS) {
                    val s = tasbih ?: TasbihState()
                    TasbihOptionsScreen(s, t, vibrate, today,
                        onChoose = { p -> vm.saveTasbih(s.choose(p)); nav.popBackStack() },
                        onReset = { vm.saveTasbih(s.reset()); nav.popBackStack() },
                        onVibrate = vm::setTasbihVibrate)
                }
                composable(Dest.CALENDAR) { config?.let { CalendarScreen(it, t, today) } }
                composable(Dest.SETTINGS) {
                    state?.let { s ->
                        SettingsScreen(SettingsUi(s, phone, sync, vm.versionName), t, SettingsActions(
                            onSync = vm::requestSync,
                            onUseWatch = vm::useWatchSettings,
                            onLocation = { nav.navigate(Dest.LOCATION) },
                            onSetup = vm::updateSetup,
                            onPrayers = { nav.navigate(Dest.ALERT_PRAYERS) },
                            onGetPhoneApp = vm::openPhoneDownload,
                        ))
                    }
                }
                composable(Dest.ALERT_PRAYERS) {
                    state?.setup?.let { setup ->
                        AlertPrayersScreen(setup, t) { p, on -> vm.updateSetup { it.copy(alertPrayers = if (on) it.alertPrayers + p else it.alertPrayers - p) } }
                    }
                }
                composable(Dest.LOCATION) {
                    LocationScreen(t, locate, results, searching, locateNow, startSearch) { place ->
                        vm.pick(place); nav.popBackStack(Dest.HOME, inclusive = false)
                    }
                }
                composable(Dest.FACE) { FaceSetupScreen() }
            }
        }
    }
}
