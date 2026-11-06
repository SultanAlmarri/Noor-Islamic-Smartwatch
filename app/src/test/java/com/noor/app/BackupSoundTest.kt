package com.noor.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The backup Adhan (played by Android's notification system when Noor may not start its player) must be exactly a
 * recording of the credited, licensed library, so the credits and licence in SOUNDS.md cover it.
 */
class BackupSoundTest {
    @Test fun backupAdhanIsTheCreditedMakkahShortVersion() {
        val backup = File("../alerts/src/main/res/raw/noor_backup_adhan.ogg")
        val library = File("src/main/assets/sounds/library.json")
        // Plain JVM test: Android's org.json is only a stub here, so the entry is read as text.
        val text = library.readText()
        val start = text.indexOf("\"id\": \"adhan.makkah_maghrib_2012\"")
        assertTrue(start >= 0)
        val entry = text.substring(start, text.indexOf("\"id\":", start + 10).takeIf { it > 0 } ?: text.length)
        val shortFile = Regex("\"short\":\\s*\\{\\s*\"file\":\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1]
        val short = File("src/main/assets/$shortFile")
        assertTrue(backup.isFile && short.isFile)
        assertArrayEquals(short.readBytes(), backup.readBytes())
        val licence = Regex("\"licence\":\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1]
        val author = Regex("\"author\":\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1]
        assertEquals("CC BY 3.0", licence)
        assertTrue(author.isNotBlank())
    }
}
