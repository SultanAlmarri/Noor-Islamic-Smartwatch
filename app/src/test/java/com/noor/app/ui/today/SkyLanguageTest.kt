package com.noor.app.ui.today

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * (0.23) The sky scene is the same in Arabic as in English (the owner, 18 November 2026: "make sure the sunpath reflects
 * real life exactly (regardless of app language setting)"): until 0.22 the whole scene was mirrored right to left.
 * Renders the home sky (the medium graphics' Canvas form here, and the low graphics) in both layout directions and
 * compares them pixel by pixel. Software renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SkyLanguageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun scene(): SkyScene {
        val zone = ZoneId.of("Asia/Dubai"); val date = LocalDate.of(2026, 11, 18); val point = GeoPoint(25.2048, 55.2708)
        val config = WatchConfig(point = point, zoneId = zone.id, placeEn = "Dubai", placeAr = null, fromGps = false,
            profileId = Profiles.MWL.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED, offsets = emptyMap(),
            hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L)
        val day = config.day(date)
        val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
        val now = ZonedDateTime.of(date.atTime(8, 46), zone).toInstant()
        return SkyScene(SunPath.of(date, point, zone), events, now, Prayer.DHUHR, 8, false, Terrain.HILLS, 5L, 77L,
            latitude = point.latitude, longitude = point.longitude)
    }

    @Test fun theSceneIsTheSameInArabicAndEnglish() {
        val dir = mutableStateOf(LayoutDirection.Ltr)
        val simple = mutableStateOf(false)
        val s = scene()
        val layout = SceneLayout(cloudTop = 229f / 400f, arcTop = 237f / 400f, horizon = 318f / 400f, ground = 394f / 400f)
        SkyCompute.synchronous = true
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f), LocalLayoutDirection provides dir.value, LocalAmbientMotion provides false) {
                Box(Modifier.requiredSize(412.dp, 400.dp).testTag("sky")) {
                    LivingSky(s, layout, s.nowFraction, scrubbing = false, page = Color(0xFF0E1A33), description = "", modifier = Modifier.fillMaxSize(),
                        stillTime = 0.1f, simple = simple.value)
                }
            }
        }
        rule.mainClock.autoAdvance = false
        fun shot(): Bitmap {
            repeat(4) {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                rule.mainClock.advanceTimeBy(400)
                rule.mainClock.advanceTimeByFrame()
            }
            val r = rule.onNodeWithTag("sky").fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val all = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(all))
            return Bitmap.createBitmap(all, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt())
        }
        val out = File("build/design").apply { mkdirs() }
        for (low in listOf(false, true)) {
            simple.value = low
            dir.value = LayoutDirection.Ltr; val en = shot()
            dir.value = LayoutDirection.Rtl; val ar = shot()
            File(out, "sky_language_${if (low) "low" else "medium"}_en.png").outputStream().use { en.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(out, "sky_language_${if (low) "low" else "medium"}_ar.png").outputStream().use { ar.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(en.width, ar.width); assertEquals(en.height, ar.height)
            val a = IntArray(en.width * en.height); val b = IntArray(a.size)
            en.getPixels(a, 0, en.width, 0, 0, en.width, en.height); ar.getPixels(b, 0, ar.width, 0, 0, ar.width, ar.height)
            var differ = 0
            for (i in a.indices) if (a[i] != b[i]) differ++
            assertTrue("${if (low) "low" else "medium"}: $differ of ${a.size} pixels differ between Arabic and English", differ <= a.size / 1000)
            // (And the scene is not blank: something was drawn.)
            assertTrue(a.toSet().size > 50)
        }
        SkyCompute.synchronous = false
    }
}
