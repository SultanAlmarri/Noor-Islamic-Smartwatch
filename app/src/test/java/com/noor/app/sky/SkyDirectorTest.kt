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
import kotlin.math.max
import kotlin.math.sin

/**
 * The real sky, the parts that are plain arithmetic: the weather the director invents (always within what the
 * renderer expects, changing smoothly through the day, arranged for the hour of colour and for the stars, true to each
 * scenery and season) and the cloud sets it chooses, the camera (the same mapping as `viewRay` in
 * assets/sky2/common.glsl), which of the rendered Suns light a moment, and the quality tier guessed for a phone. What the pictures look like is checked on the design bench
 * (tools/sky3d) and on an emulator (real-sky.yml), not here.
 */
class SkyDirectorTest {
    /** The Sun's height through a day at a mid latitude, good enough to drive the director. */
    private fun sunEl(f: Double) = asin(sin(25 * PI / 180) * sin(-18 * PI / 180) + cos(25 * PI / 180) * cos(-18 * PI / 180) * cos((f - 0.5) * 2 * PI)) * 180 / PI

    private fun values(s: SkyState) = listOf(s.cover, s.kind, s.mid, s.midForm, s.cirrus, s.veil, s.storm, s.showers, s.rain, s.mist, s.dust, s.wind, s.season, s.snow, s.wet, s.aurora, s.fog, s.waterRough, s.cluster)

    @Test fun yearFollowsTheHemisphere() {
        val midwinter = Instant.parse("2026-12-21T12:00:00Z"); val midsummer = Instant.parse("2026-06-21T12:00:00Z")
        assertTrue(SkyDirector.yearPhase(midwinter, false).let { it < 0.02f || it > 0.98f })
        assertEquals(0.5f, SkyDirector.yearPhase(midsummer, false), 0.02f)
        assertEquals(0.5f, SkyDirector.yearPhase(midwinter, true), 0.02f)
        assertEquals(0f, SkyDirector.summer(0f), 1e-4f)
        assertEquals(1f, SkyDirector.summer(0.5f), 1e-4f)
    }

