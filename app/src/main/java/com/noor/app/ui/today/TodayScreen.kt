package com.noor.app.ui.today

import androidx.compose.animation.AnimatedContent
import java.time.temporal.ChronoUnit
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectTapGestures
import com.noor.app.sky.Biome
import com.noor.app.sky.RealSky
import com.noor.app.sky.RealSkyBridge
import com.noor.app.sky.RealSkyLayer
import com.noor.app.sky.RealSkySetup
import com.noor.app.R
import com.noor.app.data.ResolvedDay
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Sky
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.PrayerDay
import com.noor.prayer.astro.SunPath
import com.noor.prayer.authority.SourceKind
import com.noor.prayer.geo.Flags
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(contentPadding: PaddingValues, onOpenLocation: () -> Unit, onOpenSource: () -> Unit, vm: TodayViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshIfStale(); vm.refreshTimetables() }
    val u = ui ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NoorTheme.colors.gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
    TodayContent(u, contentPadding, dayFor = { vm.day(u, it) }, onRefresh = vm::refresh, onOpenLocation = onOpenLocation, onOpenSource = onOpenSource)
}

/**
 * How the home screen shares its height (0.9.2): the day's six times always fit above the tab bar without scrolling
 * (at the default text size), and the sky takes whatever height is left, within limits. Tests render it on small,
 * common and large phones and fail if any prayer is cut off.
 */
internal class HomeLayout(val heroHeight: Dp, val rowHeight: Dp, val shuruqHeight: Dp, val compact: Boolean) {
    companion object {
        private val GAP = 10.dp
        fun of(viewport: Dp, top: Dp, bottom: Dp): HomeLayout {
            if (viewport == Dp.Infinity || viewport <= 0.dp) return HomeLayout(top + 330.dp, 54.dp, 44.dp, false)
            fun hero(date: Dp, row: Dp, sh: Dp) = viewport - (date + row * 5 + sh + GAP + bottom)
            val regular = hero(58.dp, 54.dp, 44.dp)
            if (regular >= top + 252.dp) return HomeLayout(regular.coerceAtMost(top + 430.dp), 54.dp, 44.dp, false)
            val compact = hero(56.dp, 48.dp, 38.dp)
            if (compact >= top + 214.dp) return HomeLayout(compact, 48.dp, 38.dp, true)
            // Very short screens: slimmer rows, and the sky header puts the name and the countdown side by side.
            return HomeLayout(hero(52.dp, 44.dp, 34.dp).coerceAtLeast(top + 150.dp), 44.dp, 34.dp, true)
        }
    }
}

/** Where things sit in the sky header, from its height (including the status bar): text on top, then the arc, then land. */
private class HeroGeometry(top: Dp, height: Dp) {
    private val inner = height - top
    /** Short headers put the prayer's name and the countdown side by side. */
    val sideBySide: Boolean = inner < 230.dp
    /** Below this height the place pill shows the place on one line, without "Chosen city" under it. */
    val slimPlace: Boolean = inner < 300.dp
    val countdownMax: Float = when { sideBySide -> 40f; inner >= 340.dp -> 72f; inner >= 300.dp -> 60f; else -> 50f }
    val titleSize: Float = when { sideBySide -> 22f; inner >= 300.dp -> 28f; else -> 25f }
    private val place: Dp = if (slimPlace) 40.dp else 48.dp
    /** First estimate of where the text ends; replaced by the measured bottom once laid out. */
    val textBottom: Dp = top + 6.dp + place + 2.dp + if (sideBySide) 54.dp else (titleSize * 1.4f).dp + (countdownMax * 1.2f).dp
    val landDepth: Dp = when { sideBySide -> 50.dp; inner >= 300.dp -> 82.dp; else -> 58.dp }
    val horizon: Dp = height - landDepth
    val ground: Dp = height - 6.dp
    /** The noon Sun sits a little below the text, never behind it, while leaving the arc some height. */
    fun arcTop(textBottom: Dp): Dp = minOf(textBottom + 16.dp, horizon - 28.dp)
}

