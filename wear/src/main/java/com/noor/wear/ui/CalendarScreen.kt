package com.noor.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
                    // The day of the Hijri month, as large as a date on a calendar's page.
                    val size = Screen.sp(52f, 62f)
                    Numeral(t.digits(h.day.toString()), size, Modifier.padding(bottom = 2.dp), color = Gold)
                    Text(
                        (if (t.arabic) h.monthNameAr else h.monthNameEn) + " " + t.digits(h.year.toString()) + if (t.arabic) " هـ" else " AH",
                        style = if (t.arabic) Type.kufi(18.sp) else Type.title.copy(fontSize = Screen.sp(15.5f, 17f)),
                        color = Ink, textAlign = TextAlign.Center, maxLines = 2,
                    )
                    Text(t.longDate(today), style = Type.detail, color = Quiet, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 1.dp))
                    if (monthLength != null) {
                        // How far through the month: a short bar and its words.
                        Box(Modifier.padding(top = 7.dp, bottom = 3.dp).width(72.dp).height(4.dp).background(PlatterHigh, RoundedCornerShape(50))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((h.day.toFloat() / monthLength).coerceIn(0.04f, 1f)).background(Gold, RoundedCornerShape(50)))
                        }
                        Text(stringResource(R.string.calendar_day_of_month, t.digits(h.day.toString()), t.digits(monthLength.toString())), style = Type.note, color = Quiet)
                    }
                }
            }
            item { Caption(stringResource(R.string.calendar_ahead), t) }
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
    // From two days on, the number of days stands large at the row's end; "Today", "Tomorrow" and "Now" are words.
    val counted = e.daysUntil >= 2
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
            .background(if (soon) PlatterHigh else Platter, PlatterShape)
            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
            .semantics(mergeDescendants = true) { contentDescription = t.eventName(e.event) + ", " + until + ", " + dates + ", " + hijri },
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.eventName(e.event), style = Type.row, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // The Hijri day of a yearly event is in its name (Eid al-Fitr is 1 Shawwal); the White Days show their month.
            val line = if (e.event == IslamicEvent.WHITE_DAYS) dates + "  ·  " + (if (t.arabic) e.hijri.monthNameAr else e.hijri.monthNameEn) else dates
            Text(line, style = Type.detail, color = Quiet, maxLines = 2)
            if (!counted) Text(until, style = Type.detail.copy(fontWeight = FontWeight.SemiBold), color = Gold, maxLines = 1)
        }
        if (counted) {
            val n = e.daysUntil.coerceAtMost(9999)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Numeral(t.digits(n.toString()), if (n >= 100) 19.sp else 22.sp, color = Gold)
                Text(pluralStringResource(R.plurals.days_unit, n.toInt()), style = Type.note, color = Quiet, maxLines = 1)
            }
        }
    }
}
