package com.noor.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
import com.noor.prayer.calendar.IslamicCalendar
import com.noor.prayer.qibla.Qibla
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.complication.ComplicationBuilder
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** What the home screen shows besides the times; all of it is optional so the screen also renders in tests. */
data class HomeExtras(
    val tasbihToday: Int = 0,
    val onOpen: (String) -> Unit = {},
)

/**
 * The watch app for a configuration, without navigation: the home screen, or the welcome screen when nothing is set
 * up yet. [clock] is replaceable so previews and tests are deterministic.
 */
@Composable
fun NoorWearContent(config: WatchConfig?, clock: () -> Instant = Instant::now, extras: HomeExtras = HomeExtras(), welcome: WelcomeActions = WelcomeActions()) {
    NoorWearTheme(config) { t ->
        NoorScaffold {
            if (config == null) WelcomeScreen(welcome) else HomeScreen(config, t, clock, extras)
        }
    }
}

/**
 * Home (0.25). The first screenful is the moment: the sky as it is now over the user's place, the next prayer, the
 * countdown very large, the time it is at, and the interval drawn round the edge. Turn the crown and the sky gives
 * way to black: the day's times, then the tools.
 */
@Composable
internal fun HomeScreen(c: WatchConfig, t: WearText, clock: () -> Instant, extras: HomeExtras) {
    val now by produceState(clock()) {
        while (true) { value = clock(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    // Recalculated once per minute at most: the countdown itself only needs the cached state's instants.
    val minuteKey = now.epochSecond / 60
    val st = remember(c, minuteKey) { c.state(now) }
    val untilNext = Duration.between(now, st.next.at)
    // The ring fills as the interval passes (it does not drain with the countdown): the same reading as the ring on
    // Minhad's watch faces, where gold is the time gone by. One ring that means two things would be worse than either.
    val progress = st.current?.let {
        val total = Duration.between(it.at, st.next.at).seconds.toFloat()
        (Duration.between(it.at, now).seconds / total).coerceIn(0f, 1f)
    } ?: 0f
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    // The sky of the moment: the phone's own sky colours for the Sun's height here and now.
    val sky = remember(c, minuteKey) {
        val dhuhr = st.today.instant(Prayer.DHUHR)
        WatchSky.stops(SunPath.elevation(now, c.point), evening = dhuhr != null && now.isAfter(dhuhr))
    }
    val qibla = remember(c.point) { Qibla.from(c.point) }
    val nextEvent = remember(c, st.today.date) { IslamicCalendar.upcoming(st.today.date, c.hijriAdjust, limit = 1).firstOrNull() }
    // How far the first screenful has been scrolled away (null once it is out of sight).
    val heroOffset by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset } }
    val screenPx = with(LocalDensity.current) { Screen.height().toPx() }
    fun heroAlpha(): Float = heroOffset?.let { (1f + it / (screenPx * 0.75f)).coerceIn(0f, 1f) } ?: 0f

    ScreenScaffold(
        scrollState = listState,
        // The scroll bar runs where the ring is: it belongs to the black part of the page, and comes as the sky goes.
        scrollIndicator = { ScrollIndicator(listState, Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = 1f - heroAlpha() }) },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            // The sky and the ring belong to the first screenful: they leave with it, fading into the black below.
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = (heroOffset ?: 0).toFloat(); alpha = heroAlpha() }) {
                SkyBackdrop(sky, Modifier.fillMaxSize())
                // The interval to the next prayer, drawn around the edge of the screen (gap at the top for the clock).
                Ring(progress, Modifier.fillMaxSize().padding(4.dp), track = Color.White.copy(alpha = 0.20f), animate = false)
            }
            ScalingLazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp), autoCentering = AutoCenteringParams(itemIndex = 0),
                // No shrinking at the edges here: the first screenful fills the screen edge to edge.
                scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
            ) {
                item { Hero(Modifier.fillParentMaxSize(), c, t, st.next.prayer, st.next.at, untilNext, t.name(st.next), HijriDate.of(st.today.date, c.hijriAdjust)) }
                item { Caption(stringResource(R.string.today), t, Modifier.padding(bottom = 2.dp)) }
                Prayer.entries.forEach { p ->
                    item {
                        val at = st.today.instant(p)
                        val isCurrent = st.current?.prayer == p && st.current?.date == st.today.date
                        val isNext = st.next.prayer == p && st.next.date == st.today.date
                        PrayerRow(p, t.name(p, st.today.date), if (at != null) t.time(at, c.zone) else "—",
                            isCurrent = isCurrent, isNext = isNext, isPast = !isCurrent && !isNext && at != null && at.isBefore(now))
                    }
                }
                item { AlertSection(c, t) }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val bearing = t.degrees(qibla.bearingDeg)
                        ToolTile(stringResource(R.string.qibla), R.drawable.ic_qibla, Tint.Gold, bearing,
                            stringResource(R.string.qibla) + ", " + stringResource(R.string.qibla_from_north, bearing), Modifier.weight(1f)) { extras.onOpen(Dest.QIBLA) }
                        val count = t.count(extras.tasbihToday.toLong())
                        // (Nothing counted yet: no figure. An Arabic-Indic zero alone is a dot.)
                        ToolTile(stringResource(R.string.tasbih), R.drawable.ic_tasbih, Tint.Green, if (extras.tasbihToday > 0) count else "",
                            stringResource(R.string.tasbih) + ", " + stringResource(R.string.tasbih_today, count), Modifier.weight(1f)) { extras.onOpen(Dest.TASBIH) }
                    }
                }
                item {
                    NoorChip(stringResource(R.string.calendar), R.drawable.ic_calendar, { extras.onOpen(Dest.CALENDAR) }, iconTint = Tint.Indigo,
                        secondary = nextEvent?.let { e -> t.eventName(e.event) + " · " + daysUntilText(e.daysUntil, t) }, modifier = Modifier.padding(top = 4.dp))
                }
                item {
                    val label = stringResource(R.string.settings)
                    Box(Modifier.padding(top = 6.dp).size(48.dp).pressable(CircleShape, Platter) { extras.onOpen(Dest.SETTINGS) }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(24.dp), tint = Mist)
                    }
                }
            }
            // The time in the ring's gap, straight on the sky, for as long as the first screenful is in place.
            WatchClock(visible = listState.atTop())
        }
    }
}

