package com.noor.app.ui.today

import androidx.compose.animation.AnimatedContent
import java.time.temporal.ChronoUnit
import androidx.compose.ui.layout.layout
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

/** Heights in the sky header, measured from the bottom of the status bar; a little tighter on short screens. */
private class Hero(
    val height: Dp,
    /** Clouds stay below this line, clear of the countdown. */
    val cloudTop: Dp,
    /** The Sun at its highest. */
    val arcTop: Dp,
    val horizon: Dp,
    /** The Sun at its lowest (midnight), under the land. */
    val ground: Dp,
    /** Largest size of the countdown, in sp. */
    val countdownMax: Float,
    val gap: Dp,
) {
    companion object {
        val Regular = Hero(472.dp, 262.dp, 290.dp, 388.dp, 452.dp, 76f, 18.dp)
        val Compact = Hero(400.dp, 220.dp, 240.dp, 326.dp, 386.dp, 58f, 8.dp)
    }
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

    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        val todayDay = remember(today, u.settings, u.place, u.profile) { dayFor(today)?.day }
        SkyHeader(u, f, todayDay, top, scroll, onOpenLocation = onOpenLocation, onRefresh = onRefresh, previewScrub = previewScrub, previewSkyTime = previewSkyTime)

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
            Spacer(Modifier.height(4.dp))
            HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 24.dp, verticalAlignment = Alignment.Top) { page ->
                val date = DateWindow.dateOf(page)
                val day = remember(date, u.settings, u.place, u.profile) { dayFor(date) }
                if (day != null) Timeline(day.day, date, isToday = date == today, u, f, official = day.official)
            }
            val shownDay = remember(shown, u.settings, u.place, u.profile) { dayFor(shown) }
            Spacer(Modifier.height(14.dp))
            SourceFooter(u, ar, official = shownDay?.official == true, f = f, onOpenSource = onOpenSource)
            Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 20.dp))

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

/**
 * The sky over the user's place right now, with the next prayer written on it. Drag sideways across it to move the Sun
 * along its path through the day: it catches on each prayer with a tick and shows its time, and springs back to now
 * when let go.
 */
@Composable
private fun SkyHeader(
    u: TodayUi, f: Formatter, todayDay: PrayerDay?, top: Dp, scroll: ScrollState,
    onOpenLocation: () -> Unit, onRefresh: () -> Unit, previewScrub: Double?, previewSkyTime: Float?,
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
    val scene = remember(path, events, minute, next, hijriDay, landSeed) {
        SkyScene(
            path, events, minute, next, hijriDay = hijriDay, southernHemisphere = place.point.latitude < 0,
            terrain = Terrain.of(place.countryCode), seed = landSeed * 1_000_003L + today.toEpochDay(), landSeed = landSeed,
        )
    }
    val current by rememberUpdatedState(scene)
    val hero = if (LocalConfiguration.current.screenHeightDp < 800) Hero.Compact else Hero.Regular
    val height = top + hero.height
    val layout = remember(top, hero) {
        SceneLayout(cloudTop = (top + hero.cloudTop) / height, arcTop = (top + hero.arcTop) / height, horizon = (top + hero.horizon) / height, ground = (top + hero.ground) / height)
    }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ambient = LocalAmbientMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // The moment shown in the sky: now, or wherever the user's finger has taken the Sun.
    val shown = remember { Animatable((previewScrub ?: scene.nowFraction).toFloat()) }
    var scrubbing by remember { mutableStateOf(previewScrub != null) }
    var caught by remember { mutableStateOf<Pair<Prayer, java.time.Instant>?>(null) }
    LaunchedEffect(scene.nowFraction) { if (!scrubbing && !shown.isRunning) shown.snapTo(scene.nowFraction.toFloat()) }
    LaunchedEffect(previewScrub) {
        if (previewScrub != null) { scrubbing = true; shown.snapTo(previewScrub.toFloat()) }
        else if (scrubbing) { scrubbing = false; shown.snapTo(current.nowFraction.toFloat()) }
    }
    val catchPx = with(density) { 18.dp.toPx() }
    fun scrubTo(x: Float, width: Float) {
        val s = current
        val g = SkyGeometry(s, width, 0f, 0f, 0f, rtl)
        val hit = s.events.minByOrNull { abs(g.x(s.path.fraction(it.second)) - x) }?.takeIf { abs(g.x(s.path.fraction(it.second)) - x) < catchPx }
        if (hit?.first != caught?.first) {
            caught = hit
            if (hit != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val target = hit?.let { s.path.fraction(it.second) } ?: g.fractionAt(x)
        scope.launch { shown.snapTo(target.toFloat()) }
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
                onDragStart = { o -> scrubbing = true; scrubTo(o.x, size.width.toFloat()) },
                onDragEnd = { release() },
                onDragCancel = { release() },
            ) { change, _ -> change.consume(); scrubTo(change.position.x, size.width.toFloat()) }
        },
    ) {
        val display = shown.value.toDouble()
        LivingSky(
            scene, layout, display = display, scrubbing = scrubbing, page = c.background, description = desc,
            modifier = Modifier.fillMaxSize(), parallax = { scroll.value.toFloat() }, stillTime = previewSkyTime,
        )

        Column(
            Modifier.fillMaxWidth().padding(top = top + 6.dp).padding(horizontal = 20.dp).graphicsLayer {
                alpha = (1f - scroll.value / (totalPx * 0.5f)).coerceIn(0f, 1f)
                translationY = scroll.value * 0.3f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaceRow(u, f, onOpenLocation, onRefresh)
            Spacer(Modifier.height(hero.gap))
            // The next prayer; while the Sun is being drawn along its path, the moment it stands at takes its place.
            val away by animateFloatAsState(if (scrubbing) 1f else 0f, tween(NoorMotion.Standard), label = "scrub")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - away; translationY = -12.dp.toPx() * away }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val until = Duration.between(u.now, st.next.at)
                    Text(stringResource(R.string.next_prayer).uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, shadow = SkyText.Shadow),
                        color = Sky.OnSkyMuted)
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(st.next.prayer to st.next.date, transitionSpec = {
                        (fadeIn(tween(NoorMotion.Standard)) + slideInVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 3 }) togetherWith fadeOut(tween(NoorMotion.Quick))
                    }, label = "nextName") { (p, d) -> PrayerTitle(f.prayerName(p, d), f.prayerNameOther(p, d), f.arabic) }
                    Countdown(f.countdown(until), hero.countdownMax)
                    Text(stringResource(R.string.at_time, f.timeAt(st.next.at, place.zone)), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, shadow = SkyText.Shadow),
                        color = Sky.OnSkySecondary)
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

