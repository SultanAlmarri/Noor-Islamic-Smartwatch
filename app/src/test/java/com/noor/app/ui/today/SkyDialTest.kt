package com.noor.app.ui.today

import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.astro.SunPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * (0.23) The sky dial: the owner (18 November 2026) did not like the Sun's path "mostly only appear[ing] inside the
 * terrain", with "the sun ... not really visible most of the time". The arch now stands on one baseline over the land,
 * the same over every scenery and level ([SkyGeometry.baseY]); its night is a short dip that stays over the true horizon.
 * Checked against the land the app really draws: Medium's painted pictures (`assets/medium/<id>/meta.json`), the
 * layered land (the dunes, the 0.9 sky and the fallbacks) and High's pictures (`assets/sky2/land/<id>/land.json`).
 */
class SkyDialTest {
    private val dubai = ZoneId.of("Asia/Dubai")

    private fun scene(date: LocalDate, point: GeoPoint, zone: ZoneId): SkyScene {
        val path = SunPath.of(date, point, zone)
        val noon = path.samples.maxBy { it.second }.first
        fun cross(el: Double, rising: Boolean): Double {
            var lo = if (rising) 0.0 else noon; var hi = if (rising) noon else 1.0
            repeat(50) { val m = (lo + hi) / 2; if ((interpolate(path.samples, m) < el) == rising) lo = m else hi = m }
            return (lo + hi) / 2
        }
        fun t(f: Double) = path.start.plusMillis(((path.end.toEpochMilli() - path.start.toEpochMilli()) * f).toLong())
        // (Fajr and Isha only where the Sun goes 18 degrees down: not in a London June.)
        val deep = path.samples.minOf { it.second } < -18.0
        val events = listOfNotNull(if (deep) Prayer.FAJR to t(cross(-18.0, true)) else null, Prayer.SHURUQ to t(cross(-0.833, true)),
            Prayer.MAGHRIB to t(cross(-0.833, false)), if (deep) Prayer.ISHA to t(cross(-18.0, false)) else null)
        return SkyScene(path, events, ZonedDateTime.of(date.atTime(9, 0), zone).toInstant(), Prayer.DHUHR, 10, point.latitude < 0, Terrain.DUNES, 1L, 77L)
    }

    private val scenes = listOf(
        scene(LocalDate.of(2026, 11, 18), GeoPoint(25.2048, 55.2708), dubai),
        scene(LocalDate.of(2027, 6, 21), GeoPoint(25.2048, 55.2708), dubai),
        scene(LocalDate.of(2026, 12, 21), GeoPoint(51.5072, -0.1276), ZoneId.of("Europe/London")),
        scene(LocalDate.of(2027, 6, 21), GeoPoint(51.5072, -0.1276), ZoneId.of("Europe/London")),
        scene(LocalDate.of(2027, 3, 20), GeoPoint(-6.2088, 106.8456), ZoneId.of("Asia/Jakarta")),
    )

    /** (width, header height, horizon, arc top) in dp: a common phone (the bench's), the review's, a small one. */
    private val sizes = listOf(floatArrayOf(411f, 457f, 347f, 227f), floatArrayOf(412f, 400f, 318f, 237f), floatArrayOf(360f, 300f, 242f, 180f))
    private val d = 2.625f

    private fun skyline(file: String): FloatArray {
        val text = listOf(File("src/main/assets/$file"), File("app/src/main/assets/$file")).first { it.exists() }.readText()
        val body = Regex("\"skyline\"\\s*:\\s*\\[([^\\]]*)]").find(text)!!.groupValues[1]
        return body.split(',').map { it.trim().toFloat() }.toFloatArray()
    }
    /** Medium's painted land as the path layer reads it (PaintedLandPainter.skyline: SunLift's form). */
    private fun painted(id: String): FloatArray { val m = skyline("medium/$id/meta.json"); return FloatArray(m.size) { m[m.size - 1 - it] / SunLift.FOCAL } }
    private fun high(id: String): FloatArray = skyline("sky2/land/$id/land.json")