/** The whole Today screen for a given state; stateless so previews and tests can render any date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    u: TodayUi,
    contentPadding: PaddingValues,
    dayFor: (LocalDate) -> ResolvedDay?,
    onRefresh: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenSource: () -> Unit,
    initialDate: LocalDate? = null,
    calendarOpenInitially: Boolean = false,
    /** For previews and tests: show the sky as if the Sun had been drawn to this fraction of the day. */
    previewScrub: Double? = null,
    /** For previews and tests: the moment in the sky's ambient cycle (0–1) to show when animations are off. */
    previewSkyTime: Float? = null,
) {
    val ar = isArabicUi()
    val f = Formatter(ar, u.settings.numerals, u.settings.timeFormat)
    val today = u.today
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val top = contentPadding.calculateTopPadding()

    if (today == null || u.state == null || u.place == null) {
        NoLocation(top, onOpenLocation)
        return
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val home = HomeLayout.of(maxHeight, top, contentPadding.calculateBottomPadding())
    // The real sky (0.10): the whole screen is the window. The scene is drawn behind everything by its own renderer;
    // the day's times lie on frosted glass over its lower part. Where it cannot run, the 0.9 sky fills the header.
    val skyContext = androidx.compose.ui.platform.LocalContext.current
    val realSky = RealSky.usable(skyContext, u.settings.skyGraphics) && RealSky.unusable.intValue == 0
    SkyText.real = realSky
    val bridge = remember { RealSkyBridge() }
    val pagePx = with(LocalDensity.current) { Triple(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), home.heroHeight.toPx()) }
    val glassRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val pageColor = NoorTheme.colors.background
    val realSetup = if (realSky) remember(bridge, u.settings.scenery, u.settings.seasons, u.place, pagePx, pageColor) {
        RealSkySetup(
            bridge, Biome.of(u.settings.scenery, u.place.countryCode, u.place.point.elevationM, u.place.point.latitude),
            pagePx.first, pagePx.second, pagePx.third, glassRadius, pageColor.luminance() > 0.5f, floatArrayOf(pageColor.red, pageColor.green, pageColor.blue),
            seasons = u.settings.seasons,
        )
    } else null
    if (realSky) {
        // Until the scene's first picture (a moment; longer the very first time, while the phone prepares its
        // shaders) a still, dusky sky holds its place over the page, so the white text written on the sky reads in
        // either theme. The renderer brings the scene in out of these same colours (final.frag).
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.value.toFloat() }.background(RealSky.WAITING_SKY)) {
            Box(Modifier.fillMaxSize().padding(top = home.heroHeight).background(pageColor, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
        }
        RealSkyLayer(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.value.toFloat() }) { bridge.frame.value?.invoke() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        val todayDay = remember(today, u.settings, u.place, u.profile) { dayFor(today)?.day }
        SkyHeader(u, f, todayDay, top, home.heroHeight, scroll, onOpenLocation = onOpenLocation, onRefresh = onRefresh, previewScrub = previewScrub, previewSkyTime = previewSkyTime, real = realSetup)

        // The page is the land: its nearest ridge is the page colour, so the day's times sit on the ground under the sky.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val pager = rememberPagerState(initialPage = DateWindow.pageOf(initialDate ?: today)) { DateWindow.pageCount }
            val shown = DateWindow.dateOf(pager.currentPage)
            val todayPage = DateWindow.pageOf(today)

            // Follow the calendar over midnight when the user was looking at "today".
            var lastTodayPage by rememberSaveable { mutableIntStateOf(todayPage) }
            LaunchedEffect(todayPage) {
                if (todayPage != lastTodayPage && pager.currentPage == lastTodayPage) pager.scrollToPage(todayPage)
                lastTodayPage = todayPage
            }
            // A light tick each time a new day settles, as with a physical date wheel.
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
            fun go(target: LocalDate) {
                val page = DateWindow.pageOf(target)
                scope.launch { if (abs(page - pager.currentPage) <= 3) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
            }
            fun step(delta: Int) {
                val from = if (pager.isScrollInProgress) pager.targetPage else pager.currentPage
                scope.launch { pager.animateScrollToPage((from + delta).coerceIn(0, DateWindow.pageCount - 1)) }
            }

            var calendarOpen by rememberSaveable { mutableStateOf(calendarOpenInitially) }
            DateBar(
                date = shown, isToday = shown == today, hijri = HijriDate.of(shown, u.settings.hijriAdjust), f = f,
                canPrev = shown > DateWindow.FIRST, canNext = shown < DateWindow.LAST,
                onPrev = { step(-1) }, onNext = { step(+1) }, onToday = { go(today) }, onCalendar = { calendarOpen = true },
            )
            HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 24.dp, verticalAlignment = Alignment.Top) { page ->
                val date = DateWindow.dateOf(page)
                val day = remember(date, u.settings, u.place, u.profile) { dayFor(date) }
                if (day != null) Timeline(day.day, date, isToday = date == today, u, f, home)
            }
            // Where the times come from and the method now live in Settings (0.9.2); the home screen is the day itself.
            Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 10.dp))

            if (calendarOpen) {
                val c = NoorTheme.colors
                val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { calendarOpen = false }, sheetState = sheet,
                    containerColor = c.surface, contentColor = c.textPrimary,
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.textMuted.copy(alpha = 0.3f))) },
                ) {
                    CalendarPicker(
                        selected = shown, today = today, hijriAdjust = u.settings.hijriAdjust, f = f,
                        onPick = { picked ->
                            go(picked)
                            scope.launch { sheet.hide() }.invokeOnCompletion { calendarOpen = false }
                        },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
    }
}

/**
 * The sky over the user's place right now, with the next prayer written on it. Drag sideways across it to move the Sun
 * along its path through the day: it catches on each prayer with a tick and shows its time, and springs back to now
 * when let go.
 */
