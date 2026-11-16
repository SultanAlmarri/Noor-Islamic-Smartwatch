package com.noor.app

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noor.app.data.AppSettings
import com.noor.app.data.PrayerProvider
import com.noor.app.ui.adhan.AdhanScreen
import com.noor.app.ui.adhan.MySoundsScreen
import com.noor.app.ui.adhan.PrayerAlertScreen
import com.noor.app.ui.adhan.SoundLibraryScreen
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.Page
import com.noor.app.ui.components.animationsOn
import com.noor.app.ui.components.systemAllowsAmbientMotion
import com.noor.app.ui.onboarding.OnboardingScreen
import com.noor.app.ui.opening.LocalOpening
import com.noor.app.ui.opening.Opening
import com.noor.app.ui.opening.OpeningMode
import com.noor.app.ui.opening.OpeningState
import com.noor.app.ui.opening.OpeningVeil
import com.noor.app.ui.qibla.QiblaScreen
import com.noor.app.ui.settings.AccuracyScreen
import com.noor.app.ui.settings.SettingsScreen
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.today.TodayScreen
import com.noor.app.ui.watch.WatchScreen
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import com.noor.shared.geo.OfficialTimetables
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : AppCompatActivity() {
    /** The opening (0.14): one for the activity, so the launch screen, the veil and the home screen share its clock. */
    private val opening = OpeningState()
    /** The settings as first read: with them the first real frame can be drawn, and the launch screen let go. */
    private var first by mutableStateOf<AppSettings?>(null)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The bars start as over the dusk of the launch screen (light icons); the app's frame sets them per page after.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val container = (application as NoorApplication).container
        val settingsFlow = container.settings.settings

        // The launch screen stays until the first real frame is there (no blank page in between), and then hands over
        // to the opening's veil, which draws the same dusk and the same mark where the launch screen had it.
        splash.setKeepOnScreenCondition { first == null }
        splash.setOnExitAnimationListener { provider ->
            opening.mark = runCatching {
                val v = provider.iconView
                val at = IntArray(2).also(v::getLocationInWindow)
                if (v.width > 0 && v.height > 0) Rect(at[0].toFloat(), at[1].toFloat(), (at[0] + v.width).toFloat(), (at[1] + v.height).toFloat()) else null
            }.getOrNull()
            opening.start()
            provider.remove()
        }
        lifecycleScope.launch {
            val s = settingsFlow.first()
            opening.begin(Opening.modeAtStart(animationsOn(this@MainActivity), home = s.onboardingDone && startRoute() == null))
            first = s
        }

        setContent {
            val live by settingsFlow.collectAsState(initial = null)
            val settings = live ?: first
            val s = settings ?: AppSettings()
            val daylight = rememberDaylight(s, container.timetables)
            NoorTheme(s.theme, daylight) {
                CompositionLocalProvider(LocalAmbientMotion provides systemAllowsAmbientMotion(), LocalOpening provides opening) {
                    NoorBackground {
                        if (settings != null) NoorRoot(s, opening, startRoute())
                        OpeningVeil(opening)
                    }
                }
            }
        }
    }

    /** Debug builds only: a page to open directly (`adb shell am start ... --es noor.route cities`), for the device tests. */
    private fun startRoute(): String? = if (BuildConfig.DEBUG) intent?.getStringExtra("noor.route") else null

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Back after a long while: the home screen comes in again (its sky runs to now, its times settle); no veil.
        val away = if (stoppedAt > 0L) SystemClock.elapsedRealtime() - stoppedAt else 0L
        stoppedAt = 0L
        if (Opening.replaysOnReturn(away, animationsOn(this), opening.onHome)) opening.begin(OpeningMode.FULL, veil = false, now = true)
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

internal object Routes {
    const val ONBOARDING = "onboarding"
    const val PRAYERS = "prayers"
    const val WATCH = "watch"
    const val QIBLA = "qibla"
    const val SETTINGS = "settings"
    const val CITIES = "cities"
    const val ACCURACY = "accuracy"
    const val SCENERY = "scenery"
    const val ADHAN = "adhan"
    const val ADHAN_PRAYER = "adhan/prayer/{p}"
    const val ADHAN_LIBRARY = "adhan/library"
    const val ADHAN_SOUNDS = "adhan/sounds"
    const val ADHAN_HISTORY = "adhan/history"
    fun adhanPrayer(p: Prayer) = "adhan/prayer/${p.name}"
    val tabs = listOf(PRAYERS, QIBLA, ADHAN, WATCH, SETTINGS)
}

// Tabs cross-fade (they are siblings). A page pushed from a tab comes in from the side one reads towards, and leaves
// that way: from the right in English, from the left in Arabic (0.14: it came from the right in both).
private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>
private val tabEnter: Scope.() -> EnterTransition = {
    if (targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs)
        fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) + scaleIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease), initialScale = 0.985f)
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 5 } + fadeIn(tween(NoorMotion.Standard))
}
private val tabExit: Scope.() -> ExitTransition = { fadeOut(tween(NoorMotion.Quick)) }
private val popEnter: Scope.() -> EnterTransition = { fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) }
private val popExit: Scope.() -> ExitTransition = {
    if (initialState.destination.route in Routes.tabs) fadeOut(tween(NoorMotion.Quick))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 5 } + fadeOut(tween(NoorMotion.Quick))
}

