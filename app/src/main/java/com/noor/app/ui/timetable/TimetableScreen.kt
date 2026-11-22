package com.noor.app.ui.timetable

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.ResolvedPlace
import com.noor.app.ui.adhan.Segmented
import com.noor.app.ui.components.BackButton
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.Page
import com.noor.app.ui.components.glassEdge
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.withTick
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter
import com.noor.prayer.CalculationProfile
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.authority.Authority
import com.noor.prayer.authority.SourceKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The month's timetable: every day of a month and its six times, as a table. The owner's first brief asked for a
 * weekly or monthly view; this is it. The times are the home screen's ([Timetables.build]), a month at a time, worked
 * out off the main thread and kept ([TimetableViewModel]).
 */
@Composable
fun TimetableScreen(
    contentPadding: PaddingValues, start: TableMonth?, onBack: () -> Unit, onOpenLocation: () -> Unit = {},
    vm: TimetableViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui ?: return
    val source = remember(u) { vm.source(u) }
    val context = LocalContext.current
    val place = u.place; val profile = u.profile; val today = u.today
    if (place == null || profile == null || today == null || source == null) { NoPlace(contentPadding, onBack, onOpenLocation); return }
    TimetableContent(contentPadding, u.settings, place, profile, u.authority, today, u.next, source, start, onBack, onShare = { subject, text ->
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    })
}

/**
 * The page for a given state; stateless, so tests can render any month at any moment.
 *
 * From the top: the slim bar (back; the calendar, Gregorian or Hijri, where a page's name would be, as iOS puts such a
 * choice in its bar; Share); the month with the arrows to its neighbours, and under its name the months of the other
 * calendar it runs through; and a pane of glass holding the table, to the foot of the page: the six names stay at its
 * head while the days scroll, and a swipe sideways turns the month. Everything above the table stays where it is: the
 * month on show and the way to the next are always in view.
 */
