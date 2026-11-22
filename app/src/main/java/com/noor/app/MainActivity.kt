package com.noor.app

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.noor.app.ui.components.glassEdge
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
import com.noor.app.ui.components.pressScale
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
    /** (0.23) The page a home-screen widget's tap asks for (a [com.noor.app.widget.WidgetTap]'s name), until it is opened. */
    private var widgetOpen by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.noor.app.widget.Widgets.EXTRA_OPEN)?.let { widgetOpen = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The bars start as over the dusk of the launch screen (light icons); the app's frame sets them per page after.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val container = (application as NoorApplication).container
        val settingsFlow = container.settings.settings
        widgetOpen = intent?.getStringExtra(com.noor.app.widget.Widgets.EXTRA_OPEN)

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
            val home = s.onboardingDone && startRoute() == null
            // (0.18) The home screen's sky gets ready behind the launch screen.
            if (home) com.noor.app.sky.RealSky.prewarm(applicationContext, s)
            opening.begin(Opening.modeAtStart(animationsOn(this@MainActivity), home = home))
            first = s
        }

        setContent {
            val live by settingsFlow.collectAsState(initial = null)
            val settings = live ?: first
            val s = settings ?: AppSettings()
            val daylight = rememberDaylight(s, container.timetables)
            NoorTheme(s.theme, daylight, rememberSun(s)) {
                CompositionLocalProvider(LocalAmbientMotion provides systemAllowsAmbientMotion(), LocalOpening provides opening) {
                    NoorBackground {
                        if (settings != null) NoorRoot(s, opening, startRoute(), widgetOpen) { widgetOpen = null }
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
        // (0.22.4) Minhad on screen is a moment Android lets the countdown's service start: if Android ended it while
        // the app was closed, the notification is rebuilt and held again (the countdown with its seconds).
        if (!com.noor.app.notify.StatusCountdownService.running) com.noor.app.notify.StatusNotification.refreshAsync(this)
        if (Opening.replaysOnReturn(away, animationsOn(this), opening.onHome)) opening.begin(OpeningMode.FULL, veil = false, now = true)
    }

    /**
     * 0.18: the app's language can also be changed in Android's own settings ("App languages", Android 13+); the stored
     * choice follows it, so notifications, the notification bar, widgets and the watch speak the same language.
     */
    override fun onResume() {
        super.onResume()
        val tag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]?.language ?: ""
        val lang = com.noor.app.data.Language.entries.firstOrNull { it.tag == tag } ?: com.noor.app.data.Language.SYSTEM
        val repo = (application as NoorApplication).container.settings
        lifecycleScope.launch {
            if (repo.current().language != lang) { repo.setLanguage(lang); com.noor.alerts.AlertNotifications.ensureChannels(this@MainActivity) }
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
    // (0.22) The day's sunrise and sunset are worked out once a day (and when the settings or the timetables change),
    // not every minute on the main thread; each minute only compares the time with them.
    val date = remember(place, minute) { Instant.ofEpochSecond(minute * 60).atZone(place.zone).toLocalDate() }
    val times = remember(s, place, date, tables) {
        runCatching {
            val day = PrayerProvider.resolve(date, place, PrayerProvider.profile(s, place), s, tables).day
            day.instant(Prayer.SHURUQ) to day.instant(Prayer.MAGHRIB)
        }.getOrNull()
    }
    return remember(times, minute) {
        runCatching {
            val now = Instant.now()
            val rise = times?.first; val set = times?.second
            if (rise == null || set == null) SunPath.elevation(now, place.point) > -0.833 else !now.isBefore(rise) && now.isBefore(set)
        }.getOrNull()
    }
}

/** The Sun's height where the user is (degrees) and whether it is after noon, re-evaluated every minute: the pages' sky. */
@Composable
fun rememberSun(s: AppSettings): Pair<Double, Boolean>? {
    val place = remember(s) { PrayerProvider.place(s) } ?: return null
    val minute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() / 60_000 }
    }
    return remember(place, minute) {
        runCatching {
            val now = Instant.now()
            val e = SunPath.elevation(now, place.point)
            e to (SunPath.elevation(now.plusSeconds(600), place.point) < e)
        }.getOrNull()
    }
}

internal object Routes {
    const val ONBOARDING = "onboarding"
    const val PRAYERS = "prayers"
    const val WATCH = "watch"
    /** 0.18: Minhad Studio, the watch-face editor (from the Watch page). */
    const val WATCH_STUDIO = "watch/studio"
    const val QIBLA = "qibla"
    const val SETTINGS = "settings"
    const val CITIES = "cities"
    const val ACCURACY = "accuracy"
    const val SCENERY = "scenery"
    /** 0.24: Settings → Widgets. */
    const val WIDGETS = "widgets"
    /** Testing only (0.14): to be removed before release, with com.noor.app.testing. */
    const val TESTING = "testing"
    /** The month's timetable (ui/timetable): "timetable/now", or a month, "timetable/2026-11", "timetable/h1448-9". */
    const val TIMETABLE = "timetable/{month}"
    fun timetable(month: com.noor.app.ui.timetable.TableMonth? = null) = "timetable/${month?.arg ?: "now"}"
    const val ADHAN = "adhan"
    const val ADHAN_PRAYER = "adhan/prayer/{p}"
    const val ADHAN_LIBRARY = "adhan/library"
    const val ADHAN_SOUNDS = "adhan/sounds"
    const val ADHAN_HISTORY = "adhan/history"
    /** 0.16: the voices, for one prayer ("adhan/voice/FAJR") or every prayer ("adhan/voice/ALL"). */
    const val ADHAN_VOICE = "adhan/voice/{p}"
    fun adhanVoice(p: Prayer?) = "adhan/voice/${p?.name ?: "ALL"}"
    fun adhanPrayer(p: Prayer) = "adhan/prayer/${p.name}"
    /** 0.17: one alarm before or after a prayer ("adhan/alarm/3"); 0 = a new one. */
    const val ADHAN_ALARM = "adhan/alarm/{id}"
    fun adhanAlarm(id: Int) = "adhan/alarm/$id"
    // 0.15.2: four, in the order of use (the owner: the watch "should just be put in the settings"): the watch's page
    // opens from Settings, set up once and left.
    val tabs = listOf(PRAYERS, QIBLA, ADHAN, SETTINGS)
    /** Pages that are a task of their own, shown without the tab bar (0.15: every other page keeps it). */
    val withoutTabBar = setOf(ONBOARDING, CITIES, WATCH_STUDIO)
}

// How pages come and go (0.25), as on an iPhone. Tabs are siblings: they cross-fade, the new one settling from a touch
// smaller. A page pushed from another slides in from the side one reads towards (the right in English, the left in
// Arabic) while the page under it draws back a little way and dims; going back is the same film run backwards, so the
// system's back gesture (Android 14 and later, predictive back) can pull the page off by hand and show the one under it.
private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>
private fun Scope.betweenTabs() = targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs
private val push = tween<androidx.compose.ui.unit.IntOffset>(NoorMotion.Page, easing = NoorMotion.Glide)
private val tabEnter: Scope.() -> EnterTransition = {
    if (betweenTabs()) fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) + scaleIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease), initialScale = 0.985f)
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, push) { (it * 0.42f).toInt() } + fadeIn(tween(NoorMotion.Quick + 60))
}
private val tabExit: Scope.() -> ExitTransition = {
    if (betweenTabs()) fadeOut(tween(NoorMotion.Quick))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, push) { (it * 0.14f).toInt() } + fadeOut(tween(NoorMotion.Quick))
}
private val popEnter: Scope.() -> EnterTransition = {
    if (betweenTabs()) fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease))
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, push) { (it * 0.14f).toInt() } + fadeIn(tween(NoorMotion.Standard, easing = NoorMotion.Ease))
}
private val popExit: Scope.() -> ExitTransition = {
    if (initialState.destination.route in Routes.tabs) fadeOut(tween(NoorMotion.Quick))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, push) { (it * 0.42f).toInt() } + fadeOut(tween(NoorMotion.Quick + 60))
}

