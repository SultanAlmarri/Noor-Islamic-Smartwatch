package com.noor.app.ui.today

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The dates the Today screen can show: fifteen full years. Every one is calculated on the device with the
 * conventions in force today (for Dubai, IACAD's since August 2020), so the range starts in 2021.
 */
object DateWindow {
    val FIRST: LocalDate = LocalDate.of(2021, 1, 1)
    val LAST: LocalDate = LocalDate.of(2035, 12, 31)
    val pageCount: Int = (ChronoUnit.DAYS.between(FIRST, LAST) + 1).toInt()

    fun pageOf(date: LocalDate): Int = ChronoUnit.DAYS.between(FIRST, clamp(date)).toInt()
    fun dateOf(page: Int): LocalDate = FIRST.plusDays(page.coerceIn(0, pageCount - 1).toLong())
    fun clamp(date: LocalDate): LocalDate = if (date < FIRST) FIRST else if (date > LAST) LAST else date
    operator fun contains(date: LocalDate) = date in FIRST..LAST
}