@Composable
fun TimetableContent(
    contentPadding: PaddingValues,
    settings: AppSettings,
    place: ResolvedPlace,
    profile: CalculationProfile,
    authority: Authority?,
    today: LocalDate,
    next: Pair<LocalDate, Prayer>?,
    source: TableSource,
    start: TableMonth? = null,
    onBack: () -> Unit,
    onShare: (subject: String, text: String) -> Unit,
) {
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val f = remember(ar, settings.numerals, settings.timeFormat) { Formatter(ar, settings.numerals, settings.timeFormat) }
    val adjust = settings.hijriAdjust
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var hijri by rememberSaveable { mutableStateOf(start?.hijri ?: false) }
    // The day a calendar opens on: today when the month asked for holds it, otherwise that month's middle. Changing
    // the calendar keeps to the same days (the month of the other calendar that holds most of them).
    var anchor by rememberSaveable { mutableLongStateOf((start?.let { if (today in it.dates(adjust)) today else it.middle(adjust) } ?: today).toEpochDay()) }
    val pages = remember(hijri, adjust) { MonthPages(hijri, adjust) }
    val pager = key(hijri) { rememberPagerState(initialPage = pages.pageOf(LocalDate.ofEpochDay(anchor))) { pages.count } }
    val month = pages.at(pager.currentPage)
    val thisPage = pages.pageOf(today)
    val title = MonthWords.title(month, f)

    // A light tick each time a new month settles, as the home screen's days do.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    fun turn(to: Int) { scope.launch { pager.animateScrollToPage(to.coerceIn(0, pages.count - 1)) } }

    val placeName = if (ar) place.nameAr ?: place.nameEn else place.nameEn
    // What the times follow, by name: the method chosen, or the authority of the place (as Settings names it).
    val method = settings.profileId?.let { Profiles.byId(it) }?.let { if (ar) it.nameAr else it.nameEn }
        ?: authority?.let { if (ar) it.nameAr else it.nameEn } ?: if (ar) profile.nameAr else profile.nameEn
    val words = TimetableText.Words(stringResource(R.string.timetable_share_title), stringResource(R.string.timetable_day), stringResource(R.string.app_name))
    val pageName = stringResource(R.string.timetable_title)

    Box(Modifier.fillMaxSize().semantics { paneTitle = pageName }) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(contentPadding.calculateTopPadding()))
            // The slim bar. Nothing scrolls under it, so it needs no ground of its own.
            Box(Modifier.fillMaxWidth().heightIn(min = Page.BarHeight), contentAlignment = Alignment.Center) {
                Segmented(
                    listOf(false to stringResource(R.string.calendar_gregorian), true to stringResource(R.string.calendar_hijri)), hijri,
                    { h ->
                        if (h != hijri) {
                            anchor = (if (today in month.dates(adjust)) today else month.middle(adjust)).toEpochDay()
                            hijri = h
                        }
                    },
                    Modifier.padding(horizontal = 60.dp).widthIn(max = 300.dp),
                )
                BackButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                val share = stringResource(R.string.timetable_share)
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = share, onClick = withTick {
                            scope.launch {
                                val table = source.loadOrNull(month) ?: return@launch
                                onShare("${words.title} · $title", TimetableText.of(table, placeName, method, adjust, f, words))
                            }
                        })
                        .semantics { contentDescription = share },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Share, null, tint = c.goldText, modifier = Modifier.size(21.dp)) }
            }
            Box(Modifier.widthIn(max = Page.MaxWidth).fillMaxWidth().padding(start = Page.Margin, end = Page.Margin, top = 10.dp, bottom = 12.dp)) {
                MonthBar(
                    title, MonthWords.span(month, adjust, f),
                    canPrev = pager.currentPage > 0, canNext = pager.currentPage < pages.count - 1,
                    onPrev = { turn(pager.targetPage - 1) }, onNext = { turn(pager.targetPage + 1) },
                )
            }
            // The table: a pane of glass from here to the foot of the page (the tab bar floats over its last rows).
            val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            BoxWithConstraints(
                Modifier.weight(1f).widthIn(max = Page.MaxWidth).fillMaxWidth().padding(horizontal = Page.Margin)
                    .clip(shape).background(c.glass).glassEdge(shape, c.glassEdge, Color.Transparent),
            ) {
                // The table sets its own sizes ([TableMetrics]): inside it a size is what is seen, whatever the phone's
                // text size. (Android scales large text less than small text, so a size measured at one scale and
                // drawn at another did not fit: at 1.3 the times were cut.) The words under the table follow the phone.
                val phone = LocalDensity.current
                val width = maxWidth
                CompositionLocalProvider(LocalDensity provides Density(phone.density, 1f)) {
                    val m = rememberTableMetrics(width, f, phone.fontScale)
                    Column(Modifier.fillMaxSize()) {
                        Heads(m, f)
                        Hairline()
                        // (Each calendar has its own pages: a month's place in the list is kept per page, and page 72
                        // of one calendar is not page 72 of the other.)
                        key(hijri) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
                                MonthPage(
                                    pages.at(page), source, adjust, f, today, next, m, phone,
                                    bottom = contentPadding.calculateBottomPadding(), placeLine = "$placeName · $method", authority = authority,
                                )
                            }
                        }
                    }
                }
            }
        }

        // The way back to this month: there only while another month is on show. A small capsule of the tab bar's own
        // glass, docked just over it (as iOS 26 sets an accessory over its floating tab bar): the bar already lies over
        // the last rows in sight, and every row can be scrolled clear of both.
        AnimatedVisibility(
            pager.currentPage != thisPage, Modifier.align(Alignment.BottomCenter).padding(bottom = (contentPadding.calculateBottomPadding() - 8.dp).coerceAtLeast(0.dp)),
            enter = fadeIn(tween(NoorMotion.Quick)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.8f),
            exit = fadeOut(tween(NoorMotion.Quick)) + scaleOut(tween(NoorMotion.Quick), targetScale = 0.85f),
        ) {
            val pill = RoundedCornerShape(50)
            Box(
                Modifier.heightIn(min = 48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = withTick {
                    scope.launch { if (kotlin.math.abs(thisPage - pager.currentPage) <= 3) pager.animateScrollToPage(thisPage) else pager.scrollToPage(thisPage) }
                }),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.shadow(10.dp, pill, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f)).clip(pill)
                        .background(c.chrome.compositeOver(c.background)).glassEdge(pill, c.glassEdge, c.glassEdgeLow).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Replay, null, tint = c.goldText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.timetable_this_month), style = MaterialTheme.typography.labelLarge, color = c.goldText, maxLines = 1)
                }
            }
        }
    }
}

