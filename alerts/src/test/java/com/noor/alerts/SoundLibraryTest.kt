package com.noor.alerts

import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.PrayerAlert
import com.noor.prayer.alerts.SoundIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SoundLibraryTest {

    @get:Rule val tmp = TemporaryFolder()

    // A made-up catalogue in the shape of the bundled one (0.17 ids). The Fajr entry and the opening-only entry exist
    // to exercise those paths; the bundled library itself is checked by the app's BackupSoundTest.
    private val json = """
        {"sounds":[
          {"id":"adhan.makkah","kind":"adhan","title_en":"Makkah","title_ar":"مكة","categories":["renowned","traditional"],
           "file":"sounds/adhan_makkah.ogg","seconds":180,"short":{"file":"sounds/adhan_makkah_short.ogg","seconds":20},
           "credit":{"author":"3omar Faruq","licence":"CC BY 3.0","licence_url":"https://creativecommons.org/licenses/by/3.0","source":"https://commons.wikimedia.org/x","changes":"Trimmed"}},
          {"id":"adhan.dubai_fajr","kind":"adhan","title_en":"Dubai (Fajr)","title_ar":"دبي (الفجر)","categories":["fajr"],"fajr":true,
           "file":"sounds/adhan_dubai_fajr.ogg","seconds":200,"short":{"file":"sounds/adhan_dubai_fajr_short.ogg","seconds":25}},
          {"id":"adhan.alquds","kind":"adhan","title_en":"Al-Quds (opening)","title_ar":"القدس","categories":["renowned"],
           "short":{"file":"sounds/adhan_alquds_short.ogg","seconds":29},"note_en":"Opening only"},
          {"id":"fx.birds","kind":"alt","title_en":"Birds","title_ar":"عصافير","categories":["nature"],"file":"sounds/fx_birds.ogg","seconds":31.6,
           "short":{"file":"sounds/fx_birds_short.ogg","seconds":1.8}},
          {"id":"fx.rain","kind":"alt","title_en":"Rain","title_ar":"مطر","categories":["nature"],"file":"sounds/fx_rain.ogg","seconds":30.0,
           "short":{"file":"sounds/fx_rain_short.ogg","seconds":4.0}}
        ]}
    """.trimIndent()

    private val phoneFiles = setOf("sounds/adhan_alquds_short.ogg", "sounds/adhan_makkah.ogg", "sounds/adhan_makkah_short.ogg", "sounds/adhan_dubai_fajr.ogg",
        "sounds/adhan_dubai_fajr_short.ogg", "sounds/fx_birds.ogg", "sounds/fx_birds_short.ogg", "sounds/fx_rain.ogg", "sounds/fx_rain_short.ogg")
    private val watchFiles = phoneFiles - setOf("sounds/adhan_makkah.ogg", "sounds/adhan_dubai_fajr.ogg")

    private fun lib(files: Set<String>, transfer: File? = null) = SoundLibrary(SoundLibrary.parse(json), { it in files }, transfer)

    @Test fun parsesCatalogueWithCredits() {
        val l = lib(phoneFiles)
        assertEquals(3, l.adhans.size)
        assertFalse(l.byId("adhan.alquds")!!.hasFullRecording)
        assertEquals("Opening only", l.byId("adhan.alquds")!!.note(false))
        assertEquals(listOf("fx.rain", "fx.birds"), l.sounds.map { it.id }) // sorted by duration
        assertEquals(1.8, l.byId("fx.birds")!!.shortSeconds!!, 0.0) // effects carry a short version too
        val m = l.byId("adhan.makkah")!!
        assertEquals("CC BY 3.0", m.credit!!.licence)
        assertEquals(20.0, m.shortSeconds!!, 0.0)
        assertTrue(l.byId("adhan.dubai_fajr")!!.fajr)
    }

    @Test fun fajrAdhanIsOfferedOnlyForFajr() {
        val l = lib(phoneFiles)
        assertEquals("adhan.dubai_fajr", l.adhansFor(Prayer.FAJR).first().id)
        assertFalse(l.adhansFor(Prayer.ASR).any { it.fajr })
        val r = l.resolve(Prayer.ASR, PrayerAlert(true, AlertMode.ADHAN, "adhan.dubai_fajr"), null)!!
        assertEquals("adhan.makkah", r.entry!!.id)
        assertEquals(Substitution.FAJR_ADHAN_OUTSIDE_FAJR, r.substitution)
    }

    @Test fun resolvesEachModeAndSaysWhenItSubstitutes() {
        val phone = lib(phoneFiles)
        val full = phone.resolve(Prayer.DHUHR, PrayerAlert(true, AlertMode.ADHAN, null), null)!!
        assertEquals(SoundSource.Asset("sounds/adhan_makkah.ogg"), full.source); assertNull(full.substitution); assertFalse(full.short)
        val short = phone.resolve(Prayer.DHUHR, PrayerAlert(true, AlertMode.ADHAN_SHORT, SoundIds.DEFAULT_ADHAN), null)!!
        assertEquals(SoundSource.Asset("sounds/adhan_makkah_short.ogg"), short.source); assertTrue(short.short)
        val sound = phone.resolve(Prayer.SHURUQ, PrayerAlert(true, AlertMode.SOUND, "fx.rain"), null)!!
        assertEquals(SoundSource.Asset("sounds/fx_rain.ogg"), sound.source)
        assertNull(phone.resolve(Prayer.ASR, PrayerAlert(true, AlertMode.NOTIFICATION, null), null))
        // A recording that only has its opening plays the short version even when "full" is chosen, and says why.
        val opening = phone.resolve(Prayer.ASR, PrayerAlert(true, AlertMode.ADHAN, "adhan.alquds"), null)!!
        assertEquals(SoundSource.Asset("sounds/adhan_alquds_short.ogg"), opening.source)
        assertEquals(Substitution.NO_FULL_RECORDING, opening.substitution)
        // The watch has no full recordings: it plays the short one and says so.
        val watch = lib(watchFiles).resolve(Prayer.FAJR, PrayerAlert(true, AlertMode.ADHAN, "adhan.dubai_fajr"), null)!!
        assertEquals(SoundSource.Asset("sounds/adhan_dubai_fajr_short.ogg"), watch.source)
        assertEquals(Substitution.FULL_NOT_ON_DEVICE, watch.substitution)
        // A missing custom sound falls back to the default sound (fx.birds), openly.
        val gone = phone.resolve(Prayer.ASR, PrayerAlert(true, AlertMode.CUSTOM, "custom.gone"), CustomSounds(tmp.newFolder()) { 3.0 })!!
        assertEquals(Substitution.CUSTOM_MISSING, gone.substitution)
        assertEquals(SoundIds.DEFAULT_SOUND, gone.entry!!.id)
        assertEquals("fx.birds", gone.entry!!.id)
    }

    @Test fun fullRecordingSentToTheWatchIsUsed() {
        val dir = tmp.newFolder()
        File(dir, "adhan.makkah.ogg").writeBytes(ByteArray(100))
        val r = lib(watchFiles, dir).resolve(Prayer.ISHA, PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_ADHAN), null)!!
        assertEquals(SoundSource.LocalFile(File(dir, "adhan.makkah.ogg")), r.source)
        assertNull(r.substitution)
        // An imported sound sent from the phone (any format) plays on the watch, which has no import store of its own.
        File(dir, "custom.abc-1.m4a").writeBytes(ByteArray(10))
        File(dir, "custom.abc-2.m4a.part").writeBytes(ByteArray(10)) // an interrupted transfer is never used
        val c = lib(watchFiles, dir).resolve(Prayer.ISHA, PrayerAlert(true, AlertMode.CUSTOM, "custom.abc-1"), null)!!
        assertEquals(SoundSource.LocalFile(File(dir, "custom.abc-1.m4a")), c.source)
        val partial = lib(watchFiles, dir).resolve(Prayer.ISHA, PrayerAlert(true, AlertMode.CUSTOM, "custom.abc-2"), null)!!
        assertEquals(Substitution.CUSTOM_MISSING, partial.substitution)
    }

    @Test fun customSoundsImportRenameReplaceDelete() {
        val dir = tmp.newFolder()
        var duration = 12.5
        val store = CustomSounds(dir) { f -> if (f.readBytes().first() == 0x49.toByte()) duration else null } // "I" = playable
        val ok = store.importStream(ByteArrayInputStream("ID3-audio".toByteArray()), "my_adhan.mp3", "mp3") as ImportResult.Ok
        assertEquals("my adhan", ok.sound.name)
        assertTrue(SoundIdsTestHelper.valid(ok.sound.id))
        assertTrue(store.file(ok.sound)!!.isFile)
        // Not audio: rejected, nothing stored.
        assertEquals(ImportResult.Rejected(ImportResult.Reason.NOT_AUDIO), store.importStream(ByteArrayInputStream("garbage".toByteArray()), "x.mp3", "mp3"))
        assertEquals(1, store.list().size)
        store.rename(ok.sound.id, "Grandfather’s Adhan")
        assertEquals("Grandfather’s Adhan", store.get(ok.sound.id)!!.name)
        // Replace keeps id and name (every prayer using it keeps working) and swaps the file.
        duration = 30.0
        val rep = store.importStream(ByteArrayInputStream("ID3-new".toByteArray()), "other.m4a", "m4a", replaceId = ok.sound.id) as ImportResult.Ok
        assertEquals(ok.sound.id, rep.sound.id); assertEquals("Grandfather’s Adhan", rep.sound.name); assertEquals(30.0, rep.sound.seconds, 0.0)
        assertEquals(1, dir.listFiles()!!.count { it.name.endsWith(".mp3") || it.name.endsWith(".m4a") })
        // Survives a "restart" (a new store over the same folder).
        assertEquals(rep.sound, CustomSounds(dir) { 1.0 }.get(ok.sound.id))
        store.delete(ok.sound.id)
        assertTrue(store.list().isEmpty())
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".m4a") })
    }

    @Test fun acceptsOnlyAudioFormats() {
        assertEquals("mp3", CustomSounds.extensionFor("audio/mpeg", null))
        assertEquals("m4a", CustomSounds.extensionFor("audio/mp4", "x.m4a"))
        assertEquals("wav", CustomSounds.extensionFor(null, "Adhan.WAV"))
        assertEquals("aac", CustomSounds.extensionFor("audio/aac", null))
        assertNull(CustomSounds.extensionFor("application/pdf", "file.pdf"))
        assertNull(CustomSounds.extensionFor(null, "virus.exe"))
    }

    @Test fun randomFromFavouritesPlaysAFavouriteFittingThePrayer() {
        val phone = lib(phoneFiles)
        val favs = listOf("adhan.makkah", "adhan.alquds", "adhan.dubai_fajr", "fx.birds")
        val random = PrayerAlert(true, AlertMode.ADHAN_SHORT, SoundIds.RANDOM)
        val days = (0L until 12L).map { java.time.LocalDate.of(2026, 11, 7).plusDays(it) }
        // Other prayers: only non-Fajr Adhans from the favourites (never the Fajr Adhan, never an alert sound).
        val picked = days.map { d -> phone.resolve(Prayer.ASR, random, null, favs, d)!!.entry!!.id }
        assertTrue(picked.all { it == "adhan.makkah" || it == "adhan.alquds" })
        assertEquals(setOf("adhan.makkah", "adhan.alquds"), picked.toSet())
        // The full Adhan: a favourite that exists only as an opening is skipped (it would only play its opening).
        val randomFull = random.copy(mode = AlertMode.ADHAN)
        assertTrue(days.all { d -> phone.resolve(Prayer.ASR, randomFull, null, favs, d)!!.entry!!.id == "adhan.makkah" })
        // Fajr: its own recording.
        assertEquals("adhan.dubai_fajr", phone.resolve(Prayer.FAJR, random, null, favs, days[0])!!.entry!!.id)
        // The same day and prayer resolve to the same recording on phone and watch (the watch plays the short one it has).
        assertEquals(phone.resolve(Prayer.ISHA, random, null, favs, days[3])!!.entry!!.id,
            lib(watchFiles).resolve(Prayer.ISHA, random, null, favs, days[3])!!.entry!!.id)
        // No usable favourites: the default Adhan.
        assertEquals("adhan.makkah", phone.resolve(Prayer.DHUHR, random, null, emptyList(), days[0])!!.entry!!.id)
    }
}

private object SoundIdsTestHelper { fun valid(id: String) = SoundIds.isValid(id) && SoundIds.isCustom(id) }