    @Test fun theSeasonsTurnSmoothlyAndEachHasItsTime() {
        // Each season is whole at its fullest and for a while to either side; between two, the one passes into the
        // other without a step; the year closes on itself; and the design bench works it out the same way.
        for (i in 0 until 4) {
            val t = Seasons.between(Seasons.FULL[i] + 0.01f)
            assertEquals(i, t.from); assertEquals(0f, t.mix, 1e-4f)
            val before = Seasons.between(Seasons.FULL[i] - 0.01f)
            assertEquals(i, before.to); assertEquals(1f, before.mix, 1e-4f)
        }
        fun share(year: Float, season: Int): Float = Seasons.between(year).let { (if (it.from == season) 1f - it.mix else 0f) + (if (it.to == season) it.mix else 0f) }
        for (season in 0 until 4) {
            var prev = share(0f, season)
            for (k in 1..2000) {
                val now = share(k / 2000f, season)
                assertTrue("season $season steps at ${k / 2000f}", abs(now - prev) < 0.01f)
                prev = now
            }
            assertEquals(share(0f, season), share(1f, season), 1e-4f)
        }
        // The middle of November is autumn; the end of January winter; July summer (northern hemisphere).
        assertEquals("autumn", Seasons.between(SkyDirector.yearPhase(Instant.parse("2026-11-16T12:00:00Z"), false)).let { Seasons.NAMES[if (it.mix < 0.5f) it.from else it.to] })
        assertEquals("winter", Seasons.between(SkyDirector.yearPhase(Instant.parse("2026-01-28T12:00:00Z"), false)).let { Seasons.NAMES[if (it.mix < 0.5f) it.from else it.to] })
        assertEquals("summer", Seasons.between(SkyDirector.yearPhase(Instant.parse("2026-07-20T12:00:00Z"), false)).let { Seasons.NAMES[if (it.mix < 0.5f) it.from else it.to] })
        // A scenery shows its own picture of a season where it has one, and its base pictures otherwise.
        assertEquals("a_autumn", Seasons.picture("a", 3, "spring", setOf("autumn", "winter")))
        assertEquals("k", Seasons.picture("k", 1, "spring", setOf("autumn", "winter")))
        assertEquals("a", Seasons.picture("a", 2, "spring", setOf("autumn", "winter")))
        assertEquals("a", Seasons.picture("a", 3, "", emptySet()))
        // The bench's copy (tools/sky3d/bench.js) has the same times.
        val bench = java.io.File("../tools/sky3d/bench.js").takeIf { it.exists() }?.readText()
        if (bench != null) assertTrue(bench.contains("const SEASON_FULL = [" + Seasons.FULL.joinToString(", ") + "]"))
        // Every season a scenery lists has its two pictures, and names a season.
        val lands = java.io.File("src/main/assets/sky2/land")
        // (Read as text: org.json is only a stub in these tests.)
        var checked = 0
        for (dir in lands.listFiles().orEmpty().filter { it.isDirectory }) {
            val meta = java.io.File(dir, "land.json").readText()
            checked++
            val list = Regex("\"seasons\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(meta)?.groupValues?.get(1) ?: continue
            val base = Regex("\"season\"\\s*:\\s*\"(\\w+)\"").find(meta)?.groupValues?.get(1) ?: ""
            assertTrue("${dir.name}: base season '$base'", base in Seasons.NAMES)
            for (n in Regex("\"(\\w+)\"").findAll(list).map { it.groupValues[1] }) {
                assertTrue("${dir.name}: $n", n in Seasons.NAMES && n != base)
                assertTrue("${dir.name}: a_$n.webp", java.io.File(dir, "a_$n.webp").exists())
                assertTrue("${dir.name}: k_$n.webp", java.io.File(dir, "k_$n.webp").exists())
            }
        }
        assertTrue("the sceneries were found", checked >= 6)
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
            // The green hills are snow-free outside the depth of winter, and never in the season they are shown in
            // when the seasons are not followed.
            val grass = SkyDirector.weather(seed, Biome.MEADOW, 0.5f)
            assertEquals(0f, SkyDirector.at(Biome.MEADOW, grass, 0.5, 60.0, 0.5f, 0f).snow, 0f)
            assertEquals(0f, SkyDirector.at(Biome.MEADOW, grass, 0.5, 60.0, Biome.MEADOW.signatureYear, 0f).snow, 0f)
            for (b in Biome.entries) if (b != Biome.ARCTIC) assertEquals("${b.id} in its own season", 0f, SkyDirector.at(b, SkyDirector.weather(seed, b, b.signatureYear), 0.5, 40.0, b.signatureYear, 0f).snow, 0f)
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
        // Upright things stay upright: the lens is shifted, not tilted, so a point straight above or below the middle of
        // the horizon has no sideways part, and the picture of the land only has to be slid to the horizon's row.
        assertEquals(0f, RealSkyFrame.ray(w / 2, 200f, w, h, focal, horizon, false)[0], 1e-6f)
        assertEquals((horizon - 300f) / focal, RealSkyFrame.ray(540f, 300f, w, h, focal, horizon, false).let { it[1] / it[2] }, 1e-5f)
        // The Sun rises and sets 30.24 % of the width from the middle: 14.7 degrees to the side through this lens, the
        // bearing the land is rendered to leave open (bake.RISE_AZIMUTH) and its lowest Suns stand at.
        val rise = RealSkyFrame.ray(w * (0.5f - 0.3024f), horizon, w, h, focal, horizon, false)
        assertEquals(-atan(0.3024f / 1.15f), kotlin.math.atan2(rise[0], rise[2]), 1e-5f)
        assertEquals(14.7, atan(0.3024 / 1.15) * 180 / PI, 0.05)
        assertEquals(14.7, LightWeights.sunAzimuth(0.7), 1e-9)
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
            assertTrue(b.sceneScale < a.sceneScale && b.stillMs >= a.stillMs && b.waterMs >= a.waterMs && b.cloudSample >= a.cloudSample && b.landSample >= a.landSample)
        }
    }

    @Test fun everySceneryHasAChoiceInSettings() {
        assertEquals(Biome.DUNES, Biome.of(Scenery.DUNES, null, null, null))
        assertEquals(Biome.RAINFOREST, Biome.of(Scenery.RAINFOREST, null, null, null))
        assertEquals(Biome.ARCTIC, Biome.of(Scenery.ARCTIC, null, null, null))
        assertEquals(Biome.OCEAN, Biome.of(Scenery.SEA, null, null, null))
        // (Green hills took the savanna's place in 0.11; a choice stored under the old name shows them.)
        assertEquals(Biome.MEADOW, Biome.of(Scenery.SAVANNA, null, null, null))
        assertEquals(Biome.CANYON, Biome.of(Scenery.CANYON, null, null, null))
        // The 0.9 choices still mean something.
        assertEquals(Biome.RAINFOREST, Biome.of(Scenery.HILLS, null, null, null))
        assertEquals(Biome.CANYON, Biome.of(Scenery.MOUNTAINS, null, null, null))
        assertEquals(6, Biome.entries.map { it.id }.toSet().size)
    }