@Composable
private fun NoorRoot(s: AppSettings, opening: OpeningState, debugRoute: String?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Where the app starts is decided once. (Were it read from the settings each time, finishing the welcome page
    // would rebuild the whole navigation on the home screen, and "Choose a city instead" would never reach the list.)
    val start = remember { if (s.onboardingDone) Routes.PRAYERS else Routes.ONBOARDING }
    val route = entry?.destination?.route ?: start
    val topLevel = route in Routes.tabs
    val dark = NoorTheme.colors.isDark
    SideEffect { opening.onHome = route == Routes.PRAYERS }

    // Status bar icons: white over the Today sky, over the opening's dusk and in the dark theme, dark on light pages.
    val view = LocalView.current
    val veiled = opening.veiled
    val lightPage = !dark && !veiled && route != Routes.PRAYERS && route != Routes.ONBOARDING
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = lightPage
                isAppearanceLightNavigationBars = !dark && !veiled && route != Routes.ONBOARDING
            }
        }
    }

    LaunchedEffect(Unit) { debugRoute?.let { r -> runCatching { nav.navigate(r) } } }

    fun openTab(r: String) = nav.navigate(r) {
        popUpTo(Routes.PRAYERS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (topLevel) NoorTabBar(route, ::openTab) },
    ) { padding ->
        val content = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        NavHost(
            navController = nav, startDestination = start,
            enterTransition = tabEnter, exitTransition = tabExit, popEnterTransition = popEnter, popExitTransition = popExit,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(content,
                    onChooseCity = { nav.navigate(Routes.CITIES) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onFinished = { nav.navigate(Routes.PRAYERS) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
            }
            composable(Routes.PRAYERS) {
                // 0.14: the place on the home screen opens the location page itself (it opened Settings before), and
                // a prayer's row opens that prayer's alert.
                TodayScreen(content, onOpenLocation = { nav.navigate(Routes.CITIES) }, onOpenSource = { nav.navigate(Routes.ACCURACY) },
                    onOpenPrayer = { nav.navigate(Routes.adhanPrayer(it)) })
            }
            composable(Routes.QIBLA) { QiblaScreen(content, onOpenLocation = { nav.navigate(Routes.CITIES) }) }
            composable(Routes.ADHAN) {
                AdhanScreen(content, onOpenPrayer = { nav.navigate(Routes.adhanPrayer(it)) },
                    onOpenLibrary = { nav.navigate(Routes.ADHAN_LIBRARY) }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) },
                    onOpenHistory = { nav.navigate(Routes.ADHAN_HISTORY) }, onOpenLocation = { nav.navigate(Routes.CITIES) })
            }
            composable(Routes.ADHAN_PRAYER) { e ->
                val p = e.arguments?.getString("p")?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: Prayer.FAJR
                PrayerAlertScreen(content, p, onBack = { nav.popBackStack() }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) },
                    onOpenLibrary = { nav.navigate(Routes.ADHAN_LIBRARY) })
            }
            composable(Routes.ADHAN_LIBRARY) { SoundLibraryScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.ADHAN_SOUNDS) { MySoundsScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.ADHAN_HISTORY) { com.noor.app.ui.adhan.AlertHistoryScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.WATCH) { WatchScreen(content) }
            composable(Routes.SETTINGS) {
                SettingsScreen(content, onOpenCities = { nav.navigate(Routes.CITIES) }, onOpenAccuracy = { nav.navigate(Routes.ACCURACY) },
                    onOpenRoute = { r ->
                        // Another tab opens as a tab (keeping each tab's place); other pages open on top of Settings.
                        if (r in Routes.tabs) openTab(r) else nav.navigate(r)
                    })
            }
            composable(Routes.CITIES) {
                com.noor.app.ui.location.LocationScreen(content, onDone = {
                    // (From the welcome page there is nothing behind the location page: go on to the home screen.)
                    if (nav.previousBackStackEntry != null) nav.popBackStack() else nav.navigate(Routes.PRAYERS) { popUpTo(Routes.CITIES) { inclusive = true } }
                })
            }
            composable(Routes.ACCURACY) { AccuracyScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.SCENERY) { com.noor.app.ui.settings.SceneryScreen(content, onBack = { nav.popBackStack() }) }
        }
    }
}

/**
 * Five destinations (0.14, as a tab bar on iOS): the icon and its name, gold for the one on show and quiet for the
 * rest; nothing else is drawn round them. The chosen icon settles with a small spring, and a light tick confirms the
 * change. On a wide screen the five keep together in the middle. During the opening the bar rises into place.
 */
@Composable
internal fun NoorTabBar(route: String?, onSelect: (String) -> Unit) {
    val c = NoorTheme.colors
    val view = LocalView.current
    val opening = LocalOpening.current
    fun go(r: String) {
        if (r != route) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onSelect(r)
    }
    Column(
        Modifier.fillMaxWidth()
            .then(if (opening.arriving && opening.veil) Modifier.graphicsLayer {
                val p = opening.part(Opening.TAB_AT, Opening.RISE_MS)
                translationY = (1f - p) * size.height; alpha = p
            } else Modifier)
            .background(c.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hairline()
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).widthIn(max = Page.MaxWidth).fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 4.dp),
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
private fun RowScope.Tab(selected: Boolean, icon: Painter, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val tint by animateColorAsState(if (selected) c.gold else c.textMuted, tween(NoorMotion.Quick), label = "tabTint")
    val text by animateColorAsState(if (selected) c.goldText else c.textMuted, tween(NoorMotion.Quick), label = "tabText")
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "tabLift")
    Column(
        Modifier.weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(top = 7.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(25.dp).graphicsLayer { val k = 1f + 0.08f * lift; scaleX = k; scaleY = k })
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.1.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