@Composable
private fun SkyHeader(
    u: TodayUi, f: Formatter, todayDay: PrayerDay?, top: Dp, heroHeight: Dp, scroll: ScrollState,
    onOpenLocation: () -> Unit, onRefresh: () -> Unit, previewScrub: Double?, previewSkyTime: Float?,
    real: RealSkySetup? = null,
) {
    val st = u.state ?: return
    val place = u.place ?: return
    val today = u.today ?: return
    val c = NoorTheme.colors
    val path = remember(today, place.point, place.zone) { SunPath.of(today, place.point, place.zone) }
    val events = remember(todayDay) { todayDay?.let { d -> Prayer.entries.mapNotNull { p -> d.instant(p)?.let { p to it } } }.orEmpty() }
    // The scene changes once a minute, not with every tick of the countdown.
    val minute = u.now.truncatedTo(ChronoUnit.MINUTES)
    val next = st.next.prayer.takeIf { st.next.date == today }
    val hijriDay = HijriDate.of(today, u.settings.hijriAdjust).day
    val landSeed = remember(place.nameEn, place.point) { place.nameEn.hashCode().toLong() * 7919L + (place.point.latitude * 100).toLong() }
    val scene = remember(path, events, minute, next, hijriDay, landSeed, u.settings.scenery) {
        SkyScene(
            path, events, minute, next, hijriDay = hijriDay, southernHemisphere = place.point.latitude < 0,
            terrain = terrainFor(u.settings.scenery, place.countryCode, place.point.elevationM), seed = landSeed * 1_000_003L + today.toEpochDay(), landSeed = landSeed,
            latitude = place.point.latitude, longitude = place.point.longitude,
        )
    }
    val current by rememberUpdatedState(scene)
    val height = heroHeight
    val hero = remember(top, height) { HeroGeometry(top, height) }
    val density = LocalDensity.current
    // The text's real bottom (it depends on the font size and the language), measured once laid out.
    var textBottom by remember(hero) { mutableStateOf(hero.textBottom) }
    val layout = remember(hero, height, textBottom) {
        val arcTop = hero.arcTop(textBottom)
        SceneLayout(cloudTop = (arcTop - 8.dp) / height, arcTop = arcTop / height, horizon = hero.horizon / height, ground = hero.ground / height)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ambient = LocalAmbientMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // The moment shown in the sky: now, or wherever the user's finger has taken the Sun.
    val shown = remember { Animatable((previewScrub ?: scene.nowFraction).toFloat()) }
    var scrubbing by remember { mutableStateOf(previewScrub != null) }
    var caught by remember { mutableStateOf<Pair<Prayer, java.time.Instant>?>(null) }
    // Opening (0.9.6): the day's sky comes in. The scene fades up from the page while the Sun runs, slow at first and
    // settling gently, from the start of the current prayer's time to this moment, so the sky passes through the hours
    // just gone (sunrise into morning, dusk into night); the clouds run fast and slow to their real drift; the land
    // rises a little; the path and its prayers draw themselves. About two to three seconds, once per launch, and a
    // brief fade only if the full one was seen in the last half hour. A touch on the sky takes over at once. Only the
    // scene moves: the prayer, its time and the countdown are the real ones from the first frame.
    var intro by remember { mutableStateOf(false) }
    val opening = remember { Animatable(1f) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (ambient && previewScrub == null && !OpeningSky.played) {
            OpeningSky.played = true
            val full = OpeningSky.fullDue(context)
            val now = current.nowFraction.toFloat()
            val start = if (full) OpeningSky.startFraction(current) else now
            val ms = if (full) OpeningSky.durationMs(start, now) else OpeningSky.BRIEF_MS
            intro = true
            opening.snapTo(0f)
            shown.snapTo(start)
            try {
                launch { opening.animateTo(1f, tween(ms, easing = androidx.compose.animation.core.LinearEasing)) }
                shown.animateTo(now, tween(ms, easing = OpeningSky.Ease))
                if (full) OpeningSky.markFull(context)
            } finally {
                intro = false
            }
        }
    }
    LaunchedEffect(scene.nowFraction) { if (!scrubbing && !shown.isRunning) shown.snapTo(scene.nowFraction.toFloat()) }
    LaunchedEffect(previewScrub) {
        if (previewScrub != null) { scrubbing = true; shown.snapTo(previewScrub.toFloat()) }
        else if (scrubbing) { scrubbing = false; shown.snapTo(current.nowFraction.toFloat()) }
    }
    // Dragging the Sun (0.9.6): it follows the finger on a quick, critically damped spring (no jumps, even when the
    // finger lands far from it), and each prayer is a soft detent: near a mark the Sun slows and rests exactly on the
    // prayer's moment, then moves off gently as the finger goes on. Nothing snaps, so the sky never jumps.
    val catchPx = with(density) { 22.dp.toPx() }
    val follow = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 3800f)
    fun scrubTo(x: Float, width: Float) {
        val s = current
        val g = SkyGeometry(s, width, 0f, 0f, 0f, rtl)
        val d = SkyDetent.of(s.events.map { it to g.x(s.path.fraction(it.second)) }, x, catchPx)
        if (d.caught?.first != caught?.first) {
            caught = d.caught
            if (d.caught != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val target = d.caught?.let { s.path.fraction(it.second) } ?: g.fractionAt(d.x)
        scope.launch { shown.animateTo(target.toFloat(), follow) }
    }
    fun release() {
        scrubbing = false
        caught = null
        scope.launch {
            val now = current.nowFraction.toFloat()
            if (ambient) shown.animateTo(now, spring(dampingRatio = 0.9f, stiffness = 90f)) else shown.snapTo(now)
        }
    }

    val elevation = scene.sunElevation.roundToInt()
    val desc = stringResource(if (elevation >= 0) R.string.sky_desc_up else R.string.sky_desc_down, f.number(abs(elevation))) + " " + stringResource(R.string.sky_scrub_hint)
    val totalPx = with(density) { height.toPx() }

    Box(
        Modifier.fillMaxWidth().height(height).pointerInput(rtl) {
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    scrubbing = true
                    if (opening.value < 1f) scope.launch { opening.animateTo(1f, tween(220)) }
                    scrubTo(o.x, size.width.toFloat())
                },
                onDragEnd = { release() },
                onDragCancel = { release() },
            ) { change, _ -> change.consume(); scrubTo(change.position.x, size.width.toFloat()) }
        }.pointerInput(real != null) {
            // The real sky answers a touch: a breath of wind runs through the scene.
            if (real != null) detectTapGestures(onTap = { RealSky.gust() })
        },
    ) {
        val display = shown.value.toDouble()
        LivingSky(
            scene, layout, display = display, scrubbing = scrubbing || intro, page = c.background, description = desc,
            modifier = Modifier.fillMaxSize(), parallax = { scroll.value.toFloat() }, stillTime = previewSkyTime,
            opening = { opening.value }, real = real,
        )

        Column(
            Modifier.fillMaxWidth().padding(top = top + 6.dp).padding(horizontal = 20.dp)
                .onGloballyPositioned { c -> with(density) { textBottom = c.boundsInParent().bottom.toDp() } }
                .graphicsLayer {
                alpha = (1f - scroll.value / (totalPx * 0.5f)).coerceIn(0f, 1f)
                translationY = scroll.value * 0.3f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaceRow(u, f, onOpenLocation, onRefresh, slim = hero.slimPlace)
            Spacer(Modifier.height(2.dp))
            // The next prayer; while the Sun is being drawn along its path, the moment it stands at takes its place.
            val away by animateFloatAsState(if (scrubbing) 1f else 0f, tween(NoorMotion.Standard), label = "scrub")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - away; translationY = -12.dp.toPx() * away }, horizontalAlignment = Alignment.CenterHorizontally) {
                    // 0.9.7: − time left before a prayer; once it has come, + time since it for a while (Countdowns),
                    // then the next prayer's countdown. The notification shows the same.
                    val cd = com.noor.prayer.Countdowns.of(st, u.now, u.settings.elapsedAfterPrayer)
                    val shownAt = cd.event.at
                    val countdownText = countdownText(cd, f)
                    val label = stringResource(when { !cd.elapsed -> R.string.next_prayer; cd.event.prayer == com.noor.prayer.Prayer.SHURUQ -> R.string.since_sunrise; else -> R.string.since_prayer })
                    val title: @Composable (Boolean) -> Unit = { inline ->
                        AnimatedContent(cd.event.prayer to cd.event.date, transitionSpec = {
                            (fadeIn(tween(NoorMotion.Standard)) + slideInVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 3 }) togetherWith fadeOut(tween(NoorMotion.Quick))
                        }, label = "nextName") { (p, d) ->
                            PrayerTitle(f.prayerName(p, d), f.arabic, hero.titleSize, time = if (inline) f.timeAt(shownAt, place.zone) else null, spoken = label)
                        }
                    }
                    if (hero.sideBySide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(0.85f)) {
                            title(false)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(f.timeAt(shownAt, place.zone), style = MaterialTheme.typography.titleMedium.merge(Tabular).copy(shadow = SkyText.Shadow), color = Sky.OnSkySecondary)
                            }
                        }
                        Box(Modifier.weight(1.15f)) { Countdown(countdownText, hero.countdownMax) }
                    } else {
                        title(true)
                        Countdown(countdownText, hero.countdownMax)
                    }
                }
                if (away > 0f) {
                    val f0 = shown.value.toDouble()
                    val at = caught?.second ?: scene.instantAt(f0).plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
                    ScrubReadout(
                        at = at, prayer = caught?.first, elevation = scene.elevationAt(f0), now = u.now, zone = place.zone, date = today, f = f, countdownMax = hero.countdownMax,
                        modifier = Modifier.matchParentSize().graphicsLayer { alpha = away; translationY = 12.dp.toPx() * (1f - away) },
                    )
                }
            }
        }
    }
}

