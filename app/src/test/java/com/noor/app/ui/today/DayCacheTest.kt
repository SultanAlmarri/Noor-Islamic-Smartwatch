package com.noor.app.ui.today

import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.PrayerProvider
import com.noor.prayer.authority.Timetable
import com.noor.shared.geo.OfficialTimetables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.21: the home screen's days follow a newer official timetable as soon as it is stored (MUIS adds each new year to
 * data.gov.sg; TimetableUpdater fetches it). Until 0.20 the days already worked out stayed as they were (calculated)
 * until a setting changed or the app was closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayCacheTest {
    @Test fun aNewerTimetableIsUsedAtOnce() {
        val tables = OfficialTimetables(RuntimeEnvironment.getApplication())
        val cache = DayCache(tables)
        val s = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "sin")
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val bundled = tables.get(TIMETABLE)!!
        assertTrue(cache.resolved(s, place, profile, bundled.last!!).official)
        val beyond = bundled.last!!.plusDays(1)
        assertFalse(cache.resolved(s, place, profile, beyond).official)

        // The next year arrives (here one more day, written as MUIS publishes it).
        tables.store(TIMETABLE, Timetable.parse(TIMETABLE, bundled.encode() + "\n$beyond,05:41,07:02,13:06,16:27,19:09,20:22"))
        val day = cache.resolved(s, place, profile, beyond)
        assertTrue(day.official)
        assertEquals(5 * 60 + 41, day.day.minutes[com.noor.prayer.Prayer.FAJR])
    }

    private companion object { const val TIMETABLE = "muis_singapore" }
}
