package com.noor.wear

import com.noor.prayer.GeoPoint
import com.noor.prayer.LocalSetup
import com.noor.prayer.Prayer
import com.noor.prayer.SetupPlace
import com.noor.prayer.authority.Timetable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * 0.22: a watch set up on its own takes up official times the app gained since (a newer table in an update) at once,
 * not after the stored weeks are seven days old. Until then, from the end of the old table, the watch calculated days
 * the authority had published, though it carried them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialRefreshTest {

    @Test fun aNewerOfficialTableIsUsedAtOnce() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
        val app = RuntimeEnvironment.getApplication() as NoorWearApp
        val repo = app.config
        val today = LocalDate.now(ZoneId.of("Asia/Dubai"))
        val here = SetupPlace(GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = false)
        repo.saveSetup(LocalSetup(here))
        val bundled = app.timetables.get("iacad_dubai")!!
        val before = repo.current()!!.official?.last
        assertTrue(before == null || before <= bundled.last!!)

        // An update brings IACAD's next weeks (stand-in minutes, made in the test); the stored weeks are not a week old.
        val target = today.plusDays(40)
        val extra = generateSequence(bundled.last!!.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= target }
            .joinToString("\n") { "$it,05:20,06:40,12:10,15:09,17:32,18:50" }
        app.timetables.store("iacad_dubai", Timetable.parse("iacad_dubai", bundled.encode() + "\n" + extra))
        repo.refreshLocal()
        val c = repo.current()!!
        assertEquals(target, c.official!!.last)
        // That day is now the published minute, not a calculated one.
        assertEquals(17 * 60 + 32, c.day(target).minutes[Prayer.MAGHRIB])
    }
}
