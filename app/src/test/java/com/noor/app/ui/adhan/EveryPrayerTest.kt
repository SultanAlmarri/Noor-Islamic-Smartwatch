package com.noor.app.ui.adhan

import com.noor.alerts.SoundLibrary
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (0.26 review) The "Every prayer" rows' third state and their Undo: what reads as "Mixed" (and only that), that a
 * change made there over prayers that differed is offered back, and that Undo gives every prayer back exactly what it
 * had, and nothing else.
 */
class EveryPrayerTest {
    private val lib = SoundLibrary(emptyList(), { true }, null)

    /** Every prayer alike: on, the Adhan of one voice, 80%, no rise, vibrating (sunrise off, as by default). */
    private val alike: AlertSettings = AlertSettings(enabled = true).withEach(FIVE_PRAYERS) { _, a ->
        a.copy(enabled = true, mode = AlertMode.ADHAN, soundId = "adhan.makkah", volumePercent = 80, gradualSeconds = 0, vibrate = true)
    }

    /** Every prayer's alert as read, and the voices Random draws from, the same in both. */
    private fun assertSame(a: AlertSettings, b: AlertSettings) {
        for (p in Prayer.entries) assertEquals("$p", a.alert(p), b.alert(p))
        assertEquals(a.favourites, b.favourites)
    }

    @Test fun prayersAlikeAreNotMixed() {
        assertEquals(80, alike.sharedVolume())
        assertEquals(Shared.OFF, alike.sharedSoftStart())
        assertEquals(Shared.ON, alike.sharedVibrate())
        for (w in EverySetting.entries) assertFalse("$w", alike.differIn(w, lib, emptyList()))
    }

    @Test fun prayersThatDifferReadAsMixed() {
        val s = alike.with(Prayer.ASR, alike.alert(Prayer.ASR).copy(volumePercent = 45, gradualSeconds = 10, vibrate = false))
            .with(Prayer.ISHA, alike.alert(Prayer.ISHA).copy(mode = AlertMode.NOTIFICATION))
        assertNull(s.sharedVolume())
        assertEquals(Shared.MIXED, s.sharedSoftStart())
        assertEquals(Shared.MIXED, s.sharedVibrate())
        for (w in EverySetting.entries) assertTrue("$w", s.differIn(w, lib, emptyList()))
        // (One prayer's page is never mixed.)
        assertEquals(45, s.sharedVolume(Prayer.ASR))
        assertEquals(Shared.ON, s.sharedSoftStart(Prayer.ASR))
        assertEquals(Shared.OFF, s.sharedVibrate(Prayer.ASR))
    }

    @Test fun whatDoesNotSoundDoesNotMakeItMixed() {
        // A prayer on a notification has no volume of Minhad's; a prayer switched off does not count while others are on.
        val s = alike.with(Prayer.ISHA, alike.alert(Prayer.ISHA).copy(mode = AlertMode.NOTIFICATION, volumePercent = 20))
            .with(Prayer.MAGHRIB, alike.alert(Prayer.MAGHRIB).copy(enabled = false, vibrate = false))
        assertEquals(80, s.sharedVolume())
        assertEquals(Shared.ON, s.sharedVibrate())
        assertFalse(s.differIn(EverySetting.VOLUME, lib, emptyList()))
        assertFalse(s.differIn(EverySetting.VIBRATE, lib, emptyList()))
        // With every alert off, the prayers that would sound are the ones compared.
        assertEquals(Shared.MIXED, s.copy(enabled = false).sharedVibrate())
    }

    @Test fun undoGivesEveryPrayerBackWhatItHadAndNothingElse() {
        val before = alike.with(Prayer.ASR, alike.alert(Prayer.ASR).copy(volumePercent = 45, vibrate = false))
            .with(Prayer.MAGHRIB, alike.alert(Prayer.MAGHRIB).copy(gradualSeconds = 10))
        // Volume set to 60 for every prayer, then (while the line is shown) Fajr switched off.
        val after = before.withEach { _, a -> a.copy(volumePercent = 60) }.let { it.with(Prayer.FAJR, it.alert(Prayer.FAJR).copy(enabled = false)) }
        val undone = after.undo(EveryUndo(EverySetting.VOLUME, before))
        for (p in Prayer.entries) assertEquals("$p", before.alert(p).volumePercent, undone.alert(p).volumePercent)
        assertFalse("what was changed since is kept", undone.alert(Prayer.FAJR).enabled)
        assertFalse(undone.alert(Prayer.ASR).vibrate)
        assertEquals(10, undone.alert(Prayer.MAGHRIB).gradualSeconds)

        // Vibration and the soft start.
        val v = before.withEach { _, a -> a.copy(vibrate = true) }.undo(EveryUndo(EverySetting.VIBRATE, before))
        assertSame(before, v)
        val r = before.withEach { _, a -> a.copy(gradualSeconds = 0) }.undo(EveryUndo(EverySetting.SOFT_START, before))
        assertSame(before, r)
    }

    @Test fun undoOfASoundForEveryPrayer() {
        val before = alike.with(Prayer.ISHA, alike.alert(Prayer.ISHA).copy(mode = AlertMode.NOTIFICATION))
            .with(Prayer.DHUHR, alike.alert(Prayer.DHUHR).copy(mode = AlertMode.ADHAN_SHORT, soundId = "adhan.egypt"))
            .copy(favourites = listOf("adhan.makkah"))
        val chosen = before.withEach(FIVE_PRAYERS) { _, a -> a.copy(mode = AlertMode.SOUND, soundId = "alt.birds") }.copy(favourites = listOf("x", "y"))
        val undone = chosen.undo(EveryUndo(EverySetting.SOUND, before))
        assertSame(before, undone)
    }
}
