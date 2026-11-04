package com.noor.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.KhatamWatermark
import com.noor.app.ui.components.Noor
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.PrayerDay
import com.noor.prayer.geo.Flags
import com.noor.prayer.authority.SourceKind
import com.noor.app.data.ResolvedDay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun TodayScreen(contentPadding: PaddingValues, onOpenLocation: () -> Unit, onOpenSource: () -> Unit, vm: TodayViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshIfStale(); vm.refreshTimetables() }
    val u = ui ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NoorTheme.colors.gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
    TodayContent(u, contentPadding, dayFor = { vm.day(u, it) }, onRefresh = vm::refresh, onOpenLocation = onOpenLocation, onOpenSource = onOpenSource)
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = Noor.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Noor.Gap),
    ) {
        Spacer(Modifier.height(2.dp))
        LocationHeader(u, f, onOpenLocation, onRefresh)

        if (today == null || u.state == null) {
            NoLocationCard(onOpenLocation)
            return@Column
        }

        NextPrayerHero(u, f)

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
            val page = (from + delta).coerceIn(0, DateWindow.pageCount - 1)
            scope.launch { pager.animateScrollToPage(page) }
        }

        var calendarOpen by rememberSaveable { mutableStateOf(calendarOpenInitially) }
        DateBar(
            date = shown, isToday = shown == today, hijri = HijriDate.of(shown, u.settings.hijriAdjust), f = f,
            canPrev = shown > DateWindow.FIRST, canNext = shown < DateWindow.LAST,
            onPrev = { step(-1) }, onNext = { step(+1) }, onToday = { go(today) }, onCalendar = { calendarOpen = true },
        )

        HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
            val date = DateWindow.dateOf(page)
            val day = remember(date, u.settings, u.place, u.profile) { dayFor(date) }
            if (day != null) DayTimes(day.day, date, isToday = date == today, u, f, official = day.official)
        }

        val shownDay = remember(shown, u.settings, u.place, u.profile) { dayFor(shown) }
        SourceCard(u, ar, official = shownDay?.official == true, f = f, onOpenSource = onOpenSource)
        Spacer(Modifier.height(8.dp))

        if (calendarOpen) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { calendarOpen = false }, sheetState = sheet,
                containerColor = c.surface, contentColor = c.textPrimary, dragHandle = null,
            ) {
                CalendarPicker(
                    selected = shown, today = today, hijriAdjust = u.settings.hijriAdjust, f = f,
                    onPick = { picked ->
                        go(picked)
                        scope.launch { sheet.hide() }.invokeOnCompletion { calendarOpen = false }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationHeader(u: TodayUi, f: Formatter, onOpenLocation: () -> Unit, onRefresh: () -> Unit) {
    val c = NoorTheme.colors
    val place = u.place
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenLocation).padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val flag = Flags.emoji(place?.countryCode)
            if (flag != null) Text(flag, fontSize = 26.sp, modifier = Modifier.padding(end = 10.dp))
            else Icon(if (place?.fromGps == true) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = c.gold,
                modifier = Modifier.padding(end = 10.dp).size(22.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    place?.let { if (f.arabic) it.nameAr ?: it.nameEn else it.nameEn } ?: stringResource(R.string.location_unknown),
                    style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (place != null) Text(
                    stringResource(if (place.fromGps) R.string.loc_label_auto else R.string.loc_label_manual),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
        if (place?.fromGps == true) {
            IconButton(onClick = onRefresh, enabled = !u.refreshing) {
                if (u.refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = c.gold, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_location), tint = c.textSecondary)
            }
        }
    }
}

@Composable
private fun NextPrayerHero(u: TodayUi, f: Formatter) {
    val c = NoorTheme.colors
    val st = u.state ?: return
    val zone = u.place!!.zone
    val until = Duration.between(u.now, st.next.at)
    val progress = st.current?.let {
        val total = Duration.between(it.at, st.next.at).seconds.toFloat().coerceAtLeast(1f)
        (Duration.between(it.at, u.now).seconds / total).coerceIn(0f, 1f)
    } ?: 0f
    val animated by animateFloatAsState(progress, tween(600), label = "heroProgress")

    GlassCard(Modifier.fillMaxWidth(), padding = 22.dp) {
        Box {
            KhatamWatermark(Modifier.align(Alignment.TopEnd).size(120.dp).padding(start = 0.dp))
            Column {
                Text(stringResource(R.string.next_prayer).uppercase(f.locale), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                AnimatedContent(st.next.prayer to st.next.date, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "nextName") { (p, d) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrayerIcon(p, 28.dp, c.gold)
                        Spacer(Modifier.width(10.dp))
                        Text(f.prayerName(p, d), style = if (f.arabic) MaterialTheme.typography.headlineMedium.copy(fontFamily = Kufi) else MaterialTheme.typography.headlineMedium,
                            color = c.textPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(f.prayerNameOther(p, d), style = MaterialTheme.typography.titleMedium.copy(fontFamily = if (f.arabic) MaterialTheme.typography.titleMedium.fontFamily else Kufi),
                            color = c.goldText)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(f.countdown(until), style = MaterialTheme.typography.displayMedium.merge(Tabular), color = c.textPrimary)
                Text(stringResource(R.string.at_time, f.timeText(st.next.at.atZone(zone).let { it.hour * 60 + it.minute })),
                    style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                Spacer(Modifier.height(16.dp))
                // Slim interval bar: from the event in progress to the next one.
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(c.textMuted.copy(alpha = 0.22f), cornerRadius = r)
                    val w = size.width * animated
                    drawRoundRect(c.gold, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height), cornerRadius = r)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(st.current?.let { f.prayerName(it.prayer, it.date) } ?: "", style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.weight(1f))
                    Text(f.prayerName(st.next.prayer, st.next.date), style = MaterialTheme.typography.labelMedium, color = c.goldText)
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
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Leading slot: a "Today" pill appears only when another date is shown.
        val todayAlpha by animateFloatAsState(if (isToday) 0f else 1f, tween(220), label = "todayBtn")
        Box(Modifier.width(64.dp).graphicsLayer { alpha = todayAlpha; scaleX = 0.85f + 0.15f * todayAlpha; scaleY = scaleX },
            contentAlignment = Alignment.CenterStart) {
            if (todayAlpha > 0.01f) Text(
                stringResource(R.string.today), style = MaterialTheme.typography.labelLarge, color = c.goldText, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.gold.copy(alpha = 0.14f))
                    .clickable(enabled = !isToday, onClick = onToday).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_day), tint = if (canPrev) c.textPrimary else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onCalendar).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Today's weekday is shown in gold; other dates in the normal colour with the "Today" pill beside them.
            Text(f.weekday(date), style = MaterialTheme.typography.titleMedium, color = if (isToday) c.goldText else c.textPrimary, maxLines = 1)
            Text(f.hijri(hijri), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
            Text(f.gregorianDate(date), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
        }
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_day), tint = if (canNext) c.textPrimary else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(30.dp))
        }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = onCalendar) { Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.open_calendar), tint = c.gold) }
        }
    }
}

@Composable
private fun DayTimes(day: PrayerDay, date: LocalDate, isToday: Boolean, u: TodayUi, f: Formatter, official: Boolean) {
    val c = NoorTheme.colors
    val st = u.state
    GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
        if (official) Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, tint = c.success, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.source_official_badge), style = MaterialTheme.typography.labelMedium, color = c.success)
        }
        Prayer.entries.forEachIndexed { i, p ->
            val minutes = day.minutes[p]
            val isCurrent = isToday && st?.current?.let { it.prayer == p && it.date == date } == true
            val isNext = isToday && st?.next?.let { it.prayer == p && it.date == date } == true
            val passed = isToday && !isCurrent && (day.instant(p)?.isBefore(u.now) ?: false)
            TimeRow(p, f.prayerName(p, date), f.prayerNameOther(p, date), minutes?.let { f.time(it) }, isCurrent, isNext, passed, f)
            if (i < Prayer.entries.size - 1) Hairline(inset = 62.dp)
        }
    }
}