    /** Every point of the day's arch (the Sun up), its x and y on the screen. */
    private fun dayArch(s: SkyScene, g: SkyGeometry): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        var f = 0.0
        while (f <= 1.0) { val el = s.elevationAt(f); if (el >= -0.83) out += g.x(f) to g.y(el, f); f += 1.0 / 1440 }
        return out
    }

    /** The same baseline, the same arch, over every scenery and level; the baseline is over the true horizon. */
    @Test fun oneDialOverEverySceneryOnABaselineOverTheHorizon() {
        for (sz in sizes) {
            val w = sz[0] * d; val hy = sz[2] * d; val arc = sz[3] * d
            val s = scenes[0]
            val g = SkyGeometry(s, w, hy, arc, sz[1] * d - 6 * d, rtl = false, dial = true)
            for (t in Terrain.entries) {
                val o = SkyGeometry(s.copy(terrain = t), w, hy, arc, sz[1] * d - 6 * d, rtl = false, dial = true)
                assertEquals(g.baseY, o.baseY, 0f)
                var f = 0.0
                while (f <= 1.0) { assertEquals(g.y(s.elevationAt(f), f), o.y(s.elevationAt(f), f), 0f); f += 0.01 }
            }
            assertTrue("baseline ${(hy - g.baseY) / d} dp over the horizon", hy - g.baseY >= minOf(SkyGeometry.DIAL_BASE * w, SkyGeometry.DIAL_SHARE * (hy - arc)) - 0.01f)
            assertTrue("the arch keeps height", g.baseY - arc >= (1 - SkyGeometry.DIAL_SHARE) * (hy - arc) - 0.01f)
            // Sunrise and sunset on the baseline, noon at the top of the arch.
            val rise = s.events.first { it.first == Prayer.SHURUQ }.second
            assertEquals(g.baseY, g.y(s.elevationAt(s.path.fraction(rise))), 0.6f * d)
            assertEquals(arc, g.y(s.path.maxElevation.coerceAtLeast(12.0)), 0.5f)
            // The night's dip stays over the true horizon, whatever the depth of the Sun.
            for (el in listOf(-1.0, -6.0, -18.0, -24.0, -60.0)) {
                val y = g.y(el)
                assertTrue("el $el: ${(hy - y) / d} dp over the horizon", y > g.baseY && y < hy - 2f * d)
            }
        }
        // Without the dial (the widgets) the path stands on the true horizon as before.
        val plain = SkyGeometry(scenes[0], 1080f, 900f, 700f, 1100f, rtl = false)
        assertEquals(900f, plain.baseY, 0f)
    }

    /**
     * The low Sun and the low ends of the path are over the land of every Medium scenery and the layered land, in both
     * reading directions, on a common phone's header (411 x 457 dp, the bench's). On shorter headers the space between
     * the horizon and the countdown is smaller than the tallest painted land (Red rock's spires stand 0.19 of the width
     * over the horizon, 79 dp, on a header whose arch has 81 dp): there a spire or a fir still reaches into the dial's
     * low ends (the Sun is then drawn in front of it), but much less than into the path of 0.20 to 0.22. The figures are
     * printed (the deepest the land reaches into the arch, dp).
     */
    @Test fun theDayArchIsOverTheLandOfEveryMediumScenery() {
        val lands = listOf("pnw", "meadow", "arctic", "ocean", "canyon").associateWith { painted(it) }
        val worst = LinkedHashMap<String, FloatArray>()     // (on the dial, on the old path)
        fun note(key: String, k: Int, v: Float) { val a = worst.getOrPut(key) { floatArrayOf(-1e9f, -1e9f) }; a[k] = maxOf(a[k], v) }
        for (sz in sizes) for (s in scenes) for (rtl in listOf(false, true)) {
            val w = sz[0] * d; val hy = sz[2] * d
            val size = "${sz[0].toInt()}x${sz[1].toInt()}"
            for ((k, dial) in listOf(0 to true, 1 to false)) {
                val g = SkyGeometry(s, w, hy, sz[3] * d, sz[1] * d - 6 * d, rtl, dial = dial)
                val arch = dayArch(s, g)
                for ((id, sky) in lands) for ((x, y) in arch) note("$id $size", k, (y - SunLift.skylineY(sky, x, w, hy, rtl)) / d)
                for (t in Terrain.entries) {
                    val lp = LandProfile.of(77L, t)
                    val geo = lp.geometry(hy, sz[1] * d, d)
                    for ((x, y) in arch) note("layered $t $size", k, (y - lp.skylineY(x, w, geo)) / d)
                }
            }
        }
        worst.forEach { (k, v) -> println("$k: the land reaches ${"%.1f".format(v[0])} dp into the dial (${"%.1f".format(v[1])} dp into the old path)") }
        worst.forEach { (k, v) ->
            if (k.endsWith("411x457")) assertTrue("$k: ${v[0]} dp", v[0] <= 0.5f)
            else assertTrue("$k: ${v[0]} dp into the dial, ${v[1]} before", v[0] <= 0.5f || v[0] < v[1] - 15f)
        }
    }

    /**
     * High: the dunes, the sea and the green hills are under the whole arch. The tallest of the mountains, the Pacific
     * Northwest's firs and Red rock's spires still rise over the dial's low ends (their land stands up to 0.4 of the
     * width over the horizon, higher than the text in places): there the Sun is drawn in front of the land
     * (LivingSky.frontSun). Locked: the Sun is hidden for less of the day than on the path of 0.20 to 0.22 (printed:
     * minutes over these five days, both directions, on the bench's phone).
     */
    @Test fun inHighTheLandHidesTheSunMuchLessThanBefore() {
        for (id in listOf("dunes", "ocean", "meadow")) {
            val sky = high(id)
            for (sz in sizes) for (s in scenes) for (rtl in listOf(false, true)) {
                val w = sz[0] * d; val hy = sz[2] * d
                val g = SkyGeometry(s, w, hy, sz[3] * d, sz[1] * d - 6 * d, rtl, dial = true)
                for ((x, y) in dayArch(s, g)) assertTrue("$id at x=${x / w}", y <= SunLift.skylineY(sky, x, w, hy, rtl) + 0.5f)
            }
        }
        for (id in listOf("pnw", "arctic", "canyon")) {
            val sky = high(id)
            var before = 0; var after = 0
            for (s in scenes) for (rtl in listOf(false, true)) {
                val sz = sizes[0]
                val w = sz[0] * d; val hy = sz[2] * d
                val old = SkyGeometry(s, w, hy, sz[3] * d, sz[1] * d - 6 * d, rtl)
                val dial = SkyGeometry(s, w, hy, sz[3] * d, sz[1] * d - 6 * d, rtl, dial = true)
                before += dayArch(s, old).count { (x, y) -> y > SunLift.skylineY(sky, x, w, hy, rtl) }
                after += dayArch(s, dial).count { (x, y) -> y > SunLift.skylineY(sky, x, w, hy, rtl) }
            }
            println("High $id: the Sun behind the land for $before minutes of these days on the old path, $after on the dial")
            assertTrue("$id: $after minutes hidden on the dial, $before before", after < before)
        }
    }
}
