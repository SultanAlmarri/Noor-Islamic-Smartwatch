package com.noor.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
import com.noor.prayer.calendar.IslamicCalendar
import com.noor.prayer.qibla.Qibla
import com.noor.prayer.sky.SkyPalette
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
        androidx.wear.compose.material3.AppScaffold {
            if (config == null) WelcomeScreen(welcome) else HomeScreen(config, t, clock, extras)
        }
    }
}

@Composable
internal fun HomeScreen(c: WatchConfig, t: WearText, clock: () -> Instant, extras: HomeExtras) {
    val now by produceState(clock()) {
        while (true) { value = clock(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    // Recalculated once per minute at most: the countdown itself only needs the cached state's instants.
    val minuteKey = now.epochSecond / 60
    val st = remember(c, minuteKey) { c.state(now) }
    val untilNext = Duration.between(now, st.next.at)
    val progress = st.current?.let {
        val total = Duration.between(it.at, st.next.at).seconds.toFloat()
        (Duration.between(it.at, now).seconds / total).coerceIn(0f, 1f)
    } ?: 0f
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    // The light of the moment: the same sky colour the phone shows, as a soft glow behind the countdown.
    val glow = remember(c, minuteKey) {
        val dhuhr = st.today.instant(Prayer.DHUHR)
        Color(SkyPalette.glow(SunPath.elevation(now, c.point), evening = dhuhr != null && now.isAfter(dhuhr)))
    }
    val qibla = remember(c.point) { Qibla.from(c.point) }
    val nextEvent = remember(c, st.today.date) { IslamicCalendar.upcoming(st.today.date, c.hijriAdjust, limit = 1).firstOrNull() }

    ScreenScaffold(scrollState = listState) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), Color.Transparent), radius = 380f, center = Offset.Unspecified)))
            // The interval to the next prayer, drawn around the edge of the screen (gap at the top for the clock).
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 300f, endAngle = 240f,
                strokeWidth = 4.dp,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = Gold, trackColor = Gold.copy(alpha = 0.16f)),
            )
            ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                item { Hero(c, t, st.next.prayer, st.next.at, untilNext) }
                item {
                    Text(
                        t.placeWithFlag(c) + "  ·  " + t.hijri(HijriDate.of(st.today.date, c.hijriAdjust)),
                        style = MaterialTheme.typography.labelSmall, color = Mist, textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
                Prayer.entries.forEach { p ->
                    item {
                        val at = st.today.instant(p)
                        PrayerRow(p, if (at != null) t.time(at, c.zone) else "—", t,
                            isCurrent = st.current?.prayer == p && st.current?.date == st.today.date,
                            isNext = st.next.prayer == p && st.next.date == st.today.date)
                    }
                }
                item { AlertSection(c, t) }
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    NoorChip(stringResource(R.string.qibla), R.drawable.ic_qibla, { extras.onOpen(Dest.QIBLA) },
                        secondary = stringResource(R.string.qibla_from_north, t.degrees(qibla.bearingDeg)))
                }
                item {
                    NoorChip(stringResource(R.string.tasbih), R.drawable.ic_tasbih, { extras.onOpen(Dest.TASBIH) },
                        secondary = if (extras.tasbihToday > 0) stringResource(R.string.tasbih_today, t.digits(extras.tasbihToday.toString())) else stringResource(R.string.tasbih_start))
                }
                item {
                    NoorChip(stringResource(R.string.calendar), R.drawable.ic_calendar, { extras.onOpen(Dest.CALENDAR) },
                        secondary = nextEvent?.let { e -> t.eventName(e.event) + " · " + daysUntilText(e.daysUntil, t) })
                }
                item { NoorChip(stringResource(R.string.settings), R.drawable.ic_settings, { extras.onOpen(Dest.SETTINGS) }, iconTint = Mist) }
            }
        }
    }
}

@Composable
private fun Hero(c: WatchConfig, t: WearText, next: Prayer, at: Instant, until: Duration) {
    // Same order as the phone: the prayer, the countdown, the time.
    val spoken = stringResource(R.string.a11y_next_prayer, t.name(next), t.remaining(until), t.time(at, c.zone))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(ComplicationBuilder.iconRes(next)), null, Modifier.size(20.dp), tint = GoldSoft)
            Spacer(Modifier.width(6.dp))
            Text(
                t.name(next),
                style = if (t.arabic) TextStyle(fontFamily = Kufi, fontSize = 24.sp) else MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
                color = GoldSoft,
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                t.countdown(until),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = Screen.sp(34f, 40f), letterSpacing = (-1).sp,
                    fontFeatureSettings = "tnum"),
                color = Ink,
            )
        }
        Text(stringResource(R.string.at_time_watch, t.time(at, c.zone)), style = MaterialTheme.typography.labelMedium, color = Mist)
    }
}

@Composable
private fun PrayerRow(p: Prayer, time: String, t: WearText, isCurrent: Boolean, isNext: Boolean) {
    val bg = if (isCurrent) Gold.copy(alpha = 0.14f) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(50))
            .then(if (isNext) Modifier.border(1.dp, Gold.copy(alpha = 0.55f), RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(painterResource(ComplicationBuilder.iconRes(p)), null, Modifier.size(18.dp),
            tint = if (isCurrent || isNext) Gold else Mist.copy(alpha = if (p.isPrayer) 1f else 0.7f))
        Text(
            t.name(p), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = when { isNext -> Gold; isCurrent -> Ink; else -> Ink.copy(alpha = if (p.isPrayer) 0.92f else 0.7f) },
        )
        Text(time, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isNext) Gold else Ink)
    }
}
