package com.noor.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.R
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.HijriDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.WeekFields

private enum class PickerMode { DAYS, MONTHS, YEARS }

/**
 * Month calendar with the Hijri day under every Gregorian day; the title switches to a month list and then to a
 * twelve-year grid for fast jumps. Only dates inside [DateWindow] can be chosen.
 */
@Composable
fun CalendarPicker(
    selected: LocalDate,
    today: LocalDate,
    hijriAdjust: Int,
    f: Formatter,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = NoorTheme.colors
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.from(selected)) }
    var mode by rememberSaveable { mutableStateOf(PickerMode.DAYS) }
    var yearPage by rememberSaveable { mutableStateOf((selected.year - DateWindow.FIRST.year) / 12) }
    val firstMonth = YearMonth.from(DateWindow.FIRST)
    val lastMonth = YearMonth.from(DateWindow.LAST)

    Column(modifier.fillMaxWidth()) {
        // Title row: "November 2026 ⌄"   ‹ ›
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
            val title = when (mode) {
                PickerMode.DAYS -> f.monthYear(month)
                PickerMode.MONTHS -> f.number(month.year)
                PickerMode.YEARS -> yearRange(yearPage).let { "${f.number(it.first)} – ${f.number(it.last)}" }
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    mode = when (mode) { PickerMode.DAYS -> PickerMode.MONTHS; PickerMode.MONTHS -> PickerMode.YEARS; PickerMode.YEARS -> PickerMode.DAYS }
                    yearPage = (month.year - DateWindow.FIRST.year) / 12
                }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Icon(Icons.Rounded.ExpandMore, null, tint = c.gold, modifier = Modifier.size(22.dp).rotate(if (mode == PickerMode.DAYS) 0f else 180f))
            }
            Spacer(Modifier.weight(1f))
            val canBack = when (mode) {
                PickerMode.DAYS -> month > firstMonth
                PickerMode.MONTHS -> month.year > DateWindow.FIRST.year
                PickerMode.YEARS -> yearPage > 0
            }
            val canFwd = when (mode) {
                PickerMode.DAYS -> month < lastMonth
                PickerMode.MONTHS -> month.year < DateWindow.LAST.year
                PickerMode.YEARS -> yearRange(yearPage + 1).first <= DateWindow.LAST.year
            }
            IconButton(onClick = {
                when (mode) {
                    PickerMode.DAYS -> month = month.minusMonths(1)
                    PickerMode.MONTHS -> month = month.minusYears(1)
                    PickerMode.YEARS -> yearPage--
                }
            }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous), tint = if (canBack) c.gold else c.textMuted.copy(alpha = 0.4f))
            }
            IconButton(onClick = {
                when (mode) {
                    PickerMode.DAYS -> month = month.plusMonths(1)
                    PickerMode.MONTHS -> month = month.plusYears(1)
                    PickerMode.YEARS -> yearPage++
                }
            }, enabled = canFwd) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_page), tint = if (canFwd) c.gold else c.textMuted.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = Triple(mode, month, yearPage),
            transitionSpec = {
                val (m0, mo0, y0) = initialState; val (m1, mo1, y1) = targetState
                if (m0 != m1) fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                else {
                    val forward = if (m1 == PickerMode.YEARS) y1 > y0 else mo1 > mo0
                    (slideInHorizontally(tween(220)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(150)))
                }
            },
            label = "calendar",
        ) { (m, ym, yp) ->
            when (m) {
                PickerMode.DAYS -> DaysGrid(ym, selected, today, hijriAdjust, f, onPick)
                PickerMode.MONTHS -> ChoiceGrid(
                    items = Month.entries.map { YearMonth.of(ym.year, it) },
                    label = { f.monthShort(it.month) },
                    isSelected = { it == YearMonth.from(selected) },
                    isCurrent = { it == YearMonth.from(today) },
                    enabled = { it in firstMonth..lastMonth },
                    onClick = { month = it; mode = PickerMode.DAYS },
                )
                PickerMode.YEARS -> ChoiceGrid(
                    items = yearRange(yp).toList(),
                    label = { f.number(it) },
                    isSelected = { it == selected.year },
                    isCurrent = { it == today.year },
                    enabled = { it in DateWindow.FIRST.year..DateWindow.LAST.year },
                    onClick = { month = YearMonth.of(it, month.month).coerceIn(firstMonth, lastMonth); mode = PickerMode.MONTHS },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_range, f.number(DateWindow.FIRST.year), f.number(DateWindow.LAST.year)),
                style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            TextButton(onClick = { onPick(today) }) { Text(stringResource(R.string.go_to_today), color = c.goldText, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

private fun yearRange(page: Int): IntRange = (DateWindow.FIRST.year + page * 12).let { it until it + 12 }

@Composable
private fun DaysGrid(
    ym: YearMonth, selected: LocalDate, today: LocalDate, hijriAdjust: Int, f: Formatter, onPick: (LocalDate) -> Unit,
) {
    val c = NoorTheme.colors
    val firstDow = WeekFields.of(f.locale).firstDayOfWeek
    val days = (0 until 7).map { firstDow.plus(it.toLong()) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            for (d in days) Text(
                f.weekdayShort(d), Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (f.arabic) 9.sp else 11.sp),
                color = if (d == DayOfWeek.FRIDAY) c.goldText else c.textMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        val lead = ((ym.atDay(1).dayOfWeek.value - firstDow.value) + 7) % 7
        val cells = lead + ym.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.92f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum)
                            DayCell(date, date == selected, date == today, date in DateWindow, HijriDate.of(date, hijriAdjust), f) { onPick(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, enabled: Boolean, hijri: HijriDate, f: Formatter, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val fg = when {
        isSelected -> c.onGold
        !enabled -> c.textMuted.copy(alpha = 0.35f)
        else -> c.textPrimary
    }
    val hijriLabel = if (hijri.day == 1) (if (f.arabic) hijri.monthNameAr else hijri.monthNameEn).split(' ', '-').first().take(5) else f.number(hijri.day)
    Column(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(c.gold) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.2.dp, c.gold, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.selected = isSelected
                contentDescription = f.weekday(date) + " " + f.gregorianDate(date)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(f.number(date.dayOfMonth), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 18.sp),
            color = fg, fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal)
        Text(hijriLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 0.sp),
            color = when { isSelected -> c.onGold.copy(alpha = 0.75f); hijri.day == 1 -> c.goldText; else -> c.textMuted },
            maxLines = 1)
    }
}

@Composable
private fun <T> ChoiceGrid(
    items: List<T>, label: (T) -> String, isSelected: (T) -> Boolean, isCurrent: (T) -> Boolean, enabled: (T) -> Boolean, onClick: (T) -> Unit,
) {
    val c = NoorTheme.colors
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in items.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (it in row) {
                    val sel = isSelected(it); val on = enabled(it)
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .then(if (sel) Modifier.background(c.gold) else Modifier)
                            .then(if (isCurrent(it) && !sel) Modifier.border(1.dp, c.gold, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(enabled = on) { onClick(it) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label(it), style = MaterialTheme.typography.titleMedium,
                            color = when { sel -> c.onGold; !on -> c.textMuted.copy(alpha = 0.35f); else -> c.textPrimary })
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val YearMonthSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>({ it.toString() }, { YearMonth.parse(it) })