    @Test fun theLightOfAMomentLiesBetweenRenderedSuns() {
        val land = setOf("l0", "l1", "l2", "l3", "l4", "l5", "l6", "l7", "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7", "top")
        val clouds = land + setOf("lu0", "lu1", "ru0", "ru1")
        var last: LightWeights? = null
        var el = -9.0
        while (el <= 80.0) {
            for (side in listOf(-1.0, -0.4, 0.0, 0.7, 1.0)) for (under in listOf(false, true)) {
                val lw = LightWeights.of(el, side, under, 0.5f)
                assertEquals("weights sum to one at $el", 1f, lw.w.sum(), 1e-4f)
                assertTrue(lw.w.all { it >= 0f })
                assertTrue("names at $el: ${lw.names.toList()}", (if (under) clouds else land).containsAll(lw.names.toList()))
                assertEquals(0.5f, lw.glow[0] + lw.glow[1], 1e-5f)
                assertEquals(1f, lw.dir.let { it[0] * it[0] + it[1] * it[1] + it[2] * it[2] }, 1e-4f)
                // Morning light comes from the left, afternoon light from the right.
                if (side < -0.3) assertTrue(lw.dir[0] < 0f) else if (side > 0.3) assertTrue(lw.dir[0] > 0f)
                // Mirrored (right to left) the sides change places, nothing else.
                val m = lw.mirrored()
                for (k in 0 until 4) assertEquals(lw.names[k].drop(1), m.names[k].drop(1)).also { assertTrue(lw.names[k][0] != m.names[k][0]) }
                assertEquals("top", m.names[4]); assertEquals(lw.glow[0], m.glow[1], 0f)
            }
            // Nothing jumps as the Sun climbs: a small step in height is a small change of the light's direction.
            val now = LightWeights.of(el, -1.0, true, 0f)
            last?.let { assertTrue("light turns smoothly at $el", abs(it.dir[1] - now.dir[1]) < 0.02f && abs(it.dir[0] - now.dir[0]) < 0.03f) }
            last = now
            el += 0.25
        }
        // The land has no Suns under the horizon: it keeps its lowest shading; the clouds are lit from below then.
        assertEquals(listOf("l0", "l1", "r0", "r1", "top"), LightWeights.of(-3.0, 0.0, false, 0f).names.toList())
        assertEquals(listOf("lu1", "lu0", "ru1", "ru0", "top"), LightWeights.of(-3.0, 0.0, true, 0f).names.toList())
        assertTrue(LightWeights.of(-3.0, 0.0, true, 0f).dir[1] < 0f)
        // At noon the high Sun in front takes part; in the morning it does not.
        assertTrue(LightWeights.of(60.0, 0.0, false, 0f).w[4] > 0.5f)
        assertEquals(0f, LightWeights.of(60.0, -1.0, false, 0f).w[4], 0f)
    }

