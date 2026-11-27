package com.noor.app.ui.qibla

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.qibla.CompassAssessor
import com.noor.shared.compass.CompassReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 0.9.6: the accuracy (0.26: the readings card's third reading, [AccuracyReadout]) follows the sensor at once (0.9.7: and the meter does not move when the word changes). The compose clock is frozen: if anything held the old level back
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
        rule.setContent { NoorTheme(com.noor.app.data.ThemeMode.DARK) { androidx.compose.foundation.layout.Row { AccuracyReadout(r, androidx.compose.ui.Modifier.width(120.dp)) {} } } }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Good", useUnmergedTree = true).assertExists()
        val meterAt = { _: String -> rule.onNode(androidx.compose.ui.test.hasTestTag("accuracyMeter"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left }
        val goodX = meterAt("Good")
        // Metal nearby: the field is 40 % off what the World Magnetic Model expects here.
        rule.runOnUiThread { r = reading(63.0) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Poor", useUnmergedTree = true).assertExists()
        // 0.9.7: the meter stays where it was whatever the word beside it.
        org.junit.Assert.assertEquals(goodX, meterAt("Poor"), 0.01f)
        // A little off (20 %): Fair.
        rule.runOnUiThread { r = reading(54.0) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Fair", useUnmergedTree = true).assertExists()
        org.junit.Assert.assertEquals(goodX, meterAt("Fair"), 0.01f)
        // And back.
        rule.runOnUiThread { r = reading(45.5) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Good", useUnmergedTree = true).assertExists()
    }
}
