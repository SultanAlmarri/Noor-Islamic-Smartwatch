package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.WhatsNew
import com.noor.app.ui.components.WhatsNewContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** (0.25) "What's new": who is shown it, and what it looks like (its content, drawn on a page: the sheet itself opens in a window of its own). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class WhatsNewDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun whatsNew() {
        // Shown once to someone who had Minhad before this edition; never before the welcome is done, never twice.
        assertTrue(WhatsNew.due(onboardingDone = true, seen = 0))
        assertFalse(WhatsNew.due(onboardingDone = false, seen = 0))
        assertFalse(WhatsNew.due(onboardingDone = true, seen = WhatsNew.EDITION))

        val h = RenderHarness(rule)
        h.screen = { pad -> Column(Modifier.fillMaxSize().padding(pad)) { WhatsNewContent {} } }
        h.start()
        h.shot("whats_new_en", "Continue")
        h.arabic = true; h.shot("whats_new_ar", "متابعة")
        h.arabic = false; h.theme = ThemeMode.LIGHT; h.fontScale = 1.3f; h.shot("whats_new_day_large_en", "Continue")
    }
}
