package com.noor.app.ui.today

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.material3.IconButton
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
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.SkyBackdrop
import com.noor.app.ui.components.SkyScene
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

/** Heights of the sky header, measured from the bottom of the status bar. */
private object Hero {
    val Height = 400.dp
    val ArcTop = 254.dp
    val Horizon = 334.dp
    val Ground = 372.dp
    val Overlap = 24.dp
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
) {
    val c = NoorTheme.colors
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
        SkyHeader(u, f, todayDay, top, scrollPx = scroll.value, onOpenLocation = onOpenLocation, onRefresh = onRefresh)

        // The sheet: everything else sits on the page colour, rising over the bottom of the sky.
        Column(
            Modifier.offset(y = -Hero.Overlap).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.background)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
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
            Spacer(Modifier.height(6.dp))
            HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 24.dp, verticalAlignment = Alignment.Top) { page ->
                val date = DateWindow.dateOf(page)
                val day = remember(date, u.settings, u.place, u.profile) { dayFor(date) }
                if (day != null) Timeline(day.day, date, isToday = date == today, u, f, official = day.official)
            }
            val shownDay = remember(shown, u.settings, u.place, u.profile) { dayFor(shown) }
            Spacer(Modifier.height(10.dp))
            SourceFooter(u, ar, official = shownDay?.official == true, f = f, onOpenSource = onOpenSource)
            Spacer(Modifier.height((contentPadding.calculateBottomPadding() - Hero.Overlap + 16.dp).coerceAtLeast(0.dp)))

            if (calendarOpen) {
                val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { calendarOpen = false }, sheetState = sheet,
                    containerColor = c.surface, contentColor = c.textPrimary,
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

/** The sky over the user's place right now, with the countdown to the next prayer written on it. */
@Composable
private fun SkyHeader(u: TodayUi, f: Formatter, todayDay: PrayerDay?, top: Dp, scrollPx: Int, onOpenLocation: () -> Unit, onRefresh: () -> Unit) {
    val st = u.state ?: return
    val place = u.place ?: return
    val today = u.today ?: return
    val path = remember(today, place.point, place.zone) { SunPath.of(today, place.point, place.zone) }
    val events = remember(todayDay) { todayDay?.let { d -> Prayer.entries.mapNotNull { p -> d.instant(p)?.let { p to it } } }.orEmpty() }
    val scene = SkyScene(
        path, events, u.now, next = st.next.prayer.takeIf { st.next.date == today },
        hijriDay = HijriDate.of(today, u.settings.hijriAdjust).day, southernHemisphere = place.point.latitude < 0,
    )
    val height = top + Hero.Height
    val density = LocalDensity.current
    val total = with(density) { height.toPx() }
    val fade = (1f - scrollPx / (total * 0.55f)).coerceIn(0f, 1f)
    val elevation = scene.sunElevation.roundToInt()
    val desc = stringResource(if (elevation >= 0) R.string.sky_desc_up else R.string.sky_desc_down, f.number(abs(elevation)))

    Box(Modifier.fillMaxWidth().height(height)) {
        SkyBackdrop(
            scene,
            Modifier.fillMaxSize().graphicsLayer { translationY = scrollPx * 0.45f },
            horizon = ((top + Hero.Horizon) / height), arcTop = ((top + Hero.ArcTop) / height), ground = ((top + Hero.Ground) / height),
            description = desc,
        )
        Column(Modifier.padding(top = top + 6.dp).padding(horizontal = 20.dp).graphicsLayer { alpha = fade; translationY = scrollPx * 0.25f }) {
            PlaceRow(u, f, onOpenLocation, onRefresh)
            Spacer(Modifier.height(24.dp))
            val until = Duration.between(u.now, st.next.at)
            AnimatedContent(st.next.prayer to st.next.date, transitionSpec = {
                (fadeIn(tween(NoorMotion.Standard)) + slideInVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 3 }) togetherWith fadeOut(tween(NoorMotion.Quick))
            }, label = "nextName") { (p, d) ->
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics { heading() }) {
                    Text(f.prayerName(p, d), style = if (f.arabic) MaterialTheme.typography.headlineMedium.copy(fontFamily = Kufi, fontSize = 30.sp)
                        else MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = Sky.OnSky)
                    Spacer(Modifier.width(12.dp))
                    Text(f.prayerNameOther(p, d), style = if (f.arabic) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge.copy(fontFamily = Kufi, fontWeight = FontWeight.Normal),
                        color = Sky.GoldOnSky, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Countdown(f.countdown(until))
            Text(stringResource(R.string.at_time, f.timeAt(st.next.at, place.zone)), style = MaterialTheme.typography.bodyLarge, color = Sky.OnSkySecondary)
        }
    }
}

/** "2:10" large, ":39" quieter; the changing digits roll into place. */
@Composable
private fun Countdown(text: String) {
    val cut = text.lastIndexOf(':').takeIf { it > 0 } ?: text.length
    val main = text.substring(0, cut)
    val secs = text.substring(cut)
    // The countdown grows with the user's font size, but not past what fits on one line of a phone screen.
    val scale = LocalDensity.current.fontScale
    val size = if (scale > 1.15f) (78f * 1.15f / scale).sp else 78.sp
    val style = MaterialTheme.typography.displayLarge.merge(Tabular).copy(fontSize = size, letterSpacing = (-2).sp, fontWeight = FontWeight.Light)
    // Numbers read left to right in Arabic too; only the block's position follows the layout direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            RollingText(main, style.copy(color = Sky.OnSky))
            RollingText(secs, style.copy(color = Sky.OnSky.copy(alpha = 0.55f)))
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

@Composable
private fun PlaceRow(u: TodayUi, f: Formatter, onOpenLocation: () -> Unit, onRefresh: () -> Unit) {
    val place = u.place ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f, fill = false).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenLocation).padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val flag = Flags.emoji(place.countryCode)
            if (flag != null) Text(flag, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            else Icon(if (place.fromGps) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = Sky.OnSky, modifier = Modifier.padding(end = 10.dp).size(20.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f.arabic) place.nameAr ?: place.nameEn else place.nameEn, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Sky.OnSky, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Sky.OnSkyMuted, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(if (place.fromGps) R.string.loc_label_auto else R.string.loc_label_manual), style = MaterialTheme.typography.bodySmall, color = Sky.OnSkyMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        if (place.fromGps) {
            IconButton(onClick = onRefresh, enabled = !u.refreshing) {
                if (u.refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Sky.OnSky, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_location), tint = Sky.OnSkySecondary)
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
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_day), tint = if (canPrev) c.textSecondary else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(28.dp))
        }
        // The date itself opens the calendar.
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = calendarLabel, onClick = onCalendar).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One text, so that with large fonts the date wraps instead of being cut.
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.textPrimary)) { append(f.weekday(date)) }
                    append("  ")
                    withStyle(SpanStyle(color = c.textSecondary)) { append(f.gregorianDate(date)) }
                }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.CalendarMonth, null, tint = c.gold, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(1.dp))
            Text(f.hijri(hijri), style = MaterialTheme.typography.bodySmall, color = c.goldText, maxLines = 1, textAlign = TextAlign.Center)
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_day), tint = if (canNext) c.textSecondary else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(28.dp))
        }
    }
    // "Today" appears only when another date is shown.
    AnimatedVisibility(!isToday, enter = fadeIn(tween(NoorMotion.Quick)) + expandVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
        exit = fadeOut(tween(NoorMotion.Quick)) + shrinkVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease))) {
        Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            com.noor.app.ui.components.PillButton(stringResource(R.string.today), onClick = onToday)
        }
    }
}

