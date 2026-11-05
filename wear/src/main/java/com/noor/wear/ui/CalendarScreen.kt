package com.noor.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.prayer.HijriDate
import com.noor.prayer.WatchConfig
import com.noor.prayer.calendar.IslamicCalendar
import com.noor.prayer.calendar.IslamicEvent
import com.noor.prayer.calendar.UpcomingEvent
import com.noor.wear.R
import com.noor.wear.WearText
import java.time.LocalDate

/** Today in the Hijri calendar and the days of the year ahead, each with how far away it is. */
@Composable
fun CalendarScreen(c: WatchConfig, t: WearText, today: LocalDate) {
    val h = remember(today, c.hijriAdjust) { HijriDate.of(today, c.hijriAdjust) }
    val events = remember(today, c.hijriAdjust) { IslamicCalendar.upcoming(today, c.hijriAdjust) }
    val monthLength = remember(h) { IslamicCalendar.monthLength(h.year, h.month) }
    val list = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                val spoken = t.hijri(h) + ", " + t.longDate(today)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
                    Text(t.digits(h.day.toString()), style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = Screen.sp(40f, 46f)), color = GoldSoft)
                    Text(
                        (if (t.arabic) h.monthNameAr else h.monthNameEn) + " " + t.digits(h.year.toString()) + if (t.arabic) " هـ" else " AH",
                        style = if (t.arabic) TextStyle(fontFamily = Kufi, fontSize = 17.sp) else MaterialTheme.typography.titleMedium,
                        color = Gold, textAlign = TextAlign.Center,
                    )
                    Text(t.longDate(today), style = MaterialTheme.typography.labelSmall, color = Mist, textAlign = TextAlign.Center)
                    if (monthLength != null) {
                        Text(stringResource(R.string.calendar_day_of_month, t.digits(h.day.toString()), t.digits(monthLength.toString())),
                            style = MaterialTheme.typography.labelSmall, color = Mist.copy(alpha = 0.75f))
                    }
                }
            }
            item { Header(stringResource(R.string.calendar_ahead)) }
            events.forEach { e -> item { EventRow(e, t, today) } }
            item { Footnote(stringResource(R.string.calendar_note)) }
        }
    }
}

@Composable
private fun EventRow(e: UpcomingEvent, t: WearText, today: LocalDate) {
    val soon = e.daysUntil <= 7
    val dates = t.dateRange(e.date, e.endDate, today)
    val hijri = t.digits(e.hijri.day.toString()) + (if (e.event == IslamicEvent.WHITE_DAYS) t.digits("–15") else "") + " " + (if (t.arabic) e.hijri.monthNameAr else e.hijri.monthNameEn)
    val until = if (e.event == IslamicEvent.WHITE_DAYS && e.daysUntil == 0L) stringResource(R.string.calendar_under_way) else daysUntilText(e.daysUntil, t)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (soon) Gold.copy(alpha = 0.14f) else Night, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = t.eventName(e.event) + ", " + until + ", " + dates + ", " + hijri },
    ) {
        Text(t.eventName(e.event), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (soon) GoldSoft else Ink, maxLines = 1)
        // The Hijri day of a yearly event is in its name (Eid al-Fitr is 1 Shawwal); the White Days show their month.
        val line = if (e.event == IslamicEvent.WHITE_DAYS) dates + "  ·  " + (if (t.arabic) e.hijri.monthNameAr else e.hijri.monthNameEn) else dates
        Text(line, style = MaterialTheme.typography.labelSmall, color = Mist, maxLines = 2)
        Text(until, style = MaterialTheme.typography.labelMedium, color = Gold, maxLines = 1)
    }
}
