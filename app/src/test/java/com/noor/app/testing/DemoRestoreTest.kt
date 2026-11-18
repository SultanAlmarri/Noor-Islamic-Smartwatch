package com.noor.app.testing

import androidx.test.core.app.ApplicationProvider
import com.noor.app.data.Scenery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * (0.22, testing only) The tester's demo changes the scenery in Settings as it goes; the user's own is kept on disk, so
 * an app closed by Android during a demo gives it back at its next start (until 0.22 it was lost).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoRestoreTest {
    @Test fun aDemoClosedHalfWayGivesTheUsersSceneryBack() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = app.getSharedPreferences("noor_tester", 0)
        // As startDemo leaves it while the demo runs: the user's own scenery, the arctic.
        prefs.edit().putString("demo_restore_scenery", Scenery.ARCTIC.name).commit()
        var set: Scenery? = null
        Tester.restoreAfterDemo(app) { set = it }
        assertEquals(Scenery.ARCTIC, set)
        assertNull(prefs.getString("demo_restore_scenery", null))
        // Nothing to give back the next time.
        set = null
        Tester.restoreAfterDemo(app) { set = it }
        assertNull(set)
    }
}
