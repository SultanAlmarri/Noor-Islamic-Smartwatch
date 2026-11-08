package com.noor.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * 0.9.9: the weather and the field of photographed clouds (Weather, CloudField).
 *
 * - Days differ: over a few weeks every kind of day comes up (clear, fair, convective, stormy, layered, overcast, rainy,
 *   mixed), some with a handful of clouds and some with dozens, some isolated and some in clusters.
 * - A day evolves: cumulus build through the day and melt at night, a convective day's clouds grow into towers by the
 *   afternoon; nothing jumps from one minute to the next (under 3% of its range a minute: dragging the Sun shows a
 *   smooth change).
 * - Thunderstorms flash now and then, with strokes to the ground.
 * - Clouds keep a sensible size beside the land (0.9.8's were bigger than the mountains); clouds with a base stand
 *   above the horizon, those whose base was cut off hide it below; they are drawn far to near and the same every time.
 * - They are lit by the moment: near-white in the midday Sun, dark on a moonless night.
 */
class CloudFieldTest {
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }
    private val table = CloudTable.parse(File(raw, "cloud_atlas.txt").readText())
    private val dp = 2.625f
    private val w = 412 * dp; private val h = 400 * dp; private val horizon = 318 * dp

    private fun frame(weather: Weather, hour: Double, sunEl: Double, time: Float = 120f, night: Float = 0f, state: Weather.State = weather.at(hour / 24)): CloudField.Frame {
        val lut = IntArray(Atmosphere.NX * Atmosphere.NY)
        val light = Atmosphere.frame(sunEl, 0.3, Math.toRadians(sunEl.coerceAtLeast(0.0)), 2.1, 40.0, lut)
        return CloudField.Frame(w, h, horizon, dp, 2.1, time, weather, state, 0.3, sunEl, light, lut, night, 0f)
    }

    private fun layout(f: CloudField.Frame, bolt: Bolt? = null) =
        ArrayList<CloudDraw>().let { out -> val n = CloudField.layout(table, f, out, bolt); out.take(n) }
    private fun clouds(f: CloudField.Frame) = layout(f).filter { !it.shaft }

    @Test fun daysDiffer() {
        val types = HashSet<Int>(); val counts = ArrayList<Int>(); var clustered = 0
        for (day in 0L until 60L) {
            val wx = Weather.of(day * 1_000_003L + 20_000L, Terrain.HILLS)
            types += wx.type
            counts += clouds(frame(wx, 15.0, 35.0)).size
            if (wx.at(15.0 / 24).cluster > 0.6f) clustered++
        }
        assertEquals("every kind of day comes up", 8, types.size)
        assertTrue("some skies nearly empty: ${counts.min()}", counts.min() <= 6)
        assertTrue("some skies full of clouds: ${counts.max()}", counts.max() >= 25)
        assertTrue("some days in clusters", clustered >= 5)
    }

    @Test fun aDayEvolves() {
        val wx = Weather.of(Weather.CONVECTIVE, java.util.Random(3))
        val morning = wx.at(9.0 / 24); val afternoon = wx.at(16.5 / 24); val night = wx.at(2.0 / 24)
        assertTrue("towers by the afternoon", afternoon.size > morning.size + 0.3f)
        assertTrue("cumulus melt at night", night.cumulus < afternoon.cumulus * 0.6f)
        // No jumps: a minute changes nothing much, all day long.
        for (type in 0..7) {
            val d = Weather.of(type, java.util.Random(type * 7L + 1))
            var prev = d.at(0.0)
            for (m in 1..1440) {
                val s = d.at(m / 1440.0)
                for ((a, b) in listOf(prev.cumulus to s.cumulus, prev.size to s.size, prev.overcast to s.overcast, prev.storm to s.storm, prev.rain to s.rain, prev.layer to s.layer)) {
                    assertTrue("type $type at minute $m: $a → $b", abs(a - b) < 0.03f)
                }
                prev = s
            }
        }
    }

    @Test fun thunderstormsFlash() {
        val wx = Weather.of(Weather.STORMY, java.util.Random(5))
        val stormy = wx.at(16.0 / 24).let { Weather.State(0.8f, 0.85f, it.cluster, it.layer, it.overcast, 0.85f, it.rain, it.showers, it.cirrus) }
        val bolt = Bolt(); var flashes = 0; var strokes = 0
        var t = 0f
        while (t < 120f) {
            layout(frame(wx, 16.0, 20.0, time = t, state = stormy), bolt)
            if (bolt.intensity > 0.5f) { flashes++; if (bolt.path.size >= 4) strokes++ }
            t += 1f / 15f
        }
        assertTrue("lightning in two minutes of storm: $flashes frames", flashes >= 3)
        assertTrue("strokes to the ground", strokes >= 1)
        // A storm brings rain shafts.
        assertTrue(layout(frame(wx, 16.0, 20.0, state = stormy)).any { it.shaft })
        // A fair day does not flash.
        val fair = Weather.of(Weather.FAIR, java.util.Random(5))
        for (q in 0 until 600) { layout(frame(fair, 15.0, 35.0, time = q * 0.2f), bolt); assertEquals(0f, bolt.intensity) }
    }

    @Test fun cloudsKeepTheirPlaceAndSize() {
        for (type in 0..7) for (seed in 1L..4L) for (hour in listOf(10.0, 15.0, 18.0)) {
            val c = layout(frame(Weather.of(type, java.util.Random(seed)), hour, 30.0))
            c.zipWithNext().forEach { (a, b) -> assertTrue("drawn far to near", a.distance >= b.distance) }
            for (d in c.filter { !it.shaft }) {
                val s = table.sprites[d.sprite]
                val width = (d.right - d.left) / w
                if (s.kind == CloudTable.HUMILIS || s.kind == CloudTable.FRACTUS) assertTrue("a small cumulus ${width * 100}% of the view wide", width < 0.30f)
                if (s.kind == CloudTable.MEDIOCRIS) assertTrue("a cumulus ${width * 100}% of the view wide", width < 0.55f)
                if (s.cut) assertTrue("a base cut off in its photograph hides below the horizon", d.bottom > horizon)
                else assertTrue("a cloud's base stands above the horizon", d.bottom - s.base * (d.bottom - d.top) / s.h <= horizon + 1f)
            }
        }
        val wx = Weather.of(Weather.MIXED, java.util.Random(2))
        assertEquals(layout(frame(wx, 14.0, 40.0, time = 500f)).map { it.left }, layout(frame(wx, 14.0, 40.0, time = 500f)).map { it.left })
    }

    @Test fun litByTheMoment() {
        val clear = Weather.of(Weather.FAIR, java.util.Random(4))
        val sunny = clear.at(13.0 / 24).let { Weather.State(0.7f, 0.3f, it.cluster, 0f, 0f, 0f, 0f, 0f, it.cirrus) }
        val noon = clouds(frame(clear, 13.0, 60.0, state = sunny)).filter { it.distance < 20f }
        assertTrue(noon.isNotEmpty())
        for (c in noon) for (j in 0..2) assertTrue("midday cloud channel $j", c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255 > 0.72f)
        // A moonless night: dark.
        for (c in clouds(frame(clear, 23.0, -30.0, night = 1f, state = sunny))) for (j in 0..2) assertTrue("night cloud channel $j = ${c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255}", c.matrix[j * 5] + c.matrix[j * 5 + 4] / 255 < 0.25f)
        // Under an overcast: grey, not sunlit white.
        val grey = Weather.State(0.7f, 0.3f, 0.3f, 0f, 1f, 0f, 0f, 0f, 0f)
        for (c in clouds(frame(clear, 13.0, 60.0, state = grey)).filter { it.distance < 20f }) assertTrue("overcast cloud green = ${c.matrix[5] + c.matrix[9] / 255}", c.matrix[5] + c.matrix[9] / 255 < 0.9f)
    }
}
