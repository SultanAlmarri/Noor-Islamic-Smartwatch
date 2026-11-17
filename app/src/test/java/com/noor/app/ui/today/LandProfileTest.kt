package com.noor.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** (0.19) The lie of the land of each of the seven terrains the 0.9 sky draws, the three newer ones in particular. */
class LandProfileTest {
    private val seeds = (1L..24L).map { it * 7919L }

    @Test fun everyTerrainHasFiveLayersOfHeightsWithinTheTable() {
        for (t in Terrain.entries) for (s in seeds) {
            val p = LandProfile.of(s, t)
            assertEquals(t, p.terrain)
            for (l in 0 until LandProfile.LAYERS) for (i in 0..100) {
                val h = p.height(l, i / 100f)
                assertTrue("$t layer $l: $h", h in 0f..1f)
            }
            val g = p.geometry(318f, 400f, 1f)
            for (l in 1 until LandProfile.LAYERS) assertTrue("$t: layers far to near", g.base[l] > g.base[l - 1] && g.distKm[l] < g.distKm[l - 1])
        }
    }

    @Test fun theForestAndTheArcticHaveStillWaterInTheMiddleDistance() {
        for (t in listOf(Terrain.FOREST, Terrain.ARCTIC)) for (s in seeds) {
            val p = LandProfile.of(s, t)
            for (i in 0..100) {
                assertEquals(0f, p.height(LandProfile.LAKE, i / 100f))
                assertTrue(p.water(LandProfile.LAKE, i / 100f))
                assertTrue(!p.water(1, i / 100f) && !p.water(3, i / 100f))
            }
        }
        for (t in listOf(Terrain.DUNES, Terrain.HILLS, Terrain.MOUNTAINS, Terrain.CANYON)) assertTrue((0..100).none { LandProfile.of(7L, t).water(LandProfile.LAKE, it / 100f) })
    }

    @Test fun theForestShoreRisesAtTheSidesOfTheLake() {
        for (s in seeds) {
            val p = LandProfile.of(s, Terrain.FOREST)
            val edges = maxOf(p.height(3, 0.02f), p.height(3, 0.98f))
            val middle = p.height(3, 0.5f)
            assertTrue("seed $s: knolls ${"%.2f".format(edges)} over the water's edge ${"%.2f".format(middle)}", edges > middle + 0.4f)
        }
    }

    /** Mesas and buttes: flat tops (long runs of nearly equal heights high up) on sheer walls (big steps between samples). */
    @Test fun theCanyonHasFlatToppedMesasOnSheerWalls() {
        for (s in seeds) {
            val p = LandProfile.of(s, Terrain.CANYON)
            for (l in 0..2) {
                val h = FloatArray(LandProfile.N) { p.height(l, it / (LandProfile.N - 1f)) }
                var run = 0; var longest = 0
                for (i in 1 until h.size) { run = if (h[i] > 0.35f && abs(h[i] - h[i - 1]) < 0.004f) run + 1 else 0; longest = maxOf(longest, run) }
                val steepest = (1 until h.size).maxOf { abs(h[it] - h[it - 1]) }
                assertTrue("seed $s layer $l: flat top of $longest samples", longest >= 12)
                assertTrue("seed $s layer $l: steepest step $steepest", steepest > 0.03f)
            }
        }
    }

    @Test fun theArcticRangesAreHigherThanTheForestsFarRange() {
        val g = LandProfile.of(1L, Terrain.ARCTIC).geometry(318f, 400f, 1f)
        val f = LandProfile.of(1L, Terrain.FOREST).geometry(318f, 400f, 1f)
        assertTrue(g.amp[0] > f.amp[0])
    }
}
