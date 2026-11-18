package com.noor.app.data

import com.noor.prayer.GeoPoint
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * 0.21: what the phone sends the watch follows the day and the time zone, not only the settings. Until 0.20 it was sent
 * again only when a setting changed: the eight weeks of times the watch carries ran out on a phone left alone, and a
 * phone carried to another time zone left the watch writing the times in the old zone's hours.
 */
class WearSyncTest {

    @Test fun theWatchIsSentTheTimesAgainWhenTheDayOrTheTimeZoneChanges() = runBlocking {
        // A new day at the place: the weeks of times move on with it.
        val abuDhabi = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "auh")
        val d1 = LocalDate.of(2026, 11, 18)
        val calls = AtomicInteger()
        val days = withTimeout(20_000) {
            WearSync.configs(flowOf(abuDhabi), null, every = 5) { if (calls.getAndIncrement() < 2) d1 else d1.plusDays(1) }.take(2).toList()
        }
        assertEquals(d1.minusDays(1), days[0]!!.official!!.first)
        assertEquals(d1, days[1]!!.official!!.first)

        // Automatic location, carried from Dubai to London: the watch is told the new zone, with no new fix.
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
            val dubai = AppSettings(onboardingDone = true, locationMode = LocationMode.AUTO, lastLocation = KnownLocation(
                GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "AE", "AE-DU", 0L))
            val zones = withTimeout(20_000) {
                val out = ArrayList<String>()
                WearSync.configs(flowOf(dubai), null, every = 5).take(2).collect { c ->
                    assertNotNull(c); out += c!!.zoneId
                    TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
                }
                out
            }
            assertEquals(listOf("Asia/Dubai", "Europe/London"), zones)
        } finally {
            TimeZone.setDefault(before)
        }
    }
}