/** The day as a line of moments: past ones quiet, the current one lit, the line filling in gold towards the next. */
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
        if (official) Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, tint = c.success, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.source_official_badge), style = MaterialTheme.typography.labelMedium, color = c.success)
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
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(18.dp))
            .then(if (isCurrent) Modifier.background(c.gold.copy(alpha = if (c.isDark) 0.09f else 0.08f)) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val track = c.textMuted.copy(alpha = 0.22f)
            val done = c.textMuted.copy(alpha = 0.5f)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2; val mid = size.height / 2; val w = 2.dp.toPx(); val gap = 17.dp.toPx()
                if (!first) {
                    drawLine(if (lineAboveDone) done else track, Offset(x, -2f), Offset(x, mid - gap), w, StrokeCap.Round)
                    if (fillAbove > 0f) drawLine(c.gold, Offset(x, -2f), Offset(x, -2f + (mid - gap + 2f) * fillAbove), w, StrokeCap.Round)
                }
                if (!last) {
                    drawLine(if (lineBelowDone) done else track, Offset(x, mid + gap), Offset(x, size.height + 2f), w, StrokeCap.Round)
                    if (fillBelow > 0f) drawLine(c.gold, Offset(x, mid + gap), Offset(x, mid + gap + (size.height + 2f - mid - gap) * fillBelow), w, StrokeCap.Round)
                }
            }
            val nodeBg = when { isCurrent -> c.gold; isNext -> c.background; else -> c.surface }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(nodeBg)
                    .border(if (isNext) 1.5.dp else 1.dp, if (isNext) c.gold else if (isCurrent) c.gold else c.separator, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PrayerIcon(p, 17.dp, when { isCurrent -> c.onGold; isNext -> c.gold; passed -> c.textMuted; else -> c.textSecondary })
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Medium)) { append(name) }
                append("  ")
                withStyle(SpanStyle(fontFamily = if (f.arabic) MaterialTheme.typography.bodySmall.fontFamily else Kufi, fontSize = 14.sp, color = c.textMuted, fontWeight = FontWeight.Normal)) { append(other) }
            }, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium, color = c.goldText)
        }
        if (time == null) Text("—", color = c.textMuted)
        else Row(verticalAlignment = Alignment.Bottom) {
            Text(time.first, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 22.sp, fontWeight = if (isNext || isCurrent) FontWeight.Medium else FontWeight.Normal),
                color = if (isNext) c.goldText else fg)
            if (time.second != null) Text(" " + time.second, style = MaterialTheme.typography.labelMedium, color = if (passed) c.textMuted else c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.width(6.dp))
    }
}

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
    Hairline()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenSource).padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Verified, null, tint = if (official) c.success else c.gold, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(basis, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            val offsets = u.settings.offsets.filterValues { it != 0 }
            if (offsets.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(stringResource(R.string.source_user_adjusted, Prayer.entries.filter { it in offsets }.joinToString(", ") { p ->
                    val v = offsets.getValue(p); f.prayerName(p) + " " + (if (v > 0) "+" else "−") + f.number(abs(v))
                }), style = MaterialTheme.typography.bodySmall, color = c.goldText)
            }
            Spacer(Modifier.height(3.dp))
            Text(stringResource(R.string.offline_ready), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
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
