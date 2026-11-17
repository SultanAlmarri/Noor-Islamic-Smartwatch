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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.noor.app.sky.Biome
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
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
 * (0.20) The pictures of the low graphics on the scenery cards (Settings → Home scenery): each scenery as the simplest
 * sky draws it ([SimpleSkyLayer], the app's own code), on a fine afternoon in Dubai, framed as the high graphics'
 * cards are (560 × 420, the horizon 62 % down, the whole width of a phone). Written to build/cards/low_<id>.png; the
 * shipped pictures (res/drawable-nodpi/scenery_low_<id>.webp) are these, converted (docs/HANDOVER.md section 31).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SceneryCardPicturesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Asia/Dubai")
    private val date = LocalDate.of(2026, 11, 4)
    private val point = GeoPoint(25.2048, 55.2708)

    private fun scene(terrain: Terrain): SkyScene {
        val config = WatchConfig(point = point, zoneId = zone.id, placeEn = "Dubai", placeAr = null, fromGps = false,
            profileId = Profiles.MWL.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED, offsets = emptyMap(),
            hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 0L)
        val day = config.day(date)
        val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
        val now = ZonedDateTime.of(date.atTime(15, 40), zone).toInstant()
        val landSeed = "Dubai".hashCode().toLong() * 7919L + (point.latitude * 100).toLong()
        return SkyScene(SunPath.of(date, point, zone), events, now, Prayer.MAGHRIB, 14, false, terrain, landSeed * 1_000_003L + date.toEpochDay(), landSeed)
    }

    @Test fun lowCards() {
        val biome = mutableStateOf(Biome.DUNES)
        val layout = SceneLayout(cloudTop = 0.05f, arcTop = 0.18f, horizon = 0.62f, ground = 0.98f)
        rule.setContent {
            val s = scene(Terrain.of(biome.value))
            CompositionLocalProvider(LocalDensity provides Density(560f / 412f)) {
                Box(Modifier.requiredSize(412.dp, 309.dp).testTag("card")) {
                    SimpleSkyLayer(s, layout, s.nowFraction, Color(0xFFF3EFE7), rtl = false, parallax = { 0f }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val dir = File("build/cards").apply { mkdirs() }
        rule.mainClock.autoAdvance = false
        for (b in Biome.entries) {
            biome.value = b
            repeat(4) {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                rule.mainClock.advanceTimeBy(400)
                rule.mainClock.advanceTimeByFrame()
            }
            // (Drawn from the window, as the design renders are: the clock does not run by itself here.)
            val r = rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val root = rule.activity.window.decorView
            val all = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(all))
            val bmp = Bitmap.createBitmap(all, r.left.toInt(), r.top.toInt(), 560, 420)
            File(dir, "low_${b.id}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
