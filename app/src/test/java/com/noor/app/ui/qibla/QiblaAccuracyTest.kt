package com.noor.app.ui.qibla

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.qibla.CompassAssessor
import com.noor.shared.compass.CompassReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 0.9.6: the accuracy box follows the sensor at once. The compose clock is frozen: if anything held the old level back
 * (a timer, a fade), the new label would not be on screen yet.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QiblaAccuracyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun reading(fieldUt: Double, expected: Double = 45.0): CompassReading {
        val a = CompassAssessor.assess(4.0, CompassAssessor.STATUS_HIGH, fieldUt, expected, 3.0)
        return CompassReading(120.0, 1.5, 4.0, CompassAssessor.STATUS_HIGH, fieldUt, expected, 3.0, a, CompassReading.Source.ROTATION_VECTOR)
    }

    @Test fun levelChangesOnTheNextFrame() {
        var r by mutableStateOf(reading(45.0))
        rule.mainClock.autoAdvance = false
        rule.setContent { NoorTheme(com.noor.app.data.ThemeMode.DARK) { AccuracyCard(r, Formatter(false, Numerals.WESTERN, TimeFormat.H12)) {} } }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Good").assertExists()
        // Metal nearby: the field is 40 % off what the World Magnetic Model expects here.
        rule.runOnUiThread { r = reading(63.0) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Poor").assertExists()
        // A little off (20 %): Fair.
        rule.runOnUiThread { r = reading(54.0) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Fair").assertExists()
        // And back.
        rule.runOnUiThread { r = reading(45.5) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Good").assertExists()
    }
}