/** The first screenful: the next prayer over the sky, and the place and date on the dunes. */
@Composable
private fun Hero(modifier: Modifier, c: WatchConfig, t: WearText, next: Prayer, at: Instant, until: Duration, name: String, hijri: HijriDate) {
    // Same order as the phone: the prayer, the countdown, the time.
    val spoken = stringResource(R.string.a11y_next_prayer, name, t.remaining(until), t.time(at, c.zone))
    val h = Screen.height()
    val countdown = t.countdown(until)
    Box(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).offset(y = -h * 0.06f).semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(ComplicationBuilder.iconRes(next)), null, Modifier.size(Screen.dp(20f, 22f)), tint = GoldSoft)
                Spacer(Modifier.width(6.dp))
                Text(name, style = if (t.arabic) Type.kufi(Screen.sp(21f, 24f)) else Type.title.copy(fontSize = Screen.sp(18f, 21f)), color = Ink, maxLines = 1)
            }
            // The owner's format: no leading zero on the hours, "1:07:35", "7:35"; the shorter it is, the larger.
            val size = when {
                countdown.length <= 5 -> Screen.sp(48f, 58f)
                countdown.length <= 7 -> Screen.sp(40f, 48f)
                else -> Screen.sp(35f, 42f)
            }
            Numeral(countdown, size, softColons = c.arabicDigits)
            Text(stringResource(R.string.at_time_watch, t.time(at, c.zone)), style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink, maxLines = 1)
        }
        // On the dunes: the Hijri date, and the place below it where the round screen is narrowest (it is the shorter).
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.07f)) {
            Text(t.hijri(hijri), style = Type.detail.copy(fontSize = Screen.sp(11f, 12.5f)), color = Ink, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Screen.dp(10f, 20f)))
            Text(t.placeWithFlag(c), style = Type.note.copy(fontWeight = FontWeight.Medium), color = Mist, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 46.dp))
        }
    }
}

/**
 * One of the day's six times. One highlight only: the next prayer's row is tinted gold with gold text. The prayer we
 * are in has no fill: full white, with a small gold "Now" after its name. Those gone by are grey.
 */
@Composable
private fun PrayerRow(p: Prayer, name: String, time: String, isCurrent: Boolean, isNext: Boolean, isPast: Boolean) {
    val text = when {
        isNext -> Gold
        isCurrent -> Ink
        isPast -> Quiet
        else -> Ink.copy(alpha = if (p.isPrayer) 1f else 0.85f)
    }
    val style = Type.row.copy(fontSize = 16.sp, fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp)
            .background(if (isNext) Gold.copy(alpha = 0.20f) else Color.Transparent, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp),
    ) {
        Icon(painterResource(ComplicationBuilder.iconRes(p)), null, Modifier.size(20.dp),
            tint = when { isNext -> Gold; isCurrent -> Ink; isPast -> Quiet.copy(alpha = 0.8f); else -> Mist })
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = style, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (isCurrent) Text(stringResource(R.string.now), style = Type.note.copy(fontWeight = FontWeight.SemiBold), color = Gold, maxLines = 1)
        }
        // "3:14" at the row's size, "PM" small and light beside it.
        val cut = time.indexOf(' ')
        Text(
            if (cut > 0) buildAnnotatedString {
                append(time.substring(0, cut))
                withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)) { append(time.substring(cut)) }
            } else buildAnnotatedString { append(time) },
            style = style.copy(fontFeatureSettings = "tnum"), color = text, maxLines = 1,
        )
    }
}

/** One of the two tools side by side: its sign, what it says now (the bearing, today's count), its name. */
@Composable
private fun ToolTile(label: String, @DrawableRes icon: Int, tint: Color, value: String, spoken: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(PlatterShape, Platter, onClick = onClick).semantics { contentDescription = spoken }.padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundSign(icon, tint, size = 28.dp)
            Spacer(Modifier.weight(1f))
            Numeral(value, Screen.sp(17f, 20f))
        }
        Text(label, style = Type.row, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