/**
 * The next prayer's name in the user's language (Kufi in Arabic), with its time beside it on the same line, so the
 * countdown and the sky get the height. [spoken] (e.g. "Next prayer") is read out first by TalkBack.
 */
@Composable
private fun PrayerTitle(name: String, arabic: Boolean, size: Float, time: String? = null, spoken: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
        heading()
        if (spoken != null) contentDescription = listOfNotNull(spoken, name, time).joinToString(", ")
    }) {
        Text(name, style = (if (arabic) MaterialTheme.typography.headlineMedium.copy(fontFamily = Kufi, fontSize = (size + 2).sp)
            else MaterialTheme.typography.headlineMedium.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)).copy(shadow = SkyText.Shadow), color = Sky.OnSky)
        if (time != null) {
            Text("  ·  ", style = MaterialTheme.typography.titleMedium.copy(shadow = SkyText.Shadow), color = Sky.OnSkyMuted)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = (size * 0.68f).sp, fontWeight = FontWeight.Normal, shadow = SkyText.Shadow),
                    color = Sky.OnSkySecondary, maxLines = 1)
            }
        }
    }
}

/**
 * Where the user has drawn the Sun to: the prayer it has caught on (or how high the Sun stands), the clock time there,
 * large, and how long ago or how far ahead that is.
 */
