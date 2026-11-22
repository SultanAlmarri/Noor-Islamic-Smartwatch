package com.noor.app.design

import android.Manifest
import android.app.Activity
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.data.LocationMode
import com.noor.app.data.ThemeMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.onboarding.ButtonGold
import com.noor.app.ui.onboarding.ButtonInk
import com.noor.app.ui.onboarding.Dawn
import com.noor.app.ui.onboarding.DawnSky
import com.noor.app.ui.onboarding.OnboardingScreen
import com.noor.app.ui.onboarding.WELCOME_BAR
import com.noor.app.ui.onboarding.WelcomeText
import com.noor.app.ui.onboarding.WelcomeTextQuiet
import com.noor.app.ui.opening.LocalOpening
import com.noor.app.ui.opening.OpeningMode
import com.noor.app.ui.opening.OpeningState
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Sky
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.util.Locale

/**
 * The welcome (0.25): every page of it drawn, in English and Arabic, on a phone (412 x 915 dp, also with text at
 * 1.5), a small phone (360 x 640, also with text at 1.3), an unfolded Fold (673 x 841, and turned: 841 x 673) and a
 * phone on its side (915 x 412), to build/design/welcome; and what must hold of it, checked:
 *
 * - the words on the sky meet 4.5:1 against the sky's own drawn pixels, at every stage of the dawn (the whole part of
 *   the sky that can hold words, and under every text of every page drawn here);
 * - no button is cut off, on any size, in either language; on the pages that ask for something every word is on
 *   the screen without scrolling; each page has one heading; each language shows its own script only;
 * - the welcome asks what it asked before and does what it did: the three answers are recorded, "Not now" and
 *   "Choose a city instead" lead where they led, and the way back returns to the page before;
 * - the exact alarms' page (0.25) is met only when the Adhan was turned on and Android does not allow exact alarms;
 *   its button opens Android's page for this app, and either answer ends the welcome;
 * - with ambient motion off nothing moves.
 *
 * Robolectric: software renders and the app's real code, not a phone. Android's own permission dialogs do not exist
 * here: the permissions are granted beforehand, so each request answers at once.
 * One test method: DataStore is one instance per process.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h950dp-xhdpi")
class WelcomeDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var arabic by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private var frameW by mutableIntStateOf(412)
    private var frameH by mutableIntStateOf(915)
    private var ambient by mutableStateOf(false)
    private var fresh by mutableIntStateOf(0)
    /** When set, the sky alone is drawn: (light, the Earth's edge in px), with [skyBand]. */
    private var skyOnly by mutableStateOf<Pair<Float, Float>?>(null)
    private var skyBand by mutableStateOf(Dawn.Band)
    private var opening by mutableStateOf(OpeningState.None)
    private var finished = 0
    private var city = 0
    private val problems = mutableListOf<String>()
    private val dir = File("build/design/welcome")

    @Test fun theWelcome() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        dir.deleteRecursively(); dir.mkdirs()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); this.fontScale = this@WelcomeDesignTest.fontScale; setLayoutDirection(locale) }
            CompositionLocalProvider(
                LocalAmbientMotion provides ambient,
                LocalContext provides base.createConfigurationContext(config),
                LocalConfiguration provides config,
                // (The frame stands at the window's left in both languages; the page inside it has its own direction.)
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalActivityResultRegistryOwner provides rule.activity,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalOpening provides opening,
            ) {
                NoorTheme(ThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF202020))) {
                        Box(Modifier.size(frameW.dp, frameH.dp).clipToBounds()) {
                            CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                                key(fresh) { OnboardingScreen(PaddingValues(top = 28.dp, bottom = 16.dp), onChooseCity = { city++ }, onFinished = { finished++ }) }
                                // (Over the page, which stays as it is underneath: the sky covers all of it.)
                                skyOnly?.let { sky -> DawnSky({ sky.first }, { sky.second }, Modifier.fillMaxSize(), skyBand, stars = false) }
                            }
                        }
                    }
                }
            }
        }

        // ---- The pages, drawn and checked, in each size and language --------------------------------------------
        data class Frame(val tag: String, val w: Int, val h: Int, val scale: Float)
        val frames = listOf(Frame("phone", 412, 915, 1f), Frame("small", 360, 640, 1f), Frame("small_130", 360, 640, 1.3f), Frame("fold", 673, 841, 1f), Frame("phone_150", 412, 915, 1.5f),
            Frame("landscape", 915, 412, 1f), Frame("fold_turned", 841, 673, 1f))
        for (f in frames) for (ar in listOf(false, true)) {
            frameW = f.w; frameH = f.h; fontScale = f.scale; arabic = ar
            val tag = "${f.tag}_${if (ar) "ar" else "en"}"
            open()
            page("0_cover_$tag", 0, primary = t("Continue", "متابعة"), secondary = null)
            click("Continue", "متابعة")
            page("1_place_$tag", 1, primary = t("Use my location", "استخدام موقعي"), secondary = t("Choose a city instead", "اختيار مدينة بدلًا من ذلك"))
            click("Use my location", "استخدام موقعي")
            page("2_travel_$tag", 2, primary = t("Continue", "متابعة"), secondary = t("Not now", "ليس الآن"))
            click("Continue", "متابعة")
            page("3_adhan_$tag", 3, primary = t("Turn on the Adhan", "تشغيل الأذان"), secondary = t("Not now", "ليس الآن"))
            // The page that is only met when Android does not yet let Minhad ring at an exact time.
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            click("Turn on the Adhan", "تشغيل الأذان")
            page("4_minute_$tag", 4, primary = t("Allow exact alarms", "السماح بالمنبّهات الدقيقة"), secondary = t("Not now", "ليس الآن"))
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
        frameW = 412; frameH = 915; fontScale = 1f; arabic = false
        // (Those pages turned the Adhan on without ending the welcome: off again, as on a first run.)
        runBlocking { repo.updateAlerts { it.copy(enabled = false) } }

        // ---- Every stage of the dawn: the part of the sky that can hold words ------------------------------------
        val density = rule.activity.resources.displayMetrics.density
        // (An upright phone, with the band of first light at its full height; a phone on its side, with the thin one.)
        for ((w, h, edge) in listOf(Triple(412, 915, 700f), Triple(915, 412, 260f))) {
            frameW = w; frameH = h; skyBand = Dawn.band(h.dp)
            val band = skyBand.value * density
            val crest = edge * density
            var light = Dawn.BEFORE
            while (light <= Dawn.LAST + 0.001f) {
                skyOnly = light to crest; settle()
                val sky = capture()
                if (h > w && light == light.toInt().toFloat()) save(sky, "sky_light_${light.toInt()}")
                val words = Rect(0f, 0f, sky.width.toFloat(), crest - band)
                val white = worst(sky, words, WelcomeText); val quiet = worst(sky, words, WelcomeTextQuiet)
                if (white < 4.5) problems += "$w x $h, light $light: white on the sky is $white:1"
                if (quiet < 4.5) problems += "$w x $h, light $light: the quiet text on the sky is $quiet:1"
                if (light <= 0.5f) worst(sky, words, Sky.GoldOnSky).let { if (it < 4.5) problems += "$w x $h, light $light: the gold name on the sky is $it:1" }
                println("welcome sky $w x $h, light ${"%.2f".format(light)}: white ${"%.2f".format(white)}:1, quiet ${"%.2f".format(quiet)}:1 at the least")
                light += 0.25f
            }
            if (h > w) { skyOnly = Dawn.SUNRISE to crest; settle(); save(capture(), "sky_light_4_sunrise") }
        }
        skyOnly = null; frameW = 412; frameH = 915; skyBand = Dawn.Band
        // The main button's own colours.
        ButtonGold.forEach { g -> Sky.contrast(ButtonInk, g).let { if (it < 4.5) problems += "the main button's words are $it:1 on its gold" } }

        // ---- What the welcome does --------------------------------------------------------------------------------
        // Every answer yes (the pages above already gave the first two): the place follows the phone, the background
        // check is on, the Adhan is on, the welcome is done, and the home screen is opened.
        finished = 0; city = 0
        open(); click("Continue", "متابعة"); click("Use my location", "استخدام موقعي"); click("Continue", "متابعة")
        assertTrue("Before the Adhan's answer the alerts are off", !runBlocking { repo.current() }.alerts.enabled)
        assertTrue("The welcome is not done before its last answer", !runBlocking { repo.current() }.onboardingDone)
        click("Turn on the Adhan", "تشغيل الأذان")
        wait("the answers recorded") { runBlocking { repo.current() }.let { it.onboardingDone && it.alerts.enabled && it.backgroundUpdates && it.locationMode == LocationMode.AUTO } }
        wait("the home screen opened") { finished == 1 }
        assertEquals(0, city)

        // Exact alarms (0.25). Allowed already (above): the Adhan's answer ended the welcome, no page more. Not allowed:
        // one page more, which opens Android's own page for this app; coming back from it ends the welcome.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        finished = 0; city = 0
        open(); click("Continue", ""); click("Use my location", ""); click("Continue", ""); click("Turn on the Adhan", "")
        has("The Adhan, on the minute")
        assertEquals("The welcome waits for the answer", 0, finished)
        val shadow = Shadows.shadowOf(rule.activity)
        while (shadow.nextStartedActivityForResult != null) { /* (what was started before) */ }
        click("Allow exact alarms", "")
        val asked = shadow.nextStartedActivityForResult
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, asked?.intent?.action)
        assertEquals("package:" + rule.activity.packageName, asked?.intent?.dataString)
        assertEquals("The welcome waits while Android's page is open", 0, finished)
        rule.runOnUiThread { shadow.receiveResult(asked!!.intent, Activity.RESULT_CANCELED, null) }
        settle()
        wait("the home screen opened after Android's page") { finished == 1 }
        assertTrue("The Adhan stays on", runBlocking { repo.current() }.alerts.enabled)
        // "Not now" there: the welcome ends, the Adhan stays on (the Adhan tab says what is missing).
        finished = 0
        open(); click("Continue", ""); click("Use my location", ""); click("Continue", ""); click("Turn on the Adhan", ""); click("Not now", "")
        wait("the home screen opened") { finished == 1 }
        assertTrue("The Adhan stays on", runBlocking { repo.current() }.alerts.enabled)
        // Back from there to the Adhan's page, and "Not now" on it: the Adhan is off again.
        finished = 0
        open(); click("Continue", ""); click("Use my location", ""); click("Continue", ""); click("Turn on the Adhan", "")
        has("The Adhan, on the minute"); back(); has("Hear the Adhan at each prayer"); click("Not now", "")
        wait("the home screen opened") { finished == 1 }
        wait("the Adhan off again") { !runBlocking { repo.current() }.alerts.enabled }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        // The way back: each page returns to the one before, and from the cover nothing holds the back gesture.
        open(); click("Continue", "متابعة"); click("Choose a city instead", "")
        has("Hear the Adhan at each prayer"); back(); has("Use my location")
        click("Use my location", ""); has("Keep times right when you travel"); back(); has("Use my location"); back(); has("Prayer times from the sky above you")
        assertTrue("On the cover the back gesture is Android's own (it leaves the app)", !rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        // "Choose a city instead", then "Not now": the location page is opened, not the home screen.
        finished = 0; city = 0
        click("Continue", "متابعة"); click("Choose a city instead", ""); click("Not now", "")
        wait("the location page opened") { city == 1 }
        assertEquals(0, finished)
        // "Use my location" after having gone back from "Choose a city instead": the home screen, not the location page.
        finished = 0; city = 0
        open(); click("Continue", ""); click("Choose a city instead", ""); back(); click("Use my location", ""); click("Not now", ""); click("Not now", "")
        wait("the home screen opened") { finished == 1 }
        assertEquals(0, city)

        // ---- With ambient motion off nothing moves ----------------------------------------------------------------
        open()
        rule.mainClock.autoAdvance = false
        rule.onAllNodes(hasText("Continue")).onFirst().performClick()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        val soon = capture()
        rule.mainClock.advanceTimeBy(4000)
        val later = capture()
        save(soon, "still_soon"); save(later, "still_later")
        assertTrue("With ambient motion off the next page is there at once and nothing moves after", same(soon, later))

        // ---- With motion on: frames of the way in, of a page turning, and of the way out (to look at) ------------
        val d = density
        opening = OpeningState(OpeningMode.NONE).apply { mark = Rect((412 - 288) / 2f * d, (915 - 288) / 2f * d, (412 + 288) / 2f * d, (915 + 288) / 2f * d) }
        ambient = true; fresh++
        rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame()
        var at = 0
        for (ms in listOf(0, 200, 500, 800, 1200, 1700, 2600)) { rule.mainClock.advanceTimeBy((ms - at).toLong()); at = ms; save(capture(), "motion_in_${"%04d".format(ms)}") }
        rule.mainClock.advanceTimeBy(1500)
        rule.onAllNodes(hasText("Continue")).onFirst().performClick()
        at = 0
        for (ms in listOf(60, 160, 320, 700, 1600)) { rule.mainClock.advanceTimeBy((ms - at).toLong()); at = ms; save(capture(), "motion_turn_${"%04d".format(ms)}") }
        rule.onAllNodes(hasText("Choose a city instead")).onFirst().performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.onAllNodes(hasText("Not now")).onFirst().performClick()
        at = 0
        for (ms in listOf(100, 250, 450, 600)) { rule.mainClock.advanceTimeBy((ms - at).toLong()); at = ms; save(capture(), "motion_out_${"%04d".format(ms)}") }
        rule.mainClock.autoAdvance = true

        assertTrue("The welcome:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun t(en: String, ar: String) = if (arabic) ar else en

    private fun settle() {
        for (i in 0 until 12) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle(); Thread.sleep(5) }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    private fun open() { fresh++; settle() }

    private fun click(en: String, ar: String) {
        val text = if (arabic) ar else en
        assertTrue("\"$text\" is on the page", rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(hasText(text)).onFirst().performClick(); settle()
    }

    private fun has(text: String) = assertTrue("\"$text\" is on the page", rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }

    private fun wait(what: String, ok: () -> Boolean) {
        for (i in 0 until 400) { if (ok()) return; Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(25) }
        assertTrue(what, ok())
    }

    private fun capture(): Bitmap {
        val root = rule.activity.window.decorView
        val d = root.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        return Bitmap.createBitmap(bmp, 0, 0, (frameW * d).toInt().coerceAtMost(bmp.width), (frameH * d).toInt().coerceAtMost(bmp.height))
    }

    private fun save(bmp: Bitmap, name: String) = File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun same(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return pa.contentEquals(pb)
    }

    /** The least contrast of [text] (as it is laid over the sky) against the pixels of [sky] in [area]. */
    private fun worst(sky: Bitmap, area: Rect, text: Color): Double {
        var least = Double.MAX_VALUE
        val x0 = area.left.toInt().coerceIn(0, sky.width - 1); val x1 = area.right.toInt().coerceIn(0, sky.width)
        val y0 = area.top.toInt().coerceIn(0, sky.height - 1); val y1 = area.bottom.toInt().coerceIn(0, sky.height)
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val px = Color(sky.getPixel(x, y))
                val c = Sky.contrast(text.compositeOver(px), px)
                if (c < least) least = c
                x += 3
            }
            y += 2
        }
        return least
    }

    /** Draws the page that is open, saves it, and checks it: its words against its sky, its buttons whole, its language. */
    private fun page(name: String, step: Int, primary: String, secondary: String?) {
        val d = rule.activity.resources.displayMetrics.density
        val shot = capture()
        save(shot, name)
        val barTop = rule.onNodeWithTag(WELCOME_BAR).fetchSemanticsNode().boundsInRoot.top
        val band = Dawn.band(frameH.dp)
        val crest = barTop + band.value * d
        val nodes = rule.onAllNodes(SemanticsMatcher("text") { it.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true).fetchSemanticsNodes()
        val texts = nodes.map { it.config[SemanticsProperties.Text].joinToString(" ") { t -> t.text } to it.boundsInRoot }
        // A page that asks for something says why in full, without scrolling, before Android is asked (on every size here).
        if (step > 0) nodes.forEach { n ->
            if (n.boundsInRoot.height < n.size.height - 1f || n.boundsInRoot.width < n.size.width - 1f)
                problems += "$name: \"${n.config[SemanticsProperties.Text].joinToString(" ") { t -> t.text }}\" is not whole on the screen"
        }
        // The sky alone, as this page has it (the same light, the same edge), without its stars.
        skyBand = band; skyOnly = Dawn.lightFor(step) to crest; settle()
        val sky = capture()
        skyOnly = null; settle()
        // (That it is this page's sky: one point of the band of first light at the screen's side, where nothing else is drawn.)
        val probeY = (crest - band.value * d * 0.5f).toInt()
        val a = Color(shot.getPixel(2, probeY)); val b = Color(sky.getPixel(2, probeY))
        if (maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue)) > 0.012f) problems += "$name: the sky drawn alone is not the page's sky ($a, $b)"

        val frame = Rect(0f, 0f, frameW * d, frameH * d)
        for ((text, bounds) in texts) {
            if (bounds.width <= 0f || bounds.height <= 0f) continue
            when {
                text == primary -> {
                    if (bounds.bottom > frame.bottom || bounds.left < 0f || bounds.right > frame.right) problems += "$name: the main button is cut off"
                }
                bounds.top >= crest -> {
                    // On the Earth: the quiet button.
                    if (text != secondary) problems += "$name: \"$text\" is below the Earth's edge"
                    if (bounds.bottom > frame.bottom) problems += "$name: \"$text\" is cut off"
                    worst(sky, bounds, Sky.GoldOnSky).let { if (it < 4.5) problems += "$name: \"$text\" is $it:1 on the Earth" }
                }
                else -> {
                    if (bounds.bottom > barTop + 1f) problems += "$name: \"$text\" reaches into the band of first light"
                    val colour = if (text == "منهاد") Sky.GoldOnSky else WelcomeTextQuiet
                    worst(sky, bounds, colour).let { if (it < 4.5) problems += "$name: \"$text\" is $it:1 on its sky" }
                }
            }
        }
        if (texts.none { it.first == primary }) problems += "$name: no main button \"$primary\""
        if (secondary != null && texts.none { it.first == secondary }) problems += "$name: no second button \"$secondary\""
        val headings = rule.onAllNodes(SemanticsMatcher("heading") { it.config.getOrNull(SemanticsProperties.Heading) != null }, useUnmergedTree = true).fetchSemanticsNodes().size
        if (headings != 1) problems += "$name: $headings headings"
        // Each language its own script (the Kufi wordmark is artwork; a watch's own name is a name).
        texts.forEach { (text, _) ->
            if (arabic) { if (Regex("[A-Za-z]").containsMatchIn(text.replace("Galaxy Watch", ""))) problems += "$name (Arabic): \"$text\"" }
            else if (Regex("[\\u0600-\\u06FF]").containsMatchIn(text.replace("منهاد", ""))) problems += "$name (English): \"$text\""
        }
    }
}