/** The month on show and the arrows to its neighbours; under its name, the months of the other calendar it spans. */
@Composable
private fun MonthBar(title: String, span: String, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = NoorTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.timetable_previous_month), canPrev, onPrev)
            // (A Hijri month's name is long, "Jumada al-Akhirah 1448 AH": it takes a smaller size before a second line.)
            FittedLine(title, MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.sp), c.textPrimary, listOf(22f, 20f, 18f, 16f),
                Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() })
            MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.timetable_next_month), canNext, onNext)
        }
        // (Under the arrows, the width of the page: beside them "Jumada al-Ula – Jumada al-Akhirah 1448 AH" broke its year off.)
        if (span.isNotEmpty()) FittedLine(span, MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = 0.1.sp), c.goldText, listOf(13.5f, 12.5f, 11.5f), Modifier.fillMaxWidth())
    }
}

/** One line in the largest of [sizes] (sp) that fits the width; two lines of the smallest when none does. */
@Composable
private fun FittedLine(text: String, style: TextStyle, color: Color, sizes: List<Float>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val size = remember(text, width, style, measurer) {
            sizes.firstOrNull { measurer.measure(text, style.copy(fontSize = it.sp), maxLines = 1, softWrap = false).size.width <= width } ?: sizes.last()
        }
        Text(text, style = style.copy(fontSize = size.sp), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** To the month before or after: a round pane of glass with a chevron in the accent (mirrored in Arabic); 48 dp to touch. */
@Composable
private fun MonthArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = withTick(onClick)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).then(if (enabled) Modifier.background(c.glass).glassEdge(CircleShape, c.glassEdge, c.glassEdgeLow) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) c.goldText else c.textMuted.copy(alpha = 0.3f), modifier = Modifier.size(22.dp)) }
    }
}

/**
 * The table's measures for a width and a text size. The six times share the width left by the day; their figures are
 * as large as fits a column with air on both sides, so they follow the phone's text size only as far as there is
 * room (a table that scrolled sideways, or broke its times, would be no table). TalkBack reads each day in full.
 */
private class TableMetrics(
    val side: Dp, val dayWidth: Dp, val dayGap: Dp, val head: TextUnit, val weekday: TextUnit, val number: TextUnit, val small: TextUnit,
    /**
     * A time is set on its colon: the hours end at it and the minutes begin at it, in boxes as wide as one figure
     * ([hour1]) or two ([hour2], [minute]) can be, so down a column the colons stand under each other whatever the
     * numerals (Arabic-Indic figures do not share one width in this typeface).
     */
    val hour1: Dp, val hour2: Dp, val minute: Dp, val rowHeight: Dp,
    /** A time's style at each weight it is drawn in (plain, today's, and the heavy ones), and the word under a Friday's noon. */
    val plain: TextStyle, val medium: TextStyle, val heavy: TextStyle, val label: TextStyle,
    /** Measures the times as they are drawn (and remembers the measures: the same hours and minutes come again and again). */
    val measurer: TextMeasurer,
)

/** [scale] is the phone's text size; the table itself is laid out at a text scale of 1 (see [TimetableContent]). */
@Composable
private fun rememberTableMetrics(width: Dp, f: Formatter, scale: Float): TableMetrics {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current
    val type = MaterialTheme.typography
    return remember(width, f, scale, density, measurer) { tableMetrics(width, f, scale, density, measurer, type.bodyMedium.merge(Tabular), type.labelSmall) }
}

