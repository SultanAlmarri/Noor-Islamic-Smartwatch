package com.noor.app.ui.watch

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 0.9.8: the phone's watch-face editor. Every face the watch carries has its own picture here; choosing a design keeps
 * the colour, choosing a colour keeps the design; the original face has no colours to choose. A render is saved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class FaceEditorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun chooseDesignAndColour() {
        // A picture for every face, and no two faces share one.
        val pictures = WatchFaces.ids.map { facePreview(it) }
        assertEquals(WatchFaces.ids.size, pictures.toSet().size)
        assertNotEquals(R.drawable.face_horizon_gold, facePreview("dial_rose"))

        var choice by mutableStateOf(WatchFaces.DEFAULT)
        rule.setContent {
            NoorTheme(com.noor.app.data.ThemeMode.DARK) {
                NoorBackground { FaceEditor(choice, { choice = it }, Modifier.padding(24.dp)) }
            }
        }
        rule.onNodeWithText("Horizon · Gold").assertExists()
        rule.onNodeWithContentDescription("Dial").performClick(); rule.waitForIdle()
        assertEquals("dial_gold", choice.id)
        rule.onNodeWithContentDescription("Rose").performClick(); rule.waitForIdle()
        assertEquals("dial_rose", choice.id)
        rule.onNodeWithText("Dial · Rose").assertExists()
        shot("80_face_editor_dial_rose")
        rule.onNodeWithContentDescription("Original").performClick(); rule.waitForIdle()
        assertEquals("original", choice.id)
        rule.onNodeWithContentDescription("Rose").assertDoesNotExist()
        rule.onNodeWithContentDescription("Minimal").performClick(); rule.waitForIdle()
        assertEquals("minimal_rose", choice.id) // the colour chosen before is kept
    }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("build/screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
