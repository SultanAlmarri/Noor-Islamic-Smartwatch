package com.noor.alerts

import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.SoundIds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 0.26: the player is given each prayer's own rise ("Start softly"), not the one setting of the alarms: off, the
 * default, means the Adhan at its full volume from the first second, whatever the old global setting says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHostApp::class, sdk = [34])
class SoftStartDispatchTest {
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication() as TestHostApp
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L,
    )

    @Test fun eachPrayerRisesAsItsOwnSettingSays() {
        val adhan = SoundEntry(SoundIds.DEFAULT_ADHAN, SoundKind.ADHAN, "Makkah", "مكة", null, null, emptySet(), false,
            "sounds/full.ogg", 176.0, "sounds/short.ogg", 16.0, null)
        SoundLibrary.replaceForTests(SoundLibrary(listOf(adhan), { true }, null))
        val date = LocalDate.of(2026, 11, 7)
        // The old one setting at 20 s (the alarms keep it); Asr starts softly over 10 s, Maghrib at full volume.
        val s = AlertSettings(enabled = true, gradualSeconds = 20)
            .let { it.with(Prayer.ASR, it.alert(Prayer.ASR).copy(soundId = SoundIds.DEFAULT_ADHAN, gradualSeconds = 10)) }
            .let { it.with(Prayer.MAGHRIB, it.alert(Prayer.MAGHRIB).copy(soundId = SoundIds.DEFAULT_ADHAN, gradualSeconds = 0)) }
        fun started(p: Prayer): PlayRequest {
            val at = dubai.day(date).instant(p)!!
            AlertDispatcher.dispatch(app, dubai, s, p, date, at, s.alert(p), test = false, firedAtMs = at.toEpochMilli())
            val i = shadowOf(app).nextStartedService
            assertEquals(AdhanService.ACTION_PLAY, i?.action)
            return PlayRequest.from(i!!)!!
        }
        assertEquals(10, started(Prayer.ASR).gradualSeconds)
        assertEquals(0, started(Prayer.MAGHRIB).gradualSeconds)
        // And a request carries it through the service's intent unchanged.
        val req = started(Prayer.ASR)
        assertEquals(10, PlayRequest.from(req.toIntent(app))!!.gradualSeconds)
        SoundLibrary.replaceForTests(null)
    }
}