    /**
     * The owner's complaint about 0.13: "the same cloud types just keep cycling over and over again... It's just
     * overcast, and then it's overcast again... I want the entire day to have a lot of variety... sometimes it's
     * overcast, sometimes it's broken, sometimes it's scattered, sometimes it's isolated clouds."
     */
    @Test fun theDayIsAWalkThroughDifferentSkies() {
        for (biome in Biome.entries) {
            val seenLow = HashSet<String>()
            var closedLegs = 0; var dayLegs = 0; var fewKinds = 0; var days = 0
            for (day in 0 until 240) {
                val year = (day % 12) / 12f
                val w = SkyDirector.weather(day * 15_485_863L + biome.ordinal * 31L + 9, biome, year)
                val p = SkyDirector.programme(w, biome, year, 6.2f, 17.9f)
                days++
                for (level in 0..2) {
                    val raw = p.sets[level]
                    val sets = raw.map { it?.removeSuffix(SkyDirector.TURNED) }
                    // No set passes twice in a row, either way round: the same clouds never follow themselves.
                    for (i in 1 until sets.size) if (sets[i] != null) assertTrue("$biome day $day level $level leg $i: ${sets[i]} twice", sets[i] != sets[i - 1])
                    for (x in sets) if (x != null) assertTrue(x, x in SkyDirector.SETS[level])
                    // A set passes turned about every other time: the very same picture comes back only after its set
                    // has been by the other way round.
                    val lastWay = HashMap<String, Boolean>()
                    for (i in raw.indices) {
                        val name = sets[i] ?: continue
                        val turned = raw[i]!!.endsWith(SkyDirector.TURNED)
                        lastWay[name]?.let { assertTrue("$biome day $day level $level: $name the same way round twice", it != turned) }
                        lastWay[name] = turned
                    }
                }
                val low = p.sets[2].map { it?.removeSuffix(SkyDirector.TURNED) }
                for (i in low.indices) assertTrue("$biome day $day: the low sky is never empty", low[i] != null)
                for (i in 1 until low.size) {
                    // A sky closes and opens by degrees.
                    assertTrue("$biome day $day leg $i: ${low[i - 1]} beside ${low[i]}", abs(SkyDirector.closedness(low[i]) - SkyDirector.closedness(low[i - 1])) <= 2)
                }
                // A closed sky holds for three legs at most (an hour and three quarters), then it breaks.
                var run = 0
                for (x in low) { run = if (SkyDirector.closedness(x) >= 4) run + 1 else 0; assertTrue("$biome day $day: closed for $run legs", run <= 3) }
                // The daylight (the legs whose middle lies between sunrise and sunset).
                val kinds = HashSet<String>()
                var closed = 0; var n = 0
                for (i in low.indices) {
                    val h = (SkyDirector.FIRST_LEG + i + 0.5f) * 0.55f
                    if (h < 6.5f || h > 18.5f) continue
                    n++; kinds += low[i]!!; seenLow += low[i]!!
                    if (SkyDirector.closedness(low[i]) >= 4) closed++
                }
                // Even the most overcast day is open for more than half of its daylight, and every day shows at least
                // three different low skies, nearly every day four or more.
                assertTrue("$biome day $day: closed for $closed of $n legs of daylight", closed <= n * 0.45f)
                assertTrue("$biome day $day: only ${kinds.size} kinds of low sky by day ($kinds)", kinds.size >= 3)
                if (kinds.size < 4) fewKinds++
                closedLegs += closed; dayLegs += n
            }
            // (The desert's sky is the most open: there one day in four may have three kinds.)
            assertTrue("$biome: $fewKinds of $days days with fewer than four kinds of low sky", fewKinds <= days * (if (biome == Biome.DUNES) 0.25f else 0.12f))
            // Over the year a closed sky is a small share of the daylight (least over the desert), and every kind of
            // low sky has its hours (no thunderstorm over the arctic).
            val share = closedLegs.toFloat() / dayLegs
            assertTrue("$biome: closed for ${share * 100} % of the daylight", share <= (if (biome == Biome.DUNES) 0.04f else 0.13f))
            // (No thunderstorm over the arctic, near or far.)
            assertEquals(biome.id, SkyDirector.SETS[2].toSet() - (if (biome == Biome.ARCTIC) SkyDirector.THUNDER.keys else emptySet()), seenLow)
        }
        // The hours of colour keep room for the Sun under cloud that takes its colour, unless the day's closed spell
        // falls on them: at sunset the low sky is open enough (never a full overcast) on nine days in ten, and there
        // is middle or high cloud above it on nine days in ten.
        var room = 0; var lit = 0; var all = 0
        for (biome in Biome.entries) for (day in 0 until 120) {
            val w = SkyDirector.weather(day * 104_729L + 5, biome, 0.3f)
            val s = SkyDirector.at(biome, w, 17.9 / 24, 1.0, 0.3f, 0.2f, 6.2f, 17.9f)
            all++
            if (s.overcast < 0.5f) room++
            if (s.clouds[0].any { it.weight * SkyDirector.share(it.offset) > 0.3f } || s.clouds[1].any { it.weight * SkyDirector.share(it.offset) > 0.3f }) lit++
        }
        assertTrue("room for the Sun at sunset on $room of $all days", room >= all * 0.9f)
        assertTrue("cloud to take the colour at sunset on $lit of $all days", lit >= all * 0.9f)
    }