/** The next prayer's name, with its name in the other language beside it in gold. */
@Composable
private fun PrayerTitle(name: String, other: String, arabic: Boolean) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(name, style = (if (arabic) MaterialTheme.typography.headlineMedium.copy(fontFamily = Kufi, fontSize = 32.sp)
            else MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)).copy(shadow = SkyText.Shadow), color = Sky.OnSky)
        Spacer(Modifier.width(12.dp))
        Text(other, style = (if (arabic) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge.copy(fontFamily = Kufi, fontWeight = FontWeight.Normal)).copy(shadow = SkyText.Shadow),
            color = Sky.GoldOnSky, modifier = Modifier.padding(bottom = 3.dp))
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
            if (prayer != null) PrayerTitle(f.prayerName(prayer, date), f.prayerNameOther(prayer, date), f.arabic)
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

/** A soft shadow under text on the sky, so white stays crisp on the palest noon sky. */
private object SkyText {
    val Shadow = androidx.compose.ui.graphics.Shadow(Color(0x40081530), Offset(0f, 1.5f), 10f)
}

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
private fun PlaceRow(u: TodayUi, f: Formatter, onOpenLocation: () -> Unit, onRefresh: () -> Unit) {
    val place = u.place ?: return
    val glass = Color.White.copy(alpha = 0.14f)
    val glassEdge = Color.White.copy(alpha = 0.22f)
    val label = stringResource(if (place.fromGps) R.string.loc_label_auto else R.string.loc_label_manual)
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 52.dp).clip(RoundedCornerShape(50)).background(glass).border(0.5.dp, glassEdge, RoundedCornerShape(50))
                .clickable(onClickLabel = stringResource(R.string.change_location), onClick = onOpenLocation)
                .heightIn(min = 44.dp)
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
                Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp), color = Sky.OnSkyMuted, maxLines = 1)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_day), canPrev, onPrev)
        // The date itself opens the calendar.
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = calendarLabel, onClick = onCalendar).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One text, so that with large fonts the date wraps instead of being cut.
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.textPrimary)) { append(f.weekday(date)) }
                    append("  ")
                    withStyle(SpanStyle(color = c.textSecondary, fontWeight = FontWeight.Normal)) { append(f.gregorianDate(date)) }
                }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            }
            Text(f.hijri(hijri), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.2.sp), color = c.goldText, maxLines = 1, textAlign = TextAlign.Center)
        }
        DayArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_day), canNext, onNext)
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
private fun Timeline(day: PrayerDay, date: LocalDate, isToday: Boolean, u: TodayUi, f: Formatter, official: Boolean) {
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
        if (official) Row(
            Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp).clip(RoundedCornerShape(50)).background(c.success.copy(alpha = if (c.isDark) 0.12f else 0.10f))
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Verified, null, tint = c.success, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.source_official_badge), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp), color = c.success)
        }
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
                p, f.prayerName(p, date), f.prayerNameOther(p, date), day.minutes[p]?.let { f.time(it) },
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
    p: Prayer, name: String, other: String, time: Pair<String, String?>?, status: String?,
    isCurrent: Boolean, isNext: Boolean, passed: Boolean, first: Boolean, last: Boolean,
    lineAboveDone: Boolean, lineBelowDone: Boolean, fillAbove: Float, fillBelow: Float, f: Formatter,
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
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(22.dp))
            .then(if (isCurrent) Modifier.background(litBg).border(0.75.dp, Brush.horizontalGradient(
                if (rtl) listOf(Color.Transparent, sky.bottom.copy(alpha = 0.35f)) else listOf(sky.bottom.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(22.dp)) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val track = c.textMuted.copy(alpha = 0.18f)
            val done = c.textMuted.copy(alpha = 0.42f)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2; val mid = size.height / 2; val w = 1.5.dp.toPx(); val gap = 21.dp.toPx()
                if (!first) {
                    drawLine(if (lineAboveDone) done else track, Offset(x, -2f), Offset(x, mid - gap), w, StrokeCap.Round)
                    if (fillAbove > 0f) drawLine(c.gold, Offset(x, -2f), Offset(x, -2f + (mid - gap + 2f) * fillAbove), w * 1.4f, StrokeCap.Round)
                }
                if (!last) {
                    drawLine(if (lineBelowDone) done else track, Offset(x, mid + gap), Offset(x, size.height + 2f), w, StrokeCap.Round)
                    if (fillBelow > 0f) drawLine(c.gold, Offset(x, mid + gap), Offset(x, mid + gap + (size.height + 2f - mid - gap) * fillBelow), w * 1.4f, StrokeCap.Round)
                }
            }
            SkyDisc(p, if (isCurrent) 44.dp else 36.dp, passed = passed, lit = isCurrent)
            if (isNext) Box(Modifier.size(42.dp).border(1.5.dp, c.gold, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Medium)) { append(name) }
                append("  ")
                withStyle(SpanStyle(fontFamily = if (f.arabic) MaterialTheme.typography.bodySmall.fontFamily else Kufi, fontSize = 14.sp, color = c.textMuted, fontWeight = FontWeight.Normal)) { append(other) }
            }, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp), color = if (isCurrent) c.textSecondary else c.goldText)
        }
        if (time == null) Text("—", color = c.textMuted)
        else Row(verticalAlignment = Alignment.Bottom) {
            Text(time.first, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 23.sp, letterSpacing = (-0.3).sp,
                fontWeight = if (isNext || isCurrent) FontWeight.Medium else FontWeight.Light),
                color = if (isNext) c.goldText else fg)
            if (time.second != null) Text(" " + time.second, style = MaterialTheme.typography.labelMedium, color = if (passed) c.textMuted else c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Where the times come from, as a quiet card at the foot of the day. */
@Composable
private fun SourceFooter(u: TodayUi, ar: Boolean, official: Boolean, f: Formatter, onOpenSource: () -> Unit) {
    val c = NoorTheme.colors
    val profile = u.profile ?: return
    val auth = u.authority
    val name = auth?.let { if (ar) it.nameAr else it.nameEn } ?: if (ar) profile.nameAr else profile.nameEn
    val basis = when {
        official -> stringResource(R.string.source_official, name)
        auth == null -> stringResource(R.string.source_manual_method)
        auth.kind == SourceKind.VALIDATED_CALCULATION -> stringResource(R.string.source_validated, name) + " " + (if (ar) profile.validationAr else profile.validationEn)
        auth.kind == SourceKind.AUTHORITY_PARAMETERS -> stringResource(R.string.source_parameters, name)
        else -> stringResource(R.string.source_recognised)
    }
    com.noor.app.ui.components.GlassCard(Modifier.fillMaxWidth().padding(horizontal = 4.dp), padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenSource).padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background((if (official) c.success else c.gold).copy(alpha = if (c.isDark) 0.14f else 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Verified, null, tint = if (official) c.success else c.gold, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, letterSpacing = 0.sp), color = c.textPrimary)
                Spacer(Modifier.height(3.dp))
                Text(basis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textSecondary)
                val offsets = u.settings.offsets.filterValues { it != 0 }
                if (offsets.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.source_user_adjusted, Prayer.entries.filter { it in offsets }.joinToString(", ") { p ->
                        val v = offsets.getValue(p); f.prayerName(p) + " " + (if (v > 0) "+" else "−") + f.number(abs(v))
                    }), style = MaterialTheme.typography.bodySmall, color = c.goldText)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.offline_ready), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp, fontWeight = FontWeight.Normal), color = c.textMuted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
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
