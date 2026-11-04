package com.noor.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.noor.prayer.WatchConfig
import com.noor.wear.complication.ComplicationBuilder
import com.noor.wear.tile.PrayerTileService
import com.noor.wear.ui.NoorWearContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor
import android.os.Looper

/**
 * Renders the watch app, the tile and checks the complication data on a 450 px round screen (Galaxy Watch 5 Pro size).
 * Images go to build/screenshots; they are software renders, not photos of a watch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-round-watch-xhdpi")
class WearScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = File("build/screenshots").apply { mkdirs() }

    private fun save(bmp: Bitmap, name: String) {
        // Mask to the round display so the picture looks like the watch.
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.TRANSPARENT)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(bmp.width / 2f, bmp.height / 2f, bmp.width / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(bmp, 0f, 0f, p)
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun drawView(v: View): Bitmap {
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.BLACK); v.draw(c)
        return bmp
    }

    @Test fun appTileAndComplication() {
        var config by mutableStateOf<WatchConfig?>(TestConfigs.dubai("en"))
        rule.setContent { NoorWearContent(config, clock = { TestConfigs.afternoon }, showFaceSection = false) }

        rule.waitUntil(10_000) { rule.onAllNodesWithText("Asr").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        save(drawView(rule.activity.window.decorView), "w01_app_en")

        config = TestConfigs.dubai("ar", arabicDigits = true)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("العصر").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        save(drawView(rule.activity.window.decorView), "w02_app_ar")

        config = null
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        save(drawView(rule.activity.window.decorView), "w03_app_setup")

        // Tile, rendered by the same renderer library the watch uses.
        val ctx = RuntimeEnvironment.getApplication()
        val device = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(225).setScreenHeightDp(225).setScreenDensity(2f)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND).build()
        val cfg = TestConfigs.dubai("en")
        val iv = Intervals.upcoming(cfg, TestConfigs.afternoon)
        val root = PrayerTileService.prayerLayout(ctx, device, cfg, iv[0], iv.drop(1).take(2))
        val parent = FrameLayout(rule.activity)
        rule.activity.runOnUiThread { rule.activity.setContentView(parent, ViewGroup.LayoutParams(450, 450)) }
        shadowOf(Looper.getMainLooper()).idle()
        val renderer = TileRenderer(rule.activity, Executor { it.run() }) { }
        renderer.inflateAsync(
            LayoutElementBuilders.Layout.Builder().setRoot(root).build(),
            ResourceBuilders.Resources.Builder().setVersion("1").build(), parent,
        )
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
        parent.measure(View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 450, 450)
        save(drawView(parent), "w04_tile_en")

        // Complication: the countdown text is produced by the complication library itself.
        val b = ComplicationBuilder(ctx, cfg)
        val short = b.data(ComplicationType.SHORT_TEXT, iv[0], TestConfigs.afternoon) as ShortTextComplicationData
        assertEquals("Asr", short.title!!.getTextAt(ctx.resources, TestConfigs.afternoon).toString())
        assertEquals("23m", short.text.getTextAt(ctx.resources, TestConfigs.afternoon).toString().replace(" ", ""))
        val long = b.data(ComplicationType.LONG_TEXT, iv[0], TestConfigs.afternoon) as LongTextComplicationData
        val longText = long.text.getTextAt(ctx.resources, TestConfigs.afternoon).toString()
        assertTrue(longText, longText.startsWith("Asr 3:14 PM · "))
        val ranged = b.data(ComplicationType.RANGED_VALUE, iv[0], TestConfigs.afternoon) as RangedValueComplicationData
        assertTrue(ranged.max > ranged.value && ranged.value > 0f)
        File(dir, "w05_complications.txt").writeText(
            "SHORT_TEXT  title='${short.title!!.getTextAt(ctx.resources, TestConfigs.afternoon)}' text='${short.text.getTextAt(ctx.resources, TestConfigs.afternoon)}'\n" +
                "LONG_TEXT   '$longText'\nRANGED      ${ranged.value} of ${ranged.max} s\n",
        )
    }
}
