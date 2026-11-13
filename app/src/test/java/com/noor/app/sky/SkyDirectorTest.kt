package com.noor.app.sky

import com.noor.app.data.Scenery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * The real sky (0.10), the parts that are plain arithmetic: the weather the director invents (always within what the
 * shaders expect, changing smoothly through the day, arranged for the hour of colour and for the stars, true to each
 * scenery and season), the camera (the same mapping as `viewRay` in assets/sky/common.glsl), where the light falls
 * from, and the quality tier guessed for a phone. What the pictures look like is checked on the design bench
 * (tools/sky3d) and on an emulator (real-sky.yml), not here.
 */
class SkyDirectorTest {
    /** The Sun's height through a day at a mid latitude, good enough to drive the director. */
    private fun sunEl(f: Double) = asin(sin(25 * PI / 180) * sin(-18 * PI / 180) + cos(25 * PI / 180) * cos(-18 * PI / 180) * cos((f - 0.5) * 2 * PI)) * 180 / PI

    private fun values(s: SkyState) = listOf(s.cover, s.kind, s.mid, s.midForm, s.cirrus, s.veil, s.storm, s.showers, s.rain, s.mist, s.dust, s.wind, s.season, s.snow, s.wet, s.aurora, s.waterRough, s.cluster)

    @Test fun yearFollowsTheHemisphere() {
        val midwinter = Instant.parse("2026-12-21T12:00:00Z"); val midsummer = Instant.parse("2026-06-21T12:00:00Z")
        assertTrue(SkyDirector.yearPhase(midwinter, false).let { it < 0.02f || it > 0.98f })
        assertEquals(0.5f, SkyDirector.yearPhase(midsummer, false), 0.02f)
        assertEquals(0.5f, SkyDirector.yearPhase(midwinter, true), 0.02f)
        assertEquals(0f, SkyDirector.summer(0f), 1e-4f)
        assertEquals(1f, SkyDirector.summer(0.5f), 1e-4f)
    }

    @Test fun everyDayOfEverySceneryIsWithinBoundsAndRepeatable() {
        for (biome in Biome.entries) for (day in 0 until 120) {
            val year = (day % 12) / 12f
            val w = SkyDirector.weather(day * 1_000_003L + 77, biome, year)
            for (i in 0..48) {
                val f = i / 48.0
                val s = SkyDirector.at(biome, w, f, sunEl(f), year, 0.3f)
                for (v in values(s)) assertTrue("$biome day $day at $f: $v", v in 0f..1f)
                assertTrue(s.cover <= 0.95f && s.kind >= 0.05f)
                assertTrue("haze ${s.haze}", s.haze in 0.5f..3.2f)
                assertTrue("base ${s.base}", s.base in 1.0f..2.0f)
            }
            // The same day at the same place is the same sky.
            val again = SkyDirector.at(biome, SkyDirector.weather(day * 1_000_003L + 77, biome, year), 0.6, sunEl(0.6), year, 0.3f)
            assertEquals(values(SkyDirector.at(biome, w, 0.6, sunEl(0.6), year, 0.3f)), values(again))
        }
    }

    @Test fun theSkyNeverJumpsAsTheDayGoesBy() {
        // Dragging the Sun through the day must not make clouds pop: nothing switches, everything eases. (A minute of
        // the day moves any value by a few hundredths at most; a switch would move it by tenths.)
        for (biome in Biome.entries) for (day in 0 until 40) {
            val w = SkyDirector.weather(day * 7919L + 3, biome, 0.4f)
            var prev = SkyDirector.at(biome, w, 0.0, sunEl(0.0), 0.4f, 0.2f)
            for (m in 1 until 1440) {
                val f = m / 1440.0
                val s = SkyDirector.at(biome, w, f, sunEl(f), 0.4f, 0.2f)
                assertTrue("$biome day $day minute $m cover", abs(s.cover - prev.cover) < 0.05f)
                assertTrue("$biome day $day minute $m kind", abs(s.kind - prev.kind) < 0.05f)
                assertTrue("$biome day $day minute $m mid", abs(s.mid - prev.mid) < 0.05f)
                assertTrue("$biome day $day minute $m cirrus", abs(s.cirrus - prev.cirrus) < 0.05f)
                assertTrue("$biome day $day minute $m aurora", abs(s.aurora - prev.aurora) < 0.05f)
                prev = s
            }
        }
    }

