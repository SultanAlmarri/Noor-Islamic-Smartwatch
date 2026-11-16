package com.noor.app.ui.watch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.noor.app.ui.watch.studio.StudioRepository
import com.noor.face.FaceFrame
import com.noor.face.FaceRenderer
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.Ambient
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceDigits
import com.noor.prayer.face.FaceLanguage
import com.noor.prayer.face.FaceTemplates
import com.noor.prayer.face.Hands
import com.noor.prayer.face.Marks
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Minhad Studio (0.18): the shared renderer draws every template, in English and Arabic, active and always-on, with
 * the phone's own fonts (the same files as the watch's). Pictures go to build/design/studio (software renders, for
 * review; not proof of how a watch shows them). Checked: every text drawn on an Arabic face is free of Latin letters
 * (and of Western digits when Arabic-Indic digits are chosen), every English face free of Arabic letters, and
 * always-on lights far fewer pixels than the face itself (OLED).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StudioRenderTest {
    private val dubai = WatchConfig(
        point = GeoPoint(25.2048, 55.2708, 5.0), zoneId = "Asia/Dubai", placeEn = "Dubai", placeAr = "دبي", fromGps = false,
        profileId = Profiles.DUBAI_IACAD.id, asrSchool = null, highLatitudeRule = HighLatitudeRule.ANGLE_BASED,
        offsets = emptyMap(), hijriAdjust = 0, language = "en", arabicDigits = false, use24h = false, updatedAtEpochMs = 1L,
    )

    /** A canvas that notes every text it is asked to draw. */
    private class Recording(b: Bitmap) : Canvas(b) {
        val texts = mutableListOf<String>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) { texts += text; super.drawText(text, x, y, paint) }
    }

    @Test fun templatesInBothLanguagesActiveAndAlwaysOn() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val renderer = FaceRenderer(ctx, StudioRepository.fonts(ctx))
        val dir = File("build/design/studio").apply { mkdirs() }
        val size = 450
        val bounds = Rect(0, 0, size, size)
        val latin = Regex("[A-Za-z]"); val western = Regex("[0-9]"); val arabicLetter = Regex("[\\u0600-\\u06FF]")
        val problems = mutableListOf<String>()
        val extras = listOf(
            "numerals" to FaceTemplates.DIAL.copy(hands = Hands.BOLD, marks = Marks.NUMERALS),
            "slim" to FaceTemplates.MINIMAL.copy(hands = Hands.SLIM, ambient = Ambient.DIMMED, ambientOutline = true),
        )
        val designs = FaceTemplates.all.map { it.id.removePrefix("tpl_") to it } + extras
        // Afternoon (the Sun up, Asr next) and night (the moon, Fajr next).
        val moments = listOf("day" to ZonedDateTime.of(2026, 11, 16, 14, 9, 30, 0, ZoneId.of("Asia/Dubai")),
            "night" to ZonedDateTime.of(2026, 11, 16, 2, 41, 12, 0, ZoneId.of("Asia/Dubai")))
        for ((name, base) in designs) for ((lang, d) in listOf("en" to base.copy(language = FaceLanguage.EN, digits = FaceDigits.WESTERN),
            "ar" to base.copy(language = FaceLanguage.AR, digits = FaceDigits.ARABIC))) for ((when_, t) in moments) {
            val data = FaceData.build(d, dubai, t.toInstant(), t.zone, battery = 0.76f)
            val lit = IntArray(2)
            for ((i, ambient) in listOf(false, true).withIndex()) {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Recording(bmp)
                renderer.draw(canvas, bounds, d, data, FaceFrame().set(t.hour, t.minute, t.second, ambient))
                if (when_ == "day" || !ambient) File(dir, "${name}_${lang}_${when_}${if (ambient) "_aod" else ""}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                lit[i] = litPixels(bmp)
                for (s in canvas.texts) {
                    if (lang == "ar" && latin.containsMatchIn(s)) problems += "$name ar: Latin in \"$s\""
                    if (lang == "ar" && western.containsMatchIn(s)) problems += "$name ar: Western digit in \"$s\""
                    if (lang == "en" && arabicLetter.containsMatchIn(s)) problems += "$name en: Arabic in \"$s\""
                }
                assertTrue("$name $lang: nothing drawn", canvas.texts.isNotEmpty() || d.hands != Hands.NONE)
            }
            assertTrue("$name $lang $when_: the face is blank (${lit[0]} px)", lit[0] > size * size / 200)
            // Always-on never lights more than the face itself, and under 15 % of the screen (OLED guidance).
            assertTrue("$name $lang $when_: always-on lights ${lit[1]} px against ${lit[0]}", lit[1] < size * size * 0.15 && lit[1] <= lit[0])
        }
        assertTrue("Text in the wrong language:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    private fun litPixels(b: Bitmap): Int {
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.count { (Color.red(it) + Color.green(it) + Color.blue(it)) > 60 }
    }
}