@Composable
private fun TimeRow(p: Prayer, name: String, other: String, time: Pair<String, String?>?, isCurrent: Boolean, isNext: Boolean, passed: Boolean, f: Formatter) {
    val c = NoorTheme.colors
    val fg = if (passed) c.textMuted else c.textPrimary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.RowRadius))
            .then(if (isCurrent) Modifier.background(c.gold.copy(alpha = if (c.isDark) 0.12f else 0.10f)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (isCurrent || isNext) c.gold.copy(alpha = 0.16f) else c.surfaceRaised), contentAlignment = Alignment.Center) {
            PrayerIcon(p, 20.dp, if (isCurrent || isNext) c.gold else if (passed) c.textMuted else c.textSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Medium)
                if (isCurrent || isNext) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isCurrent) R.string.now else R.string.next), style = MaterialTheme.typography.labelSmall, color = c.goldText,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.gold.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(other, style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (f.arabic) MaterialTheme.typography.bodySmall.fontFamily else Kufi), color = c.textMuted)
        }
        if (time == null) Text("—", color = c.textMuted)
        else Row(verticalAlignment = Alignment.Bottom) {
            Text(time.first, style = MaterialTheme.typography.titleLarge.merge(Tabular), color = if (isNext) c.goldText else fg, fontWeight = FontWeight.Medium)
            if (time.second != null) Text(" " + time.second, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun SourceCard(u: TodayUi, ar: Boolean, official: Boolean, f: Formatter, onOpenSource: () -> Unit) {
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
    GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius)).clickable(onClick = onOpenSource), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, tint = if (official) c.success else c.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(basis, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        val offsets = u.settings.offsets.filterValues { it != 0 }
        if (offsets.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.source_user_adjusted, Prayer.entries.filter { it in offsets }.joinToString(", ") { p ->
                val v = offsets.getValue(p); f.prayerName(p) + " " + (if (v > 0) "+" else "−") + f.number(kotlin.math.abs(v))
            }), style = MaterialTheme.typography.bodySmall, color = c.goldText)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.offline_ready), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

@Composable
private fun NoLocationCard(onOpenLocation: () -> Unit) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.perm_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenLocation) { Text(stringResource(R.string.set_location), color = c.goldText) }
    }
}