@Composable
private fun ScrubReadout(
    at: java.time.Instant, prayer: Prayer?, elevation: Double, now: java.time.Instant, zone: java.time.ZoneId, date: LocalDate, f: Formatter,
    countdownMax: Float, modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val deg = f.number(abs(elevation.roundToInt()))
        Text(
            (if (elevation >= 0) stringResource(R.string.sky_sun_above, deg) else stringResource(R.string.sky_sun_below, deg)).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, shadow = SkyText.Shadow), color = Sky.OnSkyMuted,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.BottomCenter) {
            if (prayer != null) PrayerTitle(f.prayerName(prayer, date), f.arabic, 28f)
        }
        val size = (countdownMax * 0.86f).sp
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(f.timeAt(at, zone), style = MaterialTheme.typography.displayLarge.merge(Tabular).copy(fontSize = size, lineHeight = size * 1.1f, letterSpacing = (-2).sp,
                fontWeight = FontWeight.Light, shadow = SkyText.Shadow), color = Sky.OnSky, maxLines = 1)
        }
        val d = Duration.between(now, at)
        Text(
            when {
                abs(d.toMinutes()) < 1 -> stringResource(R.string.now)
                d.isNegative -> stringResource(R.string.time_ago, durationText(d.negated(), f))
                else -> stringResource(R.string.today_in, durationText(d, f))
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, shadow = SkyText.Shadow), color = if (prayer != null) Sky.GoldOnSky else Sky.OnSkySecondary,
        )
    }
}

/**
 * The soft detent round each prayer mark while the Sun is dragged: within [radius] of a mark (less where marks are
 * close, so neighbouring detents meet without a gap) the finger's position is eased towards the mark; in the inner
 * part the Sun rests exactly on it (and the prayer is "caught"). The mapping is continuous and never runs backwards.
 */
internal class SkyDetent(val x: Float, val caught: Pair<Prayer, java.time.Instant>?) {
    companion object {
        private const val REST = 0.4f
        fun of(marks: List<Pair<Pair<Prayer, java.time.Instant>, Float>>, x: Float, radius: Float): SkyDetent {
            val sorted = marks.sortedBy { it.second }
            val i = sorted.indices.minByOrNull { abs(sorted[it].second - x) } ?: return SkyDetent(x, null)
            val m = sorted[i].second
            val gapLeft = if (i > 0) abs(m - sorted[i - 1].second) else Float.MAX_VALUE
            val gapRight = if (i < sorted.size - 1) abs(sorted[i + 1].second - m) else Float.MAX_VALUE
            val r = minOf(radius, gapLeft / 2f, gapRight / 2f)
            val d = x - m
            if (r <= 0f || abs(d) >= r) return SkyDetent(x, null)
            val t = abs(d) / r
            if (t <= REST) return SkyDetent(m, sorted[i].first)
            val u = (t - REST) / (1f - REST)
            return SkyDetent(m + kotlin.math.sign(d) * r * u * u, null)
        }
    }
}

/** The opening (0.9.6): once per launch; in full when none has been seen for half an hour, otherwise a brief fade. */
internal object OpeningSky {
    @Volatile var played = false
    const val BRIEF_MS = 650
    private const val FULL_AFTER_MS = 30 * 60_000L
    private const val PREFS = "noor_ui"
    private const val KEY = "opening_full_at"
    /** Moves off gently and settles slowly. */
    val Ease = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

    /** Longer for a longer run of the sky: from just under two seconds to three. */
    fun durationMs(start: Float, now: Float): Int = (1900 + 1100 * ((now - start) / 0.2f).coerceIn(0f, 1f)).toInt()

    /** Where the run starts: the beginning of the current prayer's time, but at least 50 minutes and at most 6 hours back. */
    fun startFraction(scene: SkyScene): Float {
        val now = scene.nowFraction
        val prev = scene.events.map { scene.path.fraction(it.second) }.filter { it < now }.maxOrNull() ?: (now - 3.0 / 24)
        return prev.coerceIn(now - 6.0 / 24, now - 50.0 / 1440).coerceAtLeast(0.0).toFloat()
    }

    /** Extra seconds of cloud drift during the opening: fast at first, none added by the end (so nothing jumps). */
    fun cloudLapse(p: Float): Float { val q = 1f - p.coerceIn(0f, 1f); return 45f * (1f - q * q * q) - 45f }