    /** The owner: "early in the morning, you might have fog." */
    @Test fun someMorningsHaveFogAndTheSunBurnsItOff() {
        val rise = 6.2f; val set = 17.9f
        for (biome in Biome.entries) for (year in listOf(0.05f, 0.5f)) {
            var foggy = 0; val days = 200
            for (day in 0 until days) {
                val w = SkyDirector.weather(day * 86_969L + biome.ordinal * 13L + 1, biome, year)
                fun fog(h: Float) = SkyDirector.at(biome, w, h / 24.0, sunEl(h / 24.0), year, 0.2f, rise, set).fog
                // By late morning it has gone, and it does not come back with the evening.
                for (h in listOf(10.5f, 12f, 15f, 18f, 21f, 0.5f)) assertEquals("$biome day $day at $h", 0f, fog(h), 0f)
                // It gathers and thins by degrees: never at a stroke.
                var prev = fog(2f); var most = prev
                var h = 2f
                while (h < 10.5f) {
                    h += 1f / 60
                    val now = fog(h)
                    assertTrue("$biome day $day: fog in bounds ($now)", now in 0f..1f)
                    assertTrue("$biome day $day at $h: fog steps by ${abs(now - prev)}", abs(now - prev) < 0.02f)
                    most = max(most, now); prev = now
                }
                if (most > 0.4f) {
                    foggy++
                    // A foggy morning has its fog at sunrise.
                    assertTrue("$biome day $day: fog at sunrise", fog(rise) > 0.4f)
                    assertTrue("no fog on a stormy day", w.type != com.noor.app.ui.today.Weather.STORMY)
                }
            }
            // Every scenery has foggy mornings, and most of its mornings are clear.
            assertTrue("$biome at $year: $foggy foggy mornings of $days", foggy >= 6 && foggy <= days * 0.6f)
        }
    }

    @Test fun theDirectorChoosesCloudSetsThatExist() {
        val seen = List(3) { HashSet<String>() }
        for (biome in Biome.entries) for (day in 0 until 150) {
            val w = SkyDirector.weather(day * 977L + biome.ordinal, biome, biome.signatureYear)
            var f = 0.0
            while (f < 1.0) {
                val s = SkyDirector.at(biome, w, f, sunEl(f), biome.signatureYear, 0.2f)
                assertEquals(3, s.clouds.size)
                for (level in 0..2) {
                    // (The set in the middle of the view and the frayed ends of its two neighbours: never more than three.)
                    assertTrue(s.clouds[level].size <= 3)
                    for (leg in s.clouds[level]) {
                        assertTrue(leg.set, leg.set in SkyDirector.SETS[level])
                        assertTrue(leg.weight in 0f..1f)
                        assertTrue(kotlin.math.abs(leg.offset) < SkyDirector.REACH)
                        if (leg.weight > 0.5f && SkyDirector.share(leg.offset) > 0.5f) seen[level] += leg.set
                    }
                }
                assertTrue(s.sunPart in 0f..1f); assertTrue(s.halo in 0f..1f)
                assertTrue(s.overcast in 0f..1f); assertTrue(s.shade in 0f..1f)
                // Under a full overcast the Sun is not seen, and the land is not in sunshine.
                val full = s.clouds[2].filter { it.set == "lo_overcast" }.sumOf { (it.weight * SkyDirector.share(it.offset)).toDouble() }
                if (full > 0.95) { assertTrue(s.sunPart < 0.06f); assertTrue(s.shade > 0.9f) }
                f += 1.0 / 48
            }
        }
        // Over the sceneries and a few months of days, every kind of sky has its day.
        for (level in 0..2) assertEquals(SkyDirector.SETS[level].toSet(), seen[level])
        // Every set has its pictures; the lightning strikes where each thunderstorm set's own file says its towers
        // stand (sky.json, "towers"; lo_storm's predates that and is the helper's measure).
        for (level in 0..2) for (set in SkyDirector.SETS[level]) {
            val json = java.io.File("src/main/assets/sky2/clouds/$set/sky.json")
            assertTrue("$set has its pictures", json.exists() && java.io.File("src/main/assets/sky2/clouds/$set/a.webp").exists())
            val towers = Regex("\"towers\"\\s*:\\s*\\[([^]]*)]").find(json.readText())?.groupValues?.get(1)
                ?.split(',')?.map { it.trim().toFloat() }
            if (towers != null) assertEquals(set, towers, SkyDirector.THUNDER.getValue(set).toList())
        }
        // The shaders know how far apart the sets of a level stand (layer.frag ends a set by it).
        assertTrue(java.io.File("src/main/assets/sky2/common.glsl").readText().contains("#define SKY_PITCH ${SkyDirector.PITCH.toInt()}.0"))
    }

