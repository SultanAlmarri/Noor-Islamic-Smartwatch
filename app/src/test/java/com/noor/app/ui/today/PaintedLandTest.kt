package com.noor.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * (0.22) The medium graphics' painted sceneries (PaintedLand): the light changes smoothly with the Sun, from the side it
 * shines from; every painted scenery has its seven lights, of the size and with the horizon the app draws them at,
 * with the sky clear above the land, and a skyline for the Sun's ring.
 */
class PaintedLandTest {
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    /** The share of each light at an elevation (the two of [PaintedLand.weights], summed by name). */
    private fun mix(el: Double, evening: Boolean): Map<String, Float> {
        val (pair, t) = PaintedLand.weights(el, evening)
        val m = HashMap<String, Float>()
        m[pair.first] = (m[pair.first] ?: 0f) + (1f - t)
        m[pair.second] = (m[pair.second] ?: 0f) + t
        return m
    }

    @Test fun theLightChangesSmoothlyWithTheSunFromItsOwnSide() {
        for (evening in listOf(false, true)) {
            var last = mix(-30.0, evening)
            var el = -30.0
            while (el <= 80.0) {
                val now = mix(el, evening)
                // No light jumps by more than a sliver between two steps of a tenth of a degree.
                for (k in (now.keys + last.keys)) assertTrue("$k at $el (evening $evening)", kotlin.math.abs((now[k] ?: 0f) - (last[k] ?: 0f)) < 0.03f)
                last = now; el += 0.1
            }
            assertEquals(1f, mix(-20.0, evening)["night"] ?: 0f, 1e-4f)
            assertEquals(1f, mix(80.0, evening)["noon"] ?: 0f, 1e-4f)
        }
        // The low Sun lights the land from the side it stands on: the left in the morning, the right in the evening.
        assertTrue((mix(4.0, evening = false)["golden_left"] ?: 0f) > 0.9f)
        assertTrue((mix(4.0, evening = true)["golden_right"] ?: 0f) > 0.9f)
        assertTrue((mix(18.0, evening = false)["day_left"] ?: 0f) > 0.9f)
    }

    @Test fun theDunesKeepTheirLayeredLand() {
        assertNull(PaintedLand.idOf(Terrain.DUNES))
        assertEquals(setOf("pnw", "meadow", "arctic", "ocean", "canyon"), Terrain.entries.mapNotNull { PaintedLand.idOf(it) }.toSet())
    }

    private companion object { const val MAX_RISE = 0.225 }

    @Test fun everyPaintedSceneryHasItsSevenLights() {
        for (t in Terrain.entries) {
            val id = PaintedLand.idOf(t) ?: continue
            val dir = File(assets, "medium/$id")
            val meta = org.json.JSONObject(File(dir, "meta.json").readText())
            assertEquals(PaintedLand.HORIZON, meta.getInt("horizon"))
            val sky = meta.getJSONArray("skyline")
            assertTrue("$id skyline", sky.length() >= 100)
            // The land stays below the countdown: its white digits stand about a quarter of the width over the horizon,
            // and a snowy peak behind them made the time unreadable (the first arctic, 18 November 2026).
            for (j in 0 until sky.length()) assertTrue("$id rises to ${sky.getDouble(j)} of the width at strip $j", sky.getDouble(j) <= MAX_RISE)
            for (s in PaintedLand.STATES) {
                val f = File(dir, "$s.webp")
                assertTrue("$id/$s", f.isFile && f.length() > 10_000)
            }
            // The Settings card for the medium level is there too.
            assertTrue(File(assets.parentFile, "res/drawable-nodpi/scenery_medium_$id.webp").isFile)
        }
    }
}