private fun tableMetrics(width: Dp, f: Formatter, scale: Float, density: Density, measurer: TextMeasurer, figures: TextStyle, words: TextStyle): TableMetrics {
    fun widthOf(text: String, style: TextStyle, size: Float): Float =
        measurer.measure(text, style.copy(fontSize = size.sp, letterSpacing = 0.sp), maxLines = 1, softWrap = false, density = density).size.width / density.density
    /** The widest a text of this [pattern] of figures can be ("00"): not every numeral system's figures share a width. */
    fun widest(pattern: String, style: TextStyle, size: Float): Float = (0..9).maxOf { d -> widthOf(f.digits(pattern.replace('0', '0' + d)), style, size) }
    val heavy = figures.copy(fontWeight = FontWeight.SemiBold)
    val medium = words.copy(fontWeight = FontWeight.Medium)
    // The day's own words follow the phone's text size a little way (they have a narrow column to themselves).
    val grow = scale.coerceIn(0.85f, 1.15f)
    val weekday = 10.5f * grow; val number = 16f * grow; val small = 10.5f * grow
    val side = 10.dp
    val dayGap = 8f
    val dayWidth = maxOf(DayOfWeek.entries.maxOf { widthOf(f.weekdayShort(it), medium, weekday) }, widest("00", heavy, number) + 7f + widest("00", figures, small)) + 1f
    val column = (width.value - 2 * side.value - dayWidth - dayGap) / Prayer.entries.size
    // A time takes at most 84% of its column; on a wide screen it is a little larger than on a phone.
    val unit = (2 * widest("00", heavy, 100f) + widthOf(":", heavy, 100f)) / 100f
    val time = minOf((if (column > 64f) 16f else if (column > 52f) 15f else 14.5f) * scale, column * 0.84f / unit)
    val head = minOf(11.5f * grow, column * 0.94f / (Prayer.entries.maxOf { widthOf(f.prayerName(it), medium, 100f) } / 100f))
    val friday = f.prayerName(Prayer.DHUHR, LocalDate.of(2026, 11, 6))
    val label = minOf(10f * grow, column * 0.96f / (widthOf(friday, medium, 100f) / 100f))
    val figure = figures.copy(fontSize = time.sp, lineHeight = (time * 1.3f).sp, letterSpacing = 0.sp)
    return TableMetrics(
        side, dayWidth.dp, dayGap.dp, head.sp, weekday.sp, number.sp, small.sp,
        hour1 = widest("0", heavy, time).dp, hour2 = widest("00", heavy, time).dp, minute = widest("00", heavy, time).dp,
        rowHeight = maxOf(46f, weekday * 1.35f + number * 1.3f + 10f, time * 1.3f + 2 * label * 1.35f + 6f).dp,
        plain = figure.copy(fontWeight = FontWeight.Normal), medium = figure.copy(fontWeight = FontWeight.Medium), heavy = figure.copy(fontWeight = FontWeight.SemiBold),
        label = medium.copy(fontSize = label.sp, lineHeight = (label * 1.3f).sp, letterSpacing = 0.sp),
        measurer = measurer,
    )
}

/** The six names over their columns. They stay while the days scroll. */
@Composable
private fun Heads(m: TableMetrics, f: Formatter) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = m.side), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(m.dayWidth + m.dayGap))
        for (p in Prayer.entries) Text(
            f.prayerName(p), Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = m.head, letterSpacing = 0.sp),
            // (Shuruq is not a prayer: its column is quieter, as its row is on the home screen.)
            color = if (p == Prayer.SHURUQ) c.textMuted else c.textSecondary,
        )
    }
}

/** What a month's list holds, in order: a line where a month of the other calendar begins, and the days. */
private sealed interface TableItem {
    data class Section(val text: String) : TableItem
    data class Day(val index: Int) : TableItem
}

