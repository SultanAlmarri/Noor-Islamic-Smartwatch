package com.noor.app

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noor.app.data.AppSettings
import com.noor.app.data.OfficialTimetables
import com.noor.app.data.PrayerProvider
import com.noor.app.ui.adhan.AdhanScreen
import com.noor.app.ui.adhan.MySoundsScreen
import com.noor.app.ui.adhan.PrayerAlertScreen
import com.noor.app.ui.adhan.SoundLibraryScreen
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.systemAllowsAmbientMotion
import com.noor.app.ui.onboarding.OnboardingScreen
import com.noor.app.ui.qibla.QiblaScreen
import com.noor.app.ui.settings.AccuracyScreen
import com.noor.app.ui.settings.CityPickerScreen
import com.noor.app.ui.settings.SettingsScreen
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.today.TodayScreen
import com.noor.app.ui.watch.WatchScreen
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import kotlinx.coroutines.delay
import java.time.Instant

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NoorApplication).container
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val s = settings ?: AppSettings()
            val daylight = rememberDaylight(s, container.timetables)
            NoorTheme(s.theme, daylight) {
                CompositionLocalProvider(LocalAmbientMotion provides systemAllowsAmbientMotion()) {
                    NoorBackground {
                        if (settings != null) NoorRoot(s)
                    }
                }
            }
        }
    }
}

/** Whether the sun is up where the user is (between today's Shuruq and Maghrib); re-evaluated every minute. */
@Composable
fun rememberDaylight(s: AppSettings, tables: OfficialTimetables?): Boolean? {
    val place = remember(s) { PrayerProvider.place(s) } ?: return null
    val minute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() / 60_000 }
    }
    return remember(s, place, minute) {
        runCatching {
            val now = Instant.now()
            val day = PrayerProvider.resolve(now.atZone(place.zone).toLocalDate(), place, PrayerProvider.profile(s, place), s, tables).day
            val rise = day.instant(Prayer.SHURUQ); val set = day.instant(Prayer.MAGHRIB)
            if (rise == null || set == null) SunPath.elevation(now, place.point) > -0.833 else !now.isBefore(rise) && now.isBefore(set)
        }.getOrNull()
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val PRAYERS = "prayers"
    const val WATCH = "watch"
    const val QIBLA = "qibla"
    const val SETTINGS = "settings"
    const val CITIES = "cities"
    const val ACCURACY = "accuracy"
    const val ADHAN = "adhan"
    const val ADHAN_PRAYER = "adhan/prayer/{p}"
    const val ADHAN_LIBRARY = "adhan/library"
    const val ADHAN_SOUNDS = "adhan/sounds"
    fun adhanPrayer(p: Prayer) = "adhan/prayer/${p.name}"
    val tabs = listOf(PRAYERS, QIBLA, ADHAN, WATCH, SETTINGS)
}

// Tabs cross-fade (they are siblings); pages pushed from a tab slide in from the reading direction.
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs)
        fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) + scaleIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease), initialScale = 0.985f)
    else slideInHorizontally(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 5 } + fadeIn(tween(NoorMotion.Standard))
}
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(tween(NoorMotion.Quick)) }
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) }
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (initialState.destination.route in Routes.tabs) fadeOut(tween(NoorMotion.Quick))
    else slideOutHorizontally(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 5 } + fadeOut(tween(NoorMotion.Quick))
}

@Composable
private fun NoorRoot(s: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = route in Routes.tabs
    val dark = NoorTheme.colors.isDark

    // Status bar icons: white over the Today sky and in the dark theme, dark on light pages.
    val view = LocalView.current
    val lightPage = !dark && route != Routes.PRAYERS && route != Routes.ONBOARDING
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = lightPage
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (topLevel) NoorTabBar(nav, route) },
    ) { padding ->
        val content = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = if (s.onboardingDone) Routes.PRAYERS else Routes.ONBOARDING,
            enterTransition = tabEnter, exitTransition = tabExit, popEnterTransition = popEnter, popExitTransition = popExit,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(content,
                    onChooseCity = { nav.navigate(Routes.CITIES) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onFinished = { nav.navigate(Routes.PRAYERS) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
            }
            composable(Routes.PRAYERS) {
                TodayScreen(content, onOpenLocation = { nav.navigate(Routes.SETTINGS) }, onOpenSource = { nav.navigate(Routes.ACCURACY) })
            }
            composable(Routes.QIBLA) { QiblaScreen(content, onOpenLocation = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.ADHAN) {
                AdhanScreen(content, onOpenPrayer = { nav.navigate(Routes.adhanPrayer(it)) },
                    onOpenLibrary = { nav.navigate(Routes.ADHAN_LIBRARY) }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) })
            }
            composable(Routes.ADHAN_PRAYER) { e ->
                val p = e.arguments?.getString("p")?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: Prayer.FAJR
                PrayerAlertScreen(content, p, onBack = { nav.popBackStack() }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) })
            }
            composable(Routes.ADHAN_LIBRARY) { SoundLibraryScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.ADHAN_SOUNDS) { MySoundsScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.WATCH) { WatchScreen(content) }
            composable(Routes.SETTINGS) {
                SettingsScreen(content, onOpenCities = { nav.navigate(Routes.CITIES) }, onOpenAccuracy = { nav.navigate(Routes.ACCURACY) })
            }
            composable(Routes.CITIES) {
                CityPickerScreen(content, onDone = {
                    if (!nav.popBackStack()) nav.navigate(Routes.PRAYERS)
                })
            }
            composable(Routes.ACCURACY) { AccuracyScreen(content, onBack = { nav.popBackStack() }) }
        }
    }
}

/** Five destinations. The selected one is gold, with a soft pill behind its icon; a light tick confirms the change. */
@Composable
private fun NoorTabBar(nav: NavHostController, route: String?) {
    val c = NoorTheme.colors
    val view = LocalView.current
    fun go(r: String) {
        if (r != route) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        nav.navigate(r) {
            popUpTo(Routes.PRAYERS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(66.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab(route == Routes.PRAYERS, painterResource(R.drawable.ic_tab_today), stringResource(R.string.tab_today)) { go(Routes.PRAYERS) }
            Tab(route == Routes.QIBLA, painterResource(R.drawable.ic_kaaba), stringResource(R.string.tab_qibla)) { go(Routes.QIBLA) }
            Tab(route == Routes.ADHAN, rememberVectorPainter(Icons.Rounded.Mosque), stringResource(R.string.tab_adhan)) { go(Routes.ADHAN) }
            Tab(route == Routes.WATCH, rememberVectorPainter(Icons.Rounded.Watch), stringResource(R.string.tab_watch)) { go(Routes.WATCH) }
            Tab(route == Routes.SETTINGS, rememberVectorPainter(Icons.Rounded.Settings), stringResource(R.string.tab_settings)) { go(Routes.SETTINGS) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(selected: Boolean, icon: Painter, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val tint by animateColorAsState(if (selected) c.gold else c.textMuted, tween(NoorMotion.Standard), label = "tabTint")
    val text by animateColorAsState(if (selected) c.goldText else c.textMuted, tween(NoorMotion.Standard), label = "tabText")
    val pill by animateColorAsState(if (selected) c.gold.copy(alpha = if (c.isDark) 0.16f else 0.14f) else Color.Transparent, tween(NoorMotion.Standard), label = "tabPill")
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(pill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

