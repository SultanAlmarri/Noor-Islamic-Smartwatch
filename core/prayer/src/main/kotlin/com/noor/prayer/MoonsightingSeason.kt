package com.noor.prayer

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The Moonsighting Committee Worldwide's seasonal twilight (Syed Khalid Shaukat, moonsighting.com): the longest that
 * Fajr may come before sunrise and Isha after sunset, in minutes, as a function of latitude and of the days since the
 * winter solstice. Coefficients and interpolation as published by the committee and implemented in Batoul Apps' Adhan
 * libraries (adhan-js `Astronomical.seasonAdjustedMorningTwilight` / `seasonAdjustedEveningTwilight`,
 * `daysSinceSolstice`), which this follows exactly, including the rounding to whole seconds.
 */
object MoonsightingSeason {

    /** Seconds before sunrise beyond which Fajr is not placed. */
    fun secondsBeforeSunrise(latitude: Double, date: LocalDate): Double {
        val l = abs(latitude) / 55.0
        val a = 75 + 28.65 * l
        val b = 75 + 19.44 * l
        val c = 75 + 32.74 * l
        val d = 75 + 48.10 * l
        return seconds(interpolate(a, b, c, d, daysSinceSolstice(date, latitude)))
    }

    /** Seconds after sunset beyond which Isha is not placed, for [shafaq]. */
    fun secondsAfterSunset(latitude: Double, date: LocalDate, shafaq: Shafaq): Double {
        val l = abs(latitude) / 55.0
        val (a, b, c, d) = when (shafaq) {
            Shafaq.AHMER -> listOf(62 + 17.40 * l, 62 - 7.16 * l, 62 + 5.12 * l, 62 + 19.44 * l)
            Shafaq.ABYAD -> listOf(75 + 25.60 * l, 75 + 7.16 * l, 75 + 36.84 * l, 75 + 81.84 * l)
            Shafaq.GENERAL -> listOf(75 + 25.60 * l, 75 + 2.05 * l, 75 - 9.21 * l, 75 + 6.14 * l)
        }
        return seconds(interpolate(a, b, c, d, daysSinceSolstice(date, latitude)))
    }

    private fun seconds(minutes: Double): Double = (minutes * 60.0).roundToLong().toDouble()

    private fun interpolate(a: Double, b: Double, c: Double, d: Double, dyy: Int): Double = when {
        dyy < 91 -> a + (b - a) / 91.0 * dyy
        dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
        dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
        dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
        dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
        else -> b + (a - b) / 91.0 * (dyy - 275)
    }

    /** Days since the winter solstice of the observer's hemisphere (taken as 21 December / 21 June). */
    internal fun daysSinceSolstice(date: LocalDate, latitude: Double): Int {
        val dayOfYear = date.dayOfYear
        val daysInYear = date.lengthOfYear()
        return if (latitude >= 0) {
            val d = dayOfYear + 10
            if (d >= daysInYear) d - daysInYear else d
        } else {
            val d = dayOfYear - if (date.isLeapYear) 173 else 172
            if (d < 0) d + daysInYear else d
        }
    }
}
