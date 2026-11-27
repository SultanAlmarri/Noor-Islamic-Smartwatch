package com.noor.prayer

import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.SoundIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 0.26: the five sound effects of 0.18 were replaced by short alert sounds. Settings stored with the old ids (on
 * the phone, or sent to the watch) keep working: each old id reads as the sound that replaced it.
 */
class SoundIdsTest {
    private val old = listOf("fx.birds", "fx.rooster", "fx.waves", "fx.rain", "fx.stream")

    @Test fun everyOldEffectHasItsOwnReplacement() {
        assertEquals(old.toSet(), SoundIds.RENAMED.keys)
        // (Four new sounds for now: until a beep recording is added, two old ids share the nuthatch.)
        assertTrue(SoundIds.RENAMED.values.toSet().size >= 4)
        for ((from, to) in SoundIds.RENAMED) {
            assertTrue(SoundIds.isValid(to) && to.startsWith("fx."), to)
            assertFalse(to in SoundIds.RENAMED.keys, "$from → $to is itself renamed")
            assertEquals(to, SoundIds.current(from))
        }
        // The default sound is one of the new ones; other ids pass through unchanged.
        assertTrue(SoundIds.DEFAULT_SOUND in SoundIds.RENAMED.values)
        assertEquals("fx.chickadee", SoundIds.current("fx.birds"))
        for (id in listOf("adhan.makkah", "custom.a1", SoundIds.RANDOM, "fx.chickadee")) assertEquals(id, SoundIds.current(id))
        assertNull(SoundIds.current(null))
    }

    @Test fun storedSettingsWithOldIdsReadAsTheNewSounds() {
        // As 0.25 wrote them: Shuruq with the rooster, a favourite list holding the birds, an alarm with the rain.
        val s = AlertSettings.decode("v=1;on=1;SHURUQ=1,SOUND,fx.rooster,80,1,60;fav=adhan.makkah,fx.birds;" +
            "alarms=1,1,ASR,A,5,127,LIBRARY,fx.rain,1,10,,1")
        assertEquals("fx.pip_pip", s.alert(Prayer.SHURUQ).soundId)
        assertEquals(AlertMode.SOUND, s.alert(Prayer.SHURUQ).mode)
        assertEquals(listOf("adhan.makkah", "fx.chickadee"), s.favourites)
        assertEquals(AlarmSound.LIBRARY, s.alarms.single().sound)
        assertEquals("fx.nuthatch", s.alarms.single().customId)
        // Written again, only the new ids are stored.
        assertTrue(old.none { it in s.encode() }, s.encode())
        assertEquals(s, AlertSettings.decode(s.encode()))
    }
}