/** One month: its days as rows (their times filled in as soon as they are worked out) and, under them, what they follow. */
@Composable
private fun MonthPage(
    month: TableMonth, source: TableSource, adjust: Int, f: Formatter, today: LocalDate, next: Pair<LocalDate, Prayer>?, m: TableMetrics,
    /** The phone's own density (its text size): the words between and under the days follow it. */
    phone: Density, bottom: Dp, placeLine: String, authority: Authority?,
) {
    val c = NoorTheme.colors
    // The days and their dates are known at once; the times come from the source (at once too when it has them).
    val days = remember(month, adjust) { month.dates(adjust).map { TableDay(it, HijriDate.of(it, adjust), emptyMap(), false) } }
    var table by remember(month) { mutableStateOf(source.cached(month)) }
    // (Should a month fail to be worked out, its skeleton stays; the page does not fall with it.)
    LaunchedEffect(source, month) { source.loadOrNull(month)?.let { table = it } }
    val shown = table?.takeIf { it.days.size == days.size }
    val filled by animateFloatAsState(if (shown != null) 1f else 0f, tween(NoorMotion.Quick), label = "timetableFill")

    val rows = remember(days, f) {
        buildList {
            days.forEachIndexed { i, d ->
                if (i > 0) MonthWords.section(month, d, f)?.let { add(TableItem.Section(it)) }
                add(TableItem.Day(i))
            }
        }
    }
    val todayIndex = days.indexOfFirst { it.date == today }
    // This month opens with today near the top (two days before it still in view), not on days gone by.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (todayIndex > 4) rows.indexOf(TableItem.Day(todayIndex - 2)).coerceAtLeast(0) else 0)
    // Whether a column's hours take two figures this month ("12:05", "17:38") or one ("5:13"): the room kept for them.
    val wide = remember(shown, f) { Prayer.entries.map { p -> shown?.days?.any { TimetableText.time(it.minutes[p], f).length > 4 } ?: (f.time(0).second == null) } }
    val todayWord = stringResource(R.string.today)
    val nextWord = stringResource(R.string.next_prayer)

    LazyColumn(Modifier.fillMaxSize().testTag("timetable/" + month.arg), state = list, contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 64.dp)) {
        items(rows.size, key = { i -> when (val it = rows[i]) { is TableItem.Section -> "s" + it.text; is TableItem.Day -> days[it.index].date.toEpochDay() } }) { i ->
            when (val item = rows[i]) {
                is TableItem.Section -> CompositionLocalProvider(LocalDensity provides phone) {
                    Text(
                        item.text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, letterSpacing = 0.2.sp), color = c.goldText,
                        modifier = Modifier.fillMaxWidth().padding(start = m.side + 2.dp, end = m.side, top = 14.dp, bottom = 6.dp).semantics { heading() },
                    )
                }
                is TableItem.Day -> {
                    val day = shown?.days?.get(item.index) ?: days[item.index]
                    val isToday = item.index == todayIndex
                    DayRow(
                        day, month.hijri, loaded = shown != null, filled = { filled }, isToday = isToday, past = todayIndex >= 0 && item.index < todayIndex,
                        nextPrayer = next?.takeIf { it.first == day.date }?.second, wide = wide, f = f, m = m, todayWord = todayWord, nextWord = nextWord,
                    )
                    // (No line against the lit row: its own edge is line enough.)
                    val last = i == rows.lastIndex
                    if (!last && !isToday && item.index + 1 != todayIndex && rows[i + 1] is TableItem.Day) Hairline(Modifier.padding(horizontal = m.side))
                }
            }
        }
        item(key = "foot") { CompositionLocalProvider(LocalDensity provides phone) { Foot(shown, month.hijri, placeLine, authority, m.side) } }
    }
}