    @Test fun theHourOfColourHasCloudToLightAndRoomForTheSun() {
        var counted = 0
        for (biome in Biome.entries) for (day in 0 until 60) {
            val w = SkyDirector.weather(day * 104_729L + 5, biome, 0.3f)
            if (w.at(0.72).overcast >= 0.35f) continue
            // The Sun a degree or two above the horizon.
            val s = SkyDirector.at(biome, w, 0.72, 1.5, 0.3f, 0.2f)
            counted++
            assertTrue("$biome day $day: something to light (${s.mid}, ${s.cirrus})", s.mid >= 0.29f && s.cirrus >= 0.29f)
            assertTrue("$biome day $day: room for the Sun (${s.cover})", s.cover <= 0.43f)
        }
        assertTrue(counted > 200)
    }

    @Test fun deepNightIsMostlyClearAndSceneriesKeepTheirCharacter() {
        for (day in 0 until 60) {
            val seed = day * 31_337L + 11
            val dunes = SkyDirector.weather(seed, Biome.DUNES, 0.5f)
            val noon = SkyDirector.at(Biome.DUNES, dunes, 0.5, 60.0, 0.5f, 0f)
            val night = SkyDirector.at(Biome.DUNES, dunes, 0.02, -40.0, 0.5f, 0f)
            // A desert storm raises dust rather than rain; the desert has no aurora, mist or snow.
            assertTrue(noon.rain <= 0.26f && night.rain <= 0.26f)
            assertEquals(0f, noon.aurora, 0f); assertEquals(0f, noon.snow, 0f); assertEquals(0f, noon.mist, 0f)
            // Summer in the desert is hazier than winter.
            val winter = SkyDirector.at(Biome.DUNES, SkyDirector.weather(seed, Biome.DUNES, 0f), 0.5, 40.0, 0f, 0f)
            assertTrue(noon.haze > winter.haze)

            val arctic = SkyDirector.weather(seed, Biome.ARCTIC, 0f)
            val day0 = SkyDirector.at(Biome.ARCTIC, arctic, 0.5, 5.0, 0f, 0f)
            val dark = SkyDirector.at(Biome.ARCTIC, arctic, 0.0, -30.0, 0f, 0f)
            assertEquals("no aurora in daylight", 0f, day0.aurora, 0f)
            assertEquals(0f, day0.rain, 0f)
            assertEquals(1f, day0.snow, 0f)
            if (arctic.at(0.0).overcast < 0.3f) assertTrue("the aurora shows on a clear winter night (${dark.aurora})", dark.aurora > 0.2f)

            val forest = SkyDirector.weather(seed, Biome.RAINFOREST, 0f)
            assertTrue("snow lies in the forest's winter", SkyDirector.at(Biome.RAINFOREST, forest, 0.5, 20.0, 0f, 0.3f).snow > 0.9f)
            assertEquals(0f, SkyDirector.at(Biome.RAINFOREST, forest, 0.5, 60.0, 0.5f, 0.3f).snow, 0f)
            // The savanna is green after its rains (summer), gold in the dry season.
            val grass = SkyDirector.weather(seed, Biome.SAVANNA, 0.5f)
            assertTrue(SkyDirector.at(Biome.SAVANNA, grass, 0.5, 60.0, 0.5f, 0f).season > 0.9f)
            assertTrue(SkyDirector.at(Biome.SAVANNA, grass, 0.5, 60.0, 0f, 0f).season < 0.1f)
        }
        // Night: less cloud than the same weather would have by day, storms apart.
        var clearer = 0; var all = 0
        for (day in 0 until 200) {
            val w = SkyDirector.weather(day * 613L, Biome.OCEAN, 0.4f)
            val st = w.at(0.02)
            if (st.storm > 0.05f) continue
            val base = (st.cumulus * 0.60f + st.overcast * 0.55f).coerceIn(0f, 0.95f)
            val n = SkyDirector.at(Biome.OCEAN, w, 0.02, -45.0, 0.4f, 0f)
            all++
            if (n.cover <= base * 0.5f + 1e-3f) clearer++
        }
        assertEquals(all, clearer)
    }

