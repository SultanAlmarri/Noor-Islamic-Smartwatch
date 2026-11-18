package com.noor.app.sky

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.21: the real sky's own step down (pictures too slow) holds until the app is updated, as automatic's level does
 * (SkyLevels); until 0.20 it held for good, so a phone slow for a while once drew the coarser sky ever after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealSkyTierTest {
    @Test fun aStepDownHoldsUntilTheAppIsUpdated() {
        val app = RuntimeEnvironment.getApplication()
        val gpu = "Adreno (TM) 750"
        val first = RealSky.tierFor(app, gpu)
        assertEquals(RealSky.guess(gpu, android.app.ActivityManager.MemoryInfo().also {
            app.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }.totalMem / (1024.0 * 1024 * 1024)), first)
        // Too slow: one step down, kept for this version.
        if (first != RealSky.Tier.LOW) {
            RealSky.stepDown(app, first)
            assertEquals(RealSky.Tier.entries[first.ordinal + 1], RealSky.tierFor(app, gpu))
        }
        // A step saved by another version (an earlier one, or a step that 0.10-0.18 let Settings choose) is not held.
        app.getSharedPreferences("noor_ui", 0).edit().putInt("real_sky_tier", RealSky.Tier.LOW.ordinal).putLong("real_sky_tier_for", -7L).commit()
        assertEquals(first, RealSky.tierFor(app, gpu))
        app.getSharedPreferences("noor_ui", 0).edit().remove("real_sky_tier_for").commit()
        assertEquals(first, RealSky.tierFor(app, gpu))
    }
}