private suspend fun TableSource.loadOrNull(month: TableMonth): Table? =
    try { load(month) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

/**
 * A day: its weekday over its number (the other calendar's day small beside it), then the six times. Today's row is
 * lit, and the next prayer's time in it is in the accent; days gone by this month are quieter. In Ramadan the other
 * calendar's day is in the accent and the two times the fast turns on (Fajr, Maghrib) are heavier. A Friday names its
 * noon prayer Jumu'ah, as the home screen does. For TalkBack the row is one sentence.
 */
@Composable
private fun DayRow(
    day: TableDay, hijriFirst: Boolean, loaded: Boolean, filled: () -> Float, isToday: Boolean, past: Boolean, nextPrayer: Prayer?,
    wide: List<Boolean>, f: Formatter, m: TableMetrics, todayWord: String, nextWord: String,
) {
    val c = NoorTheme.colors
    val ramadan = day.hijri.isRamadan
    val lit = RoundedCornerShape(14.dp)
    val spoken = buildString {
        val comma = if (f.arabic) "، " else ", "
        if (isToday) append(todayWord).append(comma)
        append(f.gregorian(day.date)).append(comma).append(f.hijri(day.hijri))
        if (loaded) for (p in Prayer.entries) {
            append(if (p == Prayer.FAJR) ". " else comma).append(f.prayerName(p, day.date)).append(' ')
            append(day.minutes[p]?.let(f::timeText) ?: "—")
            if (p == nextPrayer) append(" (").append(nextWord).append(')')
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (isToday) 2.dp else 0.dp)
            .then(if (isToday) Modifier.clip(lit).background(c.glassRaised).border(0.75.dp, c.gold.copy(alpha = 0.55f), lit) else Modifier)
            .heightIn(min = m.rowHeight).padding(horizontal = m.side - 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val type = MaterialTheme.typography
        Column(Modifier.width(m.dayWidth)) {
            Text(f.weekdayShort(day.date.dayOfWeek), maxLines = 1, softWrap = false,
                style = type.labelSmall.copy(fontSize = m.weekday, letterSpacing = 0.sp),
                color = if (day.friday) c.goldText else c.textMuted)
            // (The other calendar's day keeps to the cell's far edge, so down the table it is a quiet column of its
            // own: set close after the number, "1 21" read as one figure.)
            Row(Modifier.fillMaxWidth()) {
                Text(f.number(if (hijriFirst) day.hijri.day else day.date.dayOfMonth), maxLines = 1, softWrap = false,
                    style = type.bodyMedium.merge(Tabular).copy(fontSize = m.number, fontWeight = FontWeight.SemiBold, lineHeight = m.number * 1.25f),
                    color = when { isToday -> c.goldText; past -> c.textSecondary; else -> c.textPrimary }, modifier = Modifier.alignByBaseline())
                Text(f.number(if (hijriFirst) day.date.dayOfMonth else day.hijri.day), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    style = type.bodyMedium.merge(Tabular).copy(fontSize = m.small),
                    color = if (ramadan && !hijriFirst) c.goldText else c.textMuted, modifier = Modifier.weight(1f).alignByBaseline())
            }
        }
        Spacer(Modifier.width(m.dayGap))
        // The six times, drawn (not six texts): each set on its colon, in the middle of its column.
        val parts = remember(day, f) { Prayer.entries.map { p -> day.minutes[p]?.let { f.time(it).first.split(':') } } }
        val friday = if (day.friday) f.prayerName(Prayer.DHUHR, day.date) else null
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val primary = c.textPrimary; val secondary = c.textSecondary; val muted = c.textMuted; val accent = c.goldText
        Canvas(Modifier.weight(1f).height(m.rowHeight).graphicsLayer { alpha = if (loaded) filled() else 1f }) {
            val column = size.width / Prayer.entries.size
            Prayer.entries.forEachIndexed { i, p ->
                // (Times read left to right in both languages, as everywhere in the app; in Arabic the columns run from the right.)
                val middle = if (rtl) size.width - column * (i + 0.5f) else column * (i + 0.5f)
                if (!loaded) {
                    // The table's skeleton, for the moment a month is being worked out.
                    val w = (m.hour1 + m.minute).toPx() * 0.8f; val h = 8.dp.toPx()
                    drawRoundRect(muted.copy(alpha = 0.22f), Offset(middle - w / 2f, (size.height - h) / 2f), Size(w, h), CornerRadius(h / 2f))
                    return@forEachIndexed
                }
                val isNext = p == nextPrayer
                val style = when { isNext -> m.heavy; ramadan && (p == Prayer.FAJR || p == Prayer.MAGHRIB) -> m.heavy; isToday -> m.medium; else -> m.plain }
                val time = parts[i]
                if (time == null || time.size != 2) {
                    val none = m.measurer.measure("\u2014", style)
                    drawText(none, muted, Offset(middle - none.size.width / 2f, (size.height - none.size.height) / 2f))
                    return@forEachIndexed
                }
                val color = when { isNext -> accent; past || p == Prayer.SHURUQ -> secondary; else -> primary }
                val hours = m.measurer.measure(time[0], style); val colon = m.measurer.measure(":", style); val minutes = m.measurer.measure(time[1], style)
                val room = (if (wide[i]) m.hour2 else m.hour1).toPx()
                val left = middle - (room + colon.size.width + m.minute.toPx()) / 2f
                val y = (size.height - hours.size.height) / 2f
                drawText(hours, color, Offset(left + room - hours.size.width, y))
                drawText(colon, color, Offset(left + room, y))
                drawText(minutes, color, Offset(left + room + colon.size.width, y))
                if (p == Prayer.DHUHR && friday != null) {
                    val word = m.measurer.measure(friday, m.label)
                    drawText(word, accent, Offset(middle - word.size.width / 2f, y + hours.size.height - 1.dp.toPx()))
                }
            }
        }
    }
}

/** Under the days: Ramadan's note when the month holds some of it, the place, what the times follow and on what grounds. */
@Composable
private fun Foot(table: Table?, hijri: Boolean, placeLine: String, authority: Authority?, side: Dp) {
    val c = NoorTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    Column(Modifier.fillMaxWidth().padding(start = side + 2.dp, end = side + 2.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Hairline()
        Spacer(Modifier.height(2.dp))
        if (table?.hasRamadan == true) Text(stringResource(R.string.timetable_ramadan_note), style = small, color = c.goldText)
        Text(placeLine, style = small.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
        if (table != null) Text(stringResource(when {
            table.official == Table.Coverage.ALL -> R.string.basis_official
            table.official == Table.Coverage.SOME -> R.string.timetable_basis_some
            authority == null -> R.string.basis_manual
            authority.kind == SourceKind.VALIDATED_CALCULATION -> R.string.basis_validated
            authority.kind == SourceKind.AUTHORITY_PARAMETERS -> R.string.basis_parameters
            else -> R.string.basis_recognised
        }), style = small, color = c.textMuted)
        if (hijri) Text(stringResource(R.string.calendar_hijri_note), style = small, color = c.textMuted)
    }
}

/** No place yet: the bar, and the one thing to do. */
@Composable
private fun NoPlace(contentPadding: PaddingValues, onBack: () -> Unit, onOpenLocation: () -> Unit) {
    val c = NoorTheme.colors
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = contentPadding.calculateTopPadding()).height(Page.BarHeight), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.timetable_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
                modifier = Modifier.semantics { heading() })
            BackButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(horizontal = 28.dp).padding(bottom = contentPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_set_location_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.today_set_location_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            NoorButton(stringResource(R.string.set_location), onOpenLocation, Modifier.fillMaxWidth())
        }
    }
}

/** The way in from the date sheet: one button that opens the timetable of the month the sheet is showing. */
@Composable
fun TimetableEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    NoorButton(stringResource(R.string.timetable_open), onClick, modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = Icons.Rounded.CalendarViewMonth)
}