    fun fullDue(context: android.content.Context): Boolean = runCatching {
        System.currentTimeMillis() - context.getSharedPreferences(PREFS, 0).getLong(KEY, 0L) > FULL_AFTER_MS
    }.getOrDefault(true)

    fun markFull(context: android.content.Context) {
        runCatching { context.getSharedPreferences(PREFS, 0).edit().putLong(KEY, System.currentTimeMillis()).apply() }
    }
}

/** A soft shadow under text on the sky, so white stays crisp on the palest noon sky. */
private object SkyText {
    /** Over the real sky (0.10) clouds may stand behind the text: its shadow is deeper there. */
    var real = false
    private val soft = androidx.compose.ui.graphics.Shadow(Color(0x40081530), Offset(0f, 1.5f), 10f)
    private val deep = androidx.compose.ui.graphics.Shadow(Color(0x8C050A18), Offset(0f, 1.5f), 16f)
    val Shadow get() = if (real) deep else soft
}

/** The landscape to draw: the user's choice, or the one that fits the place (0.9.8). */
internal fun terrainFor(s: com.noor.app.data.Scenery, countryCode: String?, elevationM: Double?): Terrain = when (s) {
    com.noor.app.data.Scenery.AUTO -> Terrain.auto(countryCode, elevationM)
    com.noor.app.data.Scenery.DUNES -> Terrain.DUNES
    com.noor.app.data.Scenery.HILLS -> Terrain.HILLS
    com.noor.app.data.Scenery.MOUNTAINS -> Terrain.MOUNTAINS
    com.noor.app.data.Scenery.SEA -> Terrain.SEA
    // The real sky's sceneries (0.10), where the 0.9 sky still draws: the nearest of its own four.
    com.noor.app.data.Scenery.RAINFOREST, com.noor.app.data.Scenery.SAVANNA -> Terrain.HILLS
    com.noor.app.data.Scenery.ARCTIC, com.noor.app.data.Scenery.CANYON -> Terrain.MOUNTAINS
}

/** "−1:10:34" before a prayer, "+04:12" after it (0.9.7): a true minus sign, the same in both languages, as in the notification. */
internal fun countdownText(c: com.noor.prayer.Countdown, f: Formatter): String = (if (c.elapsed) "+" else "\u2212") + f.timer(c.duration)

/** "2:10" large, ":39" quieter; the changing digits roll into place. Fitted to the width, so it never wraps. */
@Composable
private fun Countdown(text: String, maxSp: Float) {
    val cut = text.lastIndexOf(':').takeIf { it > 0 } ?: text.length
    val main = text.substring(0, cut)
    val secs = text.substring(cut)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // About 0.56 em per digit in Plex Light with tabular figures (colons narrower); keep a margin.
        val digits = text.count { it.isDigit() } + text.count { !it.isDigit() } * 0.45f
        val fit = (maxWidth.value / (digits * 0.58f)).coerceAtMost(maxSp)
        val scale = LocalDensity.current.fontScale
        val size = (fit / scale.coerceAtLeast(1f)).coerceAtLeast(40f).sp
        val style = MaterialTheme.typography.displayLarge.merge(Tabular).copy(fontSize = size, letterSpacing = (-2.5).sp, fontWeight = FontWeight.Light,
            lineHeight = size * 1.1f, shadow = SkyText.Shadow)
        // Numbers read left to right in Arabic too; only the block's position follows the layout direction.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                RollingText(main, style.copy(color = Sky.OnSky))
                RollingText(secs, style.copy(color = Sky.OnSky.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun RollingText(text: String, style: androidx.compose.ui.text.TextStyle) {
    Row {
        text.forEachIndexed { i, ch ->
            AnimatedContent(ch, transitionSpec = {
                (slideInVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { -it / 3 } + fadeIn(tween(NoorMotion.Standard))) togetherWith
                    (slideOutVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 3 } + fadeOut(tween(NoorMotion.Quick))) using SizeTransform(clip = false)
            }, label = "digit$i") { d -> Text(d.toString(), style = style) }
        }
    }
}

/** Where the times are for: a small glass capsule on the sky (flag, place, a chevron), and refresh for live location. */
@Composable
private fun PlaceRow(u: TodayUi, f: Formatter, onOpenLocation: () -> Unit, onRefresh: () -> Unit, slim: Boolean = false) {
    val place = u.place ?: return
    val glass = Color.White.copy(alpha = 0.14f)
    val glassEdge = Color.White.copy(alpha = 0.22f)
    val label = stringResource(if (place.fromGps) R.string.loc_label_auto else R.string.loc_label_manual)
    Box(Modifier.fillMaxWidth().height(if (slim) 40.dp else 48.dp)) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 52.dp).clip(RoundedCornerShape(50)).background(glass).border(0.5.dp, glassEdge, RoundedCornerShape(50))
                .clickable(onClickLabel = stringResource(R.string.change_location), onClick = onOpenLocation)
                .heightIn(min = if (slim) 36.dp else 44.dp)
                .padding(start = 12.dp, end = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val flag = Flags.emoji(place.countryCode)
            if (flag != null) Text(flag, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            else Icon(if (place.fromGps) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = Sky.OnSky, modifier = Modifier.padding(end = 8.dp).size(16.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(if (f.arabic) place.nameAr ?: place.nameEn else place.nameEn, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, lineHeight = 18.sp),
                    color = Sky.OnSky, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!slim) Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp), color = Sky.OnSkyMuted, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Sky.OnSkySecondary, modifier = Modifier.size(18.dp))
        }
        if (place.fromGps) {
            Box(Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable(enabled = !u.refreshing, onClickLabel = stringResource(R.string.refresh_location), onClick = onRefresh),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(glass).border(0.5.dp, glassEdge, CircleShape), contentAlignment = Alignment.Center) {
                    if (u.refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = Sky.OnSky, strokeWidth = 1.8.dp)
                    else Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_location), tint = Sky.OnSky, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun DateBar(
    date: LocalDate, isToday: Boolean, hijri: HijriDate, f: Formatter, canPrev: Boolean, canNext: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onCalendar: () -> Unit,
) {
    val c = NoorTheme.colors
    val calendarLabel = stringResource(R.string.open_calendar)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    // On narrow phones the date takes its short form ("Wed 4 Nov 2026") so it stays on one line.
    val narrow = maxWidth < 380.dp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_day), canPrev, onPrev)
        // The date itself opens the calendar.
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = calendarLabel, onClick = onCalendar).padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One text, so that with large fonts the date wraps instead of being cut.
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.textPrimary)) { append(if (narrow) f.weekdayShort(date.dayOfWeek) else f.weekday(date)) }
                    append("  ")
                    withStyle(SpanStyle(color = c.textSecondary, fontWeight = FontWeight.Normal)) {
                        append(if (narrow) "${f.number(date.dayOfMonth)} ${f.monthShort(date.month)} ${f.digits(date.year.toString())}" else f.gregorianDate(date))
                    }
                }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            }
            Text(f.hijri(hijri), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.2.sp), color = c.goldText, maxLines = 1, textAlign = TextAlign.Center)
        }
        DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_day), canNext, onNext)
    }
    }
    // "Today" appears only when another date is shown.
    AnimatedVisibility(!isToday, enter = fadeIn(tween(NoorMotion.Quick)) + expandVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
        exit = fadeOut(tween(NoorMotion.Quick)) + shrinkVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease))) {
        Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
            com.noor.app.ui.components.PillButton(stringResource(R.string.today), onClick = onToday)
        }
    }
}