@Composable
private fun NoorRoot(s: AppSettings, opening: OpeningState, debugRoute: String?, widgetOpen: String? = null, onWidgetOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Where the app starts is decided once. (Were it read from the settings each time, finishing the welcome page
    // would rebuild the whole navigation on the home screen, and "Choose a city instead" would never reach the list.)
    val start = remember { if (s.onboardingDone) Routes.PRAYERS else Routes.ONBOARDING }
    val route = entry?.destination?.route ?: start
    // 0.15: the tab bar stays on every page of a tab, as on iOS (a page opened from a tab is still in that tab: the
    // way back to the others is always in view), except the welcome page and the location chooser, which are tasks of
    // their own (a keyboard, a map). The tab lit is the tab the page was opened from: the nearest in the back stack.
    val stack by nav.currentBackStack.collectAsState()
    val inTab = remember(stack, route) { stack.lastOrNull { it.destination.route in Routes.tabs }?.destination?.route ?: Routes.PRAYERS }
    val topLevel = route !in Routes.withoutTabBar
    val reselect = remember { androidx.compose.runtime.mutableIntStateOf(0) }
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

    fun openTab(r: String) {
        // The tab one is in, tapped again: back to its first page (as on iOS), not a new copy of it.
        if (r == inTab && route != r) { nav.popBackStack(r, inclusive = false); return }
        if (r == inTab) { reselect.intValue++; return }
        nav.navigate(r) {
            popUpTo(Routes.PRAYERS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // (0.23) A widget's tap: its page, as a tab (not before the welcome page is done).
    LaunchedEffect(widgetOpen) {
        val open = widgetOpen ?: return@LaunchedEffect
        if (s.onboardingDone) {
            val r = when (open) { "QIBLA" -> Routes.QIBLA; "ADHAN" -> Routes.ADHAN; else -> Routes.PRAYERS }
            runCatching { openTab(r) }
        }
        onWidgetOpened()
    }

    // 0.25: what is new, once, to someone who had Minhad before this edition: over the home screen, when the opening
    // has finished (never in the debug hooks' direct routes, and never after the welcome page: see setOnboardingDone).
    if (com.noor.app.ui.components.WhatsNew.due(s.onboardingDone, s.newsSeen) && route == Routes.PRAYERS && !opening.active && debugRoute == null && widgetOpen == null) {
        val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as NoorApplication
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        com.noor.app.ui.components.WhatsNewSheet { scope.launch { app.container.settings.setNewsSeen(com.noor.app.ui.components.WhatsNew.EDITION) } }
    }

    // 0.15: the tab bar floats over the page (a capsule of glass, as on iOS 26), and the pages run on under it: their
    // last line can still be scrolled clear of it.
    CompositionLocalProvider(LocalTabReselect provides reselect.intValue) {
    AppFrame(topLevel, tabBar = { NoorTabBar(inTab, ::openTab, fade = route != Routes.PRAYERS) }) { content ->
        // (Second review) A page that scrolls keeps 24 dp free over the tab bar; the home screen and the pages without
        // the bar take the frame's padding as it is.
        val page = remember(content) { PaddingValues(top = content.calculateTopPadding(), bottom = content.calculateBottomPadding() + TabBarClear) }
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
                    onOpenPrayer = { nav.navigate(Routes.adhanPrayer(it)) }, onOpenTimetable = { nav.navigate(Routes.timetable(it)) })
            }
            composable(Routes.TIMETABLE) { e ->
                com.noor.app.ui.timetable.TimetableScreen(page, com.noor.app.ui.timetable.TableMonth.parse(e.arguments?.getString("month")),
                    onBack = { nav.popBackStack() }, onOpenLocation = { nav.navigate(Routes.CITIES) })
            }
            composable(Routes.QIBLA) { QiblaScreen(page, onOpenLocation = { nav.navigate(Routes.CITIES) }) }
            composable(Routes.ADHAN) {
                AdhanScreen(page, onOpenPrayer = { nav.navigate(Routes.adhanPrayer(it)) },
                    onOpenLibrary = { nav.navigate(Routes.ADHAN_LIBRARY) }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) },
                    onOpenHistory = { nav.navigate(Routes.ADHAN_HISTORY) }, onOpenLocation = { nav.navigate(Routes.CITIES) },
                    onOpenVoice = { nav.navigate(Routes.adhanVoice(null)) }, onOpenAlarm = { nav.navigate(Routes.adhanAlarm(it)) })
            }
            composable(Routes.ADHAN_PRAYER) { e ->
                val p = e.arguments?.getString("p")?.let { n -> Prayer.entries.firstOrNull { it.name == n } } ?: Prayer.FAJR
                PrayerAlertScreen(page, p, onBack = { nav.popBackStack() }, onOpenMySounds = { nav.navigate(Routes.ADHAN_SOUNDS) },
                    onOpenLibrary = { nav.navigate(Routes.ADHAN_LIBRARY) }, onOpenVoice = { nav.navigate(Routes.adhanVoice(p)) })
            }
            composable(Routes.ADHAN_VOICE) { e ->
                val p = e.arguments?.getString("p")?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
                com.noor.app.ui.adhan.VoiceScreen(page, p, onBack = { nav.popBackStack() })
            }
            composable(Routes.ADHAN_ALARM) { e ->
                com.noor.app.ui.adhan.AlarmScreen(page, e.arguments?.getString("id")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() })
            }
            composable(Routes.ADHAN_LIBRARY) { SoundLibraryScreen(page, onBack = { nav.popBackStack() }) }
            composable(Routes.ADHAN_SOUNDS) { MySoundsScreen(page, onBack = { nav.popBackStack() }) }
            composable(Routes.ADHAN_HISTORY) { com.noor.app.ui.adhan.AlertHistoryScreen(page, onBack = { nav.popBackStack() }) }
            composable(Routes.WATCH) {
                WatchScreen(page, onOpenAdhan = { openTab(Routes.ADHAN) }, onBack = { nav.popBackStack() }, onOpenStudio = { nav.navigate(Routes.WATCH_STUDIO) })
            }
            composable(Routes.WATCH_STUDIO) { com.noor.app.ui.watch.studio.StudioScreen(content, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(page, onOpenCities = { nav.navigate(Routes.CITIES) }, onOpenAccuracy = { nav.navigate(Routes.ACCURACY) },
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
            composable(Routes.ACCURACY) { AccuracyScreen(page, onBack = { nav.popBackStack() }) }
            composable(Routes.SCENERY) { com.noor.app.ui.settings.SceneryScreen(page, onBack = { nav.popBackStack() }) }
            composable(Routes.WIDGETS) { com.noor.app.ui.settings.WidgetsScreen(page, onBack = { nav.popBackStack() }) }
            // Testing only (0.14): to be removed before release.
            composable(Routes.TESTING) {
                val vm: com.noor.app.ui.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val s = vm.settings.collectAsState().value
                val app = androidx.compose.ui.platform.LocalContext.current
                com.noor.app.testing.TestingScreen(
                    page, scenery = s?.scenery ?: com.noor.app.data.Scenery.AUTO,
                    // (Through the app's own store, not this page's view model: the demo goes on after the page has gone.)
                    setScenery = { sc -> com.noor.app.testing.Tester.launch { (app.applicationContext as NoorApplication).container.settings.setScenery(sc) } },
                    onBack = { nav.popBackStack() }, onShowHome = { openTab(Routes.PRAYERS) }, onOpenLocation = { nav.navigate(Routes.CITIES) },
                )
            }
        }
    }
    }
}

/**
 * The frame of every page: the page, and over its foot the tester's banner (testing only) and, on a tab's pages, the
 * floating tab bar. The page's bottom padding holds both, so its last line can always be scrolled clear of them, and
 * the home screen fits its day above them (0.21: the banner was laid over the page with no room kept for it, and on
 * the owner's phone it covered Isha's row and its "Now").
 */
@Composable
internal fun AppFrame(topLevel: Boolean, tabBar: @Composable () -> Unit, page: @Composable (PaddingValues) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val bars = WindowInsets.systemBars.asPaddingValues()
        val density = androidx.compose.ui.platform.LocalDensity.current
        var banner by remember { mutableStateOf(0.dp) }
        page(PaddingValues(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding() + (if (topLevel) TabBarSpace else 0.dp) + banner))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // (Testing only, 0.14: says so on every page while the tester's options change what the sky shows. Drawn
            // over the fade that the tab bar lays on the foot of the page, which reaches up past the bar.)
            Box(Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { banner = with(density) { it.height.toDp() } }) { com.noor.app.testing.TesterBanner() }
            if (topLevel) tabBar() else Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

/**
 * Counts the taps on the tab one is already on, at its first page (0.15): a page that keeps pages of its own (Settings)
 * goes back to its first on a new count, as a tab does on iOS. 0 until the first such tap.
 */
val LocalTabReselect = androidx.compose.runtime.compositionLocalOf { 0 }

/** How much of the foot of a tab's page the floating tab bar takes (over the navigation bar). */
internal val TabBarSpace = 64.dp + 10.dp + 8.dp

/**
 * (0.25, second review) What a page that scrolls keeps free over the bar besides: with [TabBarSpace]'s 8 dp, 24 dp, so
 * that its last row can be scrolled clear of the bar and of the fade over the page's foot ([TabBarFade]). The home
 * screen does not scroll and keeps its day right over the bar, so it is not given this.
 */
internal val TabBarClear = 16.dp

/**
 * (0.25, second review) The foot of a scrolling page fades into the page's own colour before it reaches the floating
 * tab bar, as what scrolls under an iPhone's bar loses itself in it: at 78% of glass the rows of a table were read
 * through the bar and ran into the tabs' names. No blur: it cannot be checked without a phone. The fade begins
 * [ABOVE] over the bar, is half there at the bar's upper edge and all but whole from its middle down.
 */
internal object TabBarFade {
    val ABOVE = 32.dp
    /** How much of the page's colour lies over the page [f] of the way from the fade's top (0) to the bar's middle (1). */
    fun alpha(f: Float): Float { val x = f.coerceIn(0f, 1f); return 0.94f * x * x * (3f - 2f * x) }
}

/**
 * Five destinations (0.15: a capsule of glass floating over the page, as the tab bar of iOS 26). The icon and its name;
 * the one on show sits in a lens of light that slides to the tab chosen, the others are quiet. A light tick confirms
 * the change. On a wide screen the capsule keeps to the middle. During the opening it rises into place.
 *
 * [fade]: the page under the bar scrolls, so its foot fades out behind the bar ([TabBarFade]); not on the home
 * screen, which fits its day above the bar and whose times stand right over it.
 */
@Composable
internal fun NoorTabBar(route: String?, onSelect: (String) -> Unit, fade: Boolean = true) {
    val c = NoorTheme.colors
    val foot = com.noor.app.ui.theme.LocalBackdrop.current.at(1f)
    val fading by animateFloatAsState(if (fade) 1f else 0f, tween(NoorMotion.Quick), label = "tabFade")
    val view = LocalView.current
    val opening = LocalOpening.current
    fun go(r: String) {
        if (r != route) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onSelect(r)
    }
    val index = Routes.tabs.indexOf(route).coerceAtLeast(0)
    val lens by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.72f, stiffness = 380f), label = "tabLens")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth()
            .then(if (opening.arriving && opening.veil) Modifier.graphicsLayer {
                val p = opening.part(Opening.TAB_AT, Opening.RISE_MS)
                translationY = (1f - p) * size.height; alpha = p
            } else Modifier)
            .drawBehind {
                if (fading <= 0f) return@drawBehind
                // From ABOVE over the bar's upper edge to the bar's middle, then the same to the foot of the screen.
                val barTop = 8.dp.toPx(); val mid = barTop + 32.dp.toPx(); val top = barTop - TabBarFade.ABOVE.toPx()
                val steps = 8
                val stops = Array(steps + 1) { i -> (i / steps.toFloat()) to foot.copy(alpha = TabBarFade.alpha(i / steps.toFloat()) * fading) }
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(*stops, startY = top, endY = mid),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, mid - top))
                drawRect(foot.copy(alpha = TabBarFade.alpha(1f) * fading), topLeft = androidx.compose.ui.geometry.Offset(0f, mid),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - mid))
            }
            .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().height(64.dp)
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(shape).background(c.chrome).glassEdge(shape, c.glassEdge, c.glassEdgeLow).padding(5.dp),
        ) {
            val w = maxWidth / Routes.tabs.size
            // The lens: a softer pane of light behind the tab on show.
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((w * lens).roundToPx(), 0) }.width(w).fillMaxHeight()
                .clip(shape).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.15f)))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Tab(route == Routes.PRAYERS, painterResource(R.drawable.ic_tab_today), stringResource(R.string.tab_today)) { go(Routes.PRAYERS) }
                Tab(route == Routes.QIBLA, painterResource(R.drawable.ic_kaaba), stringResource(R.string.tab_qibla)) { go(Routes.QIBLA) }
                Tab(route == Routes.ADHAN, rememberVectorPainter(Icons.Rounded.Mosque), stringResource(R.string.tab_adhan)) { go(Routes.ADHAN) }
                Tab(route == Routes.SETTINGS, rememberVectorPainter(Icons.Rounded.Settings), stringResource(R.string.tab_settings)) { go(Routes.SETTINGS) }
            }
        }
    }
}

@Composable
private fun RowScope.Tab(selected: Boolean, icon: Painter, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val tint by animateColorAsState(if (selected) c.gold else c.textSecondary, tween(NoorMotion.Quick), label = "tabTint")
    val text by animateColorAsState(if (selected) c.goldText else c.textSecondary, tween(NoorMotion.Quick), label = "tabText")
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "tabLift")
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).pressScale(source, 0.92f)
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = com.noor.app.ui.components.withTick(onClick))
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { val k = 1f + 0.08f * lift; scaleX = k; scaleY = k })
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
