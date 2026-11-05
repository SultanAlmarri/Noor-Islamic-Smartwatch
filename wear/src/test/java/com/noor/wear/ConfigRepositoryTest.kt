package com.noor.wear

import com.noor.prayer.GeoPoint
import com.noor.prayer.LocalSetup
import com.noor.prayer.Profiles
import com.noor.prayer.SetupPlace
import com.noor.prayer.dhikr.TasbihPreset
import com.noor.prayer.dhikr.TasbihState
import com.noor.wear.data.ConfigSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.TimeZone

/**
 * Where the watch's times come from, end to end through the real storage: nothing, then set up on the watch, then the
 * phone's settings arriving, the user's choice between them, and travel with the watch's own location.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigRepositoryTest {

    @Test fun watchSetupPhoneSettingsAndTheChoiceBetweenThem() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorWearApp
        val repo = app.config
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))

        assertEquals(ConfigSource.NONE, repo.state.first().source)
        assertNull(repo.current())

        // Set up on the watch with its own position in Dubai: the IACAD timetable bundled with the app is used.
        val here = SetupPlace(GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", "AE", "AE-DU", fromGps = true)
        repo.saveSetup(LocalSetup(here))
        val local = repo.state.first()
        assertEquals(ConfigSource.WATCH, local.source)
        assertEquals(Profiles.DUBAI_IACAD.id, local.config!!.profileId)
        assertEquals("Asia/Dubai", local.config!!.zoneId)
        // The bundled IACAD table covers November 2026; while it does, the watch carries it like the phone would.
        if (!LocalDate.now().isAfter(LocalDate.of(2026, 11, 30))) assertTrue("bundled official times attached", (local.config!!.official?.size ?: 0) > 0)

        // The phone sends its settings later. The user set the watch up on purpose, so the watch keeps its own...
        val phone = TestConfigs.dubai("ar").copy(placeEn = "Dubai Marina", updatedAtEpochMs = 1L)
        repo.save(phone.encode())
        val both = repo.state.first()
        assertEquals(ConfigSource.WATCH, both.source)
        assertTrue(both.hasPhoneConfig)
        // ...until they choose the phone's.
        repo.useWatchSettings(false)
        assertEquals(ConfigSource.PHONE, repo.state.first().source)
        assertEquals("Dubai Marina", repo.current()!!.placeEn)
        repo.useWatchSettings(true)
        assertEquals("Dubai", repo.current()!!.placeEn)

        // Travelling: with the watch's own position, the time zone follows the watch.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Muscat"))
        repo.refreshLocal()
        assertEquals("Asia/Muscat", repo.current()!!.zoneId)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))

        // A city keeps its own zone wherever the watch is.
        val city = SetupPlace(GeoPoint(21.4225, 39.8262, 277.0), "Asia/Riyadh", "Makkah", "مكة المكرمة", "SA", null, fromGps = false)
        repo.saveSetup(repo.state.first().setup!!.copy(place = city))
        assertEquals("Asia/Riyadh", repo.current()!!.zoneId)
        assertEquals(Profiles.byId(repo.current()!!.profileId)!!.id, com.noor.prayer.authority.Authorities.forPlace("SA", null).profile.id)

        // The counter survives restarts and keeps its day total.
        val day = LocalDate.of(2026, 11, 5)
        val counted = (1..7).fold(TasbihState().choose(TasbihPreset.ISTIGHFAR)) { s, _ -> s.increment(day).first }
        app.tasbih.save(counted)
        assertEquals(7, app.tasbih.current().todayOn(day))
        assertNotNull(app.tasbih.current())
    }
}