/** Previous/next day: a quiet round button, full-size touch target. */
@Composable
private fun DayArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (enabled) c.surface else Color.Transparent).then(if (enabled && c.isDark) Modifier.border(0.5.dp, c.separator, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (enabled) c.textSecondary else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The day as the sky passes: each prayer is a small window onto its own sky, joined by a line that fills in gold
 * from the current moment towards the next. Past moments are quiet; the current one is lit.
 */
@Composable
private fun Timeline(day: PrayerDay, date: LocalDate, isToday: Boolean, u: TodayUi, f: Formatter, home: HomeLayout) {
    val c = NoorTheme.colors
    val st = u.state
    val current = if (isToday) st?.current?.takeIf { it.date == date }?.prayer else null
    val next = if (isToday) st?.next?.takeIf { it.date == date }?.prayer else null
    val progress = if (isToday && st != null && st.current != null) {
        val total = Duration.between(st.current!!.at, st.next.at).seconds.toFloat().coerceAtLeast(1f)
        (Duration.between(st.current!!.at, u.now).seconds / total).coerceIn(0f, 1f)
    } else 0f
    val fill by animateFloatAsState(progress, tween(600), label = "lineFill")
    // In Ramadan the day turns on two moments: the end of Suhoor at Fajr and Iftar at Maghrib.
    val ramadan = HijriDate.of(date, u.settings.hijriAdjust).isRamadan
    val suhoor = stringResource(R.string.today_suhoor)
    val iftar = stringResource(R.string.today_iftar)
    Column(Modifier.fillMaxWidth()) {
        val list = Prayer.entries
        list.forEachIndexed { i, p ->
            val at = day.instant(p)
            val isCurrent = p == current && next != null
            val isNext = p == next
            val passed = isToday && !isCurrent && at != null && at.isBefore(u.now)
            // Gold fills the line from the current moment's node to the next one's, in step with the time.
            val fillBelow = if (isCurrent && list.getOrNull(i + 1) == next) (fill * 2f).coerceIn(0f, 1f) else 0f
            val fillAbove = if (isNext && list.getOrNull(i - 1) == current) (fill * 2f - 1f).coerceIn(0f, 1f) else 0f
            TimelineRow(
                p, f.prayerName(p, date), day.minutes[p]?.let { f.time(it) },
                status = listOfNotNull(
                    when {
                        isCurrent -> stringResource(R.string.now)
                        isNext && st != null -> stringResource(R.string.today_in, durationText(Duration.between(u.now, st.next.at), f))
                        else -> null
                    },
                    if (ramadan && p == Prayer.FAJR) suhoor else if (ramadan && p == Prayer.MAGHRIB) iftar else null,
                ).joinToString(" · ").ifEmpty { null },
                isCurrent = isCurrent, isNext = isNext, passed = passed,
                first = i == 0, last = i == list.size - 1,
                lineAboveDone = passed || isCurrent, lineBelowDone = passed, fillAbove = fillAbove, fillBelow = fillBelow, f = f,
                // Shuruq marks the end of Fajr's time; it is not a prayer, so its row is quieter and slimmer.
                height = if (p == Prayer.SHURUQ) home.shuruqHeight else home.rowHeight, minor = p == Prayer.SHURUQ,
            )
        }
    }
}

@Composable
private fun durationText(d: Duration, f: Formatter): String {
    val (h, m) = f.hoursMinutes(d)
    return if (h > 0) stringResource(R.string.hours_minutes, f.number(h.toInt()), f.number(m.toInt())) else stringResource(R.string.minutes_only, f.number(m.toInt()))
}

@Composable
private fun TimelineRow(
    p: Prayer, name: String, time: Pair<String, String?>?, status: String?,
    isCurrent: Boolean, isNext: Boolean, passed: Boolean, first: Boolean, last: Boolean,
    lineAboveDone: Boolean, lineBelowDone: Boolean, fillAbove: Float, fillBelow: Float, f: Formatter,
    height: Dp = 54.dp, minor: Boolean = false,
) {
    val c = NoorTheme.colors
    val fg = if (passed) c.textMuted else c.textPrimary
    val sky = PrayerSkies.of(p)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The lit row carries the colour of its own sky, strongest where the disc is.
    val litBg = Brush.horizontalGradient(
        if (rtl) listOf(Color.Transparent, sky.middle.copy(alpha = if (c.isDark) 0.30f else 0.16f)) else listOf(sky.middle.copy(alpha = if (c.isDark) 0.30f else 0.16f), Color.Transparent),
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = height).height(androidx.compose.foundation.layout.IntrinsicSize.Min).clip(RoundedCornerShape(20.dp))
            .then(if (isCurrent) Modifier.background(litBg).border(0.75.dp, Brush.horizontalGradient(
                if (rtl) listOf(Color.Transparent, sky.bottom.copy(alpha = 0.35f)) else listOf(sky.bottom.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(22.dp)) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val track = c.textMuted.copy(alpha = 0.18f)
            val done = c.textMuted.copy(alpha = 0.42f)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2; val mid = size.height / 2; val w = 1.5.dp.toPx(); val gap = (if (minor) 14 else 18).dp.toPx()
                if (!first) {
                    drawLine(if (lineAboveDone) done else track, Offset(x, -2f), Offset(x, (mid - gap).coerceAtLeast(-2f)), w, StrokeCap.Round)
                    if (fillAbove > 0f) drawLine(c.gold, Offset(x, -2f), Offset(x, -2f + (mid - gap + 2f) * fillAbove), w * 1.4f, StrokeCap.Round)
                }
                if (!last) {
                    drawLine(if (lineBelowDone) done else track, Offset(x, mid + gap), Offset(x, size.height + 2f), w, StrokeCap.Round)
                    if (fillBelow > 0f) drawLine(c.gold, Offset(x, mid + gap), Offset(x, mid + gap + (size.height + 2f - mid - gap) * fillBelow), w * 1.4f, StrokeCap.Round)
                }
            }
            SkyDisc(p, when { minor -> 24.dp; isCurrent -> 38.dp; else -> 32.dp }, passed = passed, lit = isCurrent)
            if (isNext) Box(Modifier.size(if (minor) 30.dp else 38.dp).border(1.5.dp, c.gold, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = (if (minor) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium).copy(fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Medium),
                color = if (minor && !isNext && !isCurrent) c.textSecondary else fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp), color = if (isCurrent) c.textSecondary else c.goldText)
        }
        if (time == null) Text("—", color = c.textMuted)
        else Row(verticalAlignment = Alignment.Bottom) {
            Text(time.first, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = if (minor) 18.sp else 22.sp, letterSpacing = (-0.3).sp,
                fontWeight = if (isNext || isCurrent) FontWeight.Medium else FontWeight.Light),
                color = if (isNext) c.goldText else fg)
            if (time.second != null) Text(" " + time.second, style = MaterialTheme.typography.labelMedium, color = if (passed) c.textMuted else c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** No location yet: a dawn sky and one clear action. */
@Composable
private fun NoLocation(top: Dp, onOpenLocation: () -> Unit) {
    val sky = Sky.colors(-5.0, false)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky.zenith, sky.mid, sky.horizon)))) {
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 48.dp).padding(top = top)) {
            Text(stringResource(R.string.today_set_location_title), style = MaterialTheme.typography.headlineMedium, color = Sky.OnSky)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.today_set_location_body), style = MaterialTheme.typography.bodyLarge, color = Sky.OnSkySecondary)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onOpenLocation, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky.OnSky, contentColor = Color(0xFF0C1537))) {
                Text(stringResource(R.string.set_location), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