    @Test fun theCameraIsTheShadersCamera() {
        val w = 1080f; val h = 2340f; val focal = 1.15f * w; val horizon = 911f
        // The middle of the horizon line looks level, straight ahead.
        val ahead = RealSkyFrame.ray(w / 2, horizon, w, h, focal, horizon, false)
        assertEquals(0f, ahead[0], 1e-5f); assertEquals(0f, ahead[1], 1e-5f); assertEquals(1f, ahead[2], 1e-5f)
        // Every point of the horizon line is level; above it looks up, below it down.
        for (x in listOf(0f, 200f, 900f, 1080f)) assertEquals(0f, RealSkyFrame.ray(x, horizon, w, h, focal, horizon, false)[1], 1e-5f)
        assertTrue(RealSkyFrame.ray(540f, 300f, w, h, focal, horizon, false)[1] > 0.3f)
        assertTrue(RealSkyFrame.ray(540f, 1500f, w, h, focal, horizon, false)[1] < -0.3f)
        // Left of the middle is to the left; mirrored (right-to-left), to the right. Unit length.
        val left = RealSkyFrame.ray(100f, 700f, w, h, focal, horizon, false)
        val mirrored = RealSkyFrame.ray(100f, 700f, w, h, focal, horizon, true)
        assertTrue(left[0] < 0f)
        assertEquals(-left[0], mirrored[0], 1e-6f); assertEquals(left[1], mirrored[1], 1e-6f); assertEquals(left[2], mirrored[2], 1e-6f)
        assertEquals(1f, left[0] * left[0] + left[1] * left[1] + left[2] * left[2], 1e-5f)
        // The Sun rises and sets 30.24 % of the width from the middle: the gap the land leaves open for it.
        val rise = RealSkyFrame.ray(w * (0.5f - 0.3024f), horizon, w, h, focal, horizon, false)
        assertEquals(-RealSkyFrame.gapAzimuth(w, h, focal, horizon), kotlin.math.atan2(rise[0], rise[2]), 1e-4f)
        assertEquals(atan(0.3024f / 1.15f), RealSkyFrame.gapAzimuth(w, h, focal, h / 2), 1e-5f)
        assertEquals(0.0, RealSkyFrame.dirAzEl(0.3, 0.0)[1].toDouble(), 1e-6)
        assertEquals(sin(0.5).toFloat(), RealSkyFrame.dirAzEl(0.3, 0.5)[1], 1e-6f)
    }

    @Test fun tiersAreGuessedFromTheGpuAndMemory() {
        assertEquals(RealSky.Tier.ULTRA, RealSky.guess("Adreno (TM) 750|OpenGL ES 3.2", 12.0))
        assertEquals(RealSky.Tier.ULTRA, RealSky.guess("ANGLE (Samsung Xclipse 940) on Vulkan", 12.0))
        assertEquals(RealSky.Tier.ULTRA, RealSky.guess("Mali-G715-Immortalis MC11", 8.0))
        assertEquals(RealSky.Tier.HIGH, RealSky.guess("Adreno (TM) 740", 6.0))
        assertEquals(RealSky.Tier.HIGH, RealSky.guess("Adreno (TM) 660", 8.0))
        assertEquals(RealSky.Tier.MEDIUM, RealSky.guess("Adreno (TM) 619", 6.0))
        assertEquals(RealSky.Tier.MEDIUM, RealSky.guess("Mali-G52 MC2", 4.0))
        assertEquals(RealSky.Tier.LOW, RealSky.guess("PowerVR Rogue GE8320", 3.0))
        assertEquals(RealSky.Tier.MEDIUM, RealSky.guess("Some future GPU", 8.0))
        // Tiers only ever get lighter down the list.
        for (i in 1 until RealSky.Tier.entries.size) {
            val a = RealSky.Tier.entries[i - 1]; val b = RealSky.Tier.entries[i]
            assertTrue(b.cloudScale < a.cloudScale && b.sceneScale < a.sceneScale && b.steps > a.steps)
        }
    }

    @Test fun everySceneryHasAChoiceInSettings() {
        assertEquals(Biome.DUNES, Biome.of(Scenery.DUNES, null, null, null))
        assertEquals(Biome.RAINFOREST, Biome.of(Scenery.RAINFOREST, null, null, null))
        assertEquals(Biome.ARCTIC, Biome.of(Scenery.ARCTIC, null, null, null))
        assertEquals(Biome.OCEAN, Biome.of(Scenery.SEA, null, null, null))
        assertEquals(Biome.SAVANNA, Biome.of(Scenery.SAVANNA, null, null, null))
        assertEquals(Biome.CANYON, Biome.of(Scenery.CANYON, null, null, null))
        // The 0.9 choices still mean something.
        assertEquals(Biome.RAINFOREST, Biome.of(Scenery.HILLS, null, null, null))
        assertEquals(Biome.CANYON, Biome.of(Scenery.MOUNTAINS, null, null, null))
        assertEquals(6, Biome.entries.map { it.id }.toSet().size)
    }
}