    /**
     * The owner's complaint about 0.11: "I don't like how they instantly change as soon as one prayer time comes...
     * the entire sky, all the clouds in the sky suddenly transform into a different type of cloud."
     * From one minute to the next every set on show has only moved on at its own slow speed, and thinned or thickened
     * by a small step; a set appears or disappears only where it has no part of the view yet, or no more (it comes in
     * from the side and passes out at the other); and the Sun's window and the land's shade change by small steps:
     * at sunrise, at sunset, at every hour of the day, whatever the weather.
     */
    @Test fun theCloudsNeverChangeAtAStroke() {
        for (biome in Biome.entries) for (day in 0 until 40) {
            val w = SkyDirector.weather(day * 613L + biome.ordinal, biome, biome.signatureYear)
            fun at(f: Double) = SkyDirector.at(biome, w, f, sunEl(f), biome.signatureYear, 0.2f)
            val minute = 1.0 / 1440
            var f = 2 * minute
            var before = at(f)
            while (f < 1.0 - 3 * minute) {
                f += minute
                val now = at(f)
                val where = "$biome day $day ${"%.4f".format(f)}"
                for (level in 0..2) {
                    val was = before.clouds[level]; val isNow = now.clouds[level]
                    for (a in was) {
                        val moved = a.speed * 60f
                        // (No set passes faster than two and a half degrees a minute.)
                        assertTrue("$where ${a.set} at ${kotlin.math.abs(moved)} degrees a minute", kotlin.math.abs(moved) < 2.5f)
                        val b = isNow.firstOrNull { it.set == a.set && kotlin.math.abs(it.offset - a.offset - moved) < 0.05f }
                        if (b == null) assertTrue("$where ${a.set} gone from the middle of the view", SkyDirector.share(a.offset) < 0.05f)
                        else assertTrue("$where ${a.set} by ${kotlin.math.abs(b.weight - a.weight)}", kotlin.math.abs(b.weight - a.weight) < 0.08f)
                    }
                    for (b in isNow) {
                        val moved = b.speed * 60f
                        if (was.none { it.set == b.set && kotlin.math.abs(b.offset - it.offset - moved) < 0.05f })
                            assertTrue("$where ${b.set} appeared in the middle of the view", SkyDirector.share(b.offset) < 0.05f)
                    }
                }
                assertTrue("$where the Sun's window by ${kotlin.math.abs(now.sunPart - before.sunPart)}", kotlin.math.abs(now.sunPart - before.sunPart) < 0.2f)
                assertTrue("$where the land's shade by ${kotlin.math.abs(now.shade - before.shade)}", kotlin.math.abs(now.shade - before.shade) < 0.2f)
                assertTrue("$where the overcast by ${kotlin.math.abs(now.overcast - before.overcast)}", kotlin.math.abs(now.overcast - before.overcast) < 0.1f)
                before = now
            }
        }
    }

    @Test fun theFinestNightKeepsTheMilkyWayInThePicture() {
        var h = 0f
        while (h < 24f) {
            val moon = listOf(Float.NaN, -12f, 3f, 14f)[(h * 2).toInt() % 4]
            val f = RealSkyFrame.showcaseSky(h, moon)
            val x = f[0]; val y = f[1]; val z = f[2]; val pole = f[3]; val centre = f[4]
            fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
            // The sky is only turned: its axes stay square and of unit length.
            for (a in listOf(x, y, z)) assertEquals(1f, dot(a, a), 1e-4f)
            assertEquals(0f, dot(x, y), 1e-4f); assertEquals(0f, dot(y, z), 1e-4f); assertEquals(0f, dot(x, z), 1e-4f)
            // The Milky Way's centre is where the true sky's axes put it, and stands in the picture over the horizon.
            val ra = 266.40499 * PI / 180; val dec = -28.93617 * PI / 180
            val c = floatArrayOf((cos(dec) * cos(ra)).toFloat(), (cos(dec) * sin(ra)).toFloat(), sin(dec).toFloat())
            for (k in 0 until 3) assertEquals(centre[k], x[k] * c[0] + y[k] * c[1] + z[k] * c[2], 1e-4f)
            val el = asin(centre[1].toDouble()) * 180 / PI
            val az = kotlin.math.atan2(centre[0].toDouble(), centre[2].toDouble()) * 180 / PI
            assertTrue("height $el", el in 9.0..17.0); assertTrue("bearing $az", abs(az) < 14.0)
            // It keeps to the side of the picture the Moon is not on.
            if (!moon.isNaN() && abs(moon) > 6f) assertTrue("away from the Moon at $moon: $az", az * moon < 0 || abs(az) < 3.5)
            assertEquals(0f, dot(pole, centre), 1e-4f)
            h += 0.5f
        }
    }
}
