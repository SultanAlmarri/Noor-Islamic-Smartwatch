package com.noor.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val start = text.indexOf("\"id\": \"adhan.makkah\"")
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

    /**
     * 0.17: the bundled library is the owner's curated set. Every sound in it is a real recording under a licence that
     * allows redistribution and commercial use, credited with its source; every file it names is bundled; nothing of
     * the old library (generated "alt." sounds, other Adhans) is left on the phone or the watch.
     */
    @Test fun bundledLibraryIsCreditedAndComplete() {
        val allowed = setOf("Public domain", "CC0", "CC BY 3.0", "CC BY 4.0", "CC BY-SA 3.0", "CC BY-SA 4.0",
            // 0.18: the owner's own recordings (Dubai), which he states he holds the rights to; their file in the repository
            // is the source, and the rights statement is written down with them.
            "Owner's rights")
        for ((assets, label) in listOf(File("src/main/assets") to "phone", File("../wear/src/main/assets") to "watch")) {
            val text = File(assets, "sounds/library.json").readText()
            val ids = Regex("\"id\":\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
            assertTrue("$label: adhan.makkah", "adhan.makkah" in ids)
            assertTrue("$label: fx.birds", "fx.birds" in ids)
            assertTrue("$label: only adhan.* and fx.*: $ids", ids.all { it.startsWith("adhan.") || it.startsWith("fx.") })
            val licences = Regex("\"licence\":\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
            assertEquals("$label: one licence per sound", ids.size, licences.size)
            assertTrue("$label: licences $licences", licences.all { it in allowed })
            val sources = Regex("\"source\":\\s*\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(ids.size, sources.size)
            assertTrue("$label: every sound names its source", sources.all { it.startsWith("https://") || (it.startsWith("owner:") && File("../" + it.removePrefix("owner:")).isFile) })
            val owners = licences.count { it == "Owner's rights" }
            assertEquals("$label: an owner's recording says whose rights", owners, Regex("\"provenance\":\\s*\"[^\"]{40,}\"").findAll(text).count())
            val bundled = File(assets, "sounds").list()!!.filter { it.endsWith(".ogg") }.toSet()
            val named = Regex("\"file\":\\s*\"sounds/([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toSet()
            // The phone bundles every file it names; the watch bundles the openings and the effects (whole Adhans are
            // sent from the phone), and nothing it does not name.
            if (label == "phone") assertEquals(named, bundled) else assertTrue("$label: $bundled", named.containsAll(bundled))
            assertTrue("$label: the opening of every Adhan", ids.filter { it.startsWith("adhan.") }
                .all { "${it.removePrefix("adhan.").let { id -> "adhan_${id}_short.ogg" }}" in bundled })
            assertFalse("$label: $bundled", bundled.any { it.startsWith("alt_") })
        }
    }
}
