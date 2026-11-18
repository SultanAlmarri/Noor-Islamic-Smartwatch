package com.noor.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.Scenery
import com.noor.app.data.TimeFormat
import com.noor.app.widget.WidgetAccent
import com.noor.app.widget.WidgetBackground
import com.noor.app.widget.WidgetKind
import com.noor.app.widget.WidgetOptions
import com.noor.app.widget.WidgetTextSize
import com.noor.app.widget.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Every widget (0.23) at every size it supports, in English and Arabic, over its backgrounds and with a few options,
 * built as the home screen gets it and drawn as Android draws it, into build/widgets (for review: software renders,
 * not a launcher). Each is checked: no text is cut short ("…"), wider than its view or outside the widget, and no
 * view leaves the widget's bounds. Also drawn with the phone in Arabic and Minhad in English (the owner's phone: the
 * home screen lays widgets out in the phone's language unless they say otherwise), and at the largest font size.
 * One test method (DataStore allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGalleryTest {
    private val out = File("build/widgets").apply { mkdirs() }

    /** The sizes each kind is drawn at, in dp (portrait width × height), as phones' home screens give them. */
    private val sizes = mapOf(
        WidgetKind.NEXT to listOf(150 to 64, 170 to 84, 250 to 84, 170 to 170, 250 to 180),
        WidgetKind.TODAY to listOf(340 to 84, 300 to 64, 340 to 180, 300 to 130, 170 to 280, 170 to 380, 340 to 280, 340 to 380, 170 to 170),
        WidgetKind.SKY to listOf(340 to 180, 340 to 280, 250 to 120),
        WidgetKind.RING to listOf(150 to 150, 170 to 185, 250 to 250),
        WidgetKind.STRIP to listOf(340 to 84, 300 to 64, 420 to 84),
        WidgetKind.DATE to listOf(150 to 64, 170 to 84, 340 to 84, 170 to 170, 340 to 180),
    )

    @Test fun everyWidgetAtEverySize() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("dxb"); repo.setScenery(Scenery.DUNES)
        val dubai = ZoneId.of("Asia/Dubai")
        // Wednesday 4 November 2026 in Dubai: 14:10, before Asr; 15:50, Asr's "+" period.
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, dubai).toInstant()
        val problems = ArrayList<String>()
        var count = 0

        suspend fun shoot(name: String, kind: WidgetKind, o: WidgetOptions, w: Int, h: Int, at: Instant = before, host: Context = app) {
            val views = Widgets.preview(app, kind, o, w, h, at)!!
            val d = app.resources.displayMetrics.density
            val frame = FrameLayout(host)
            val v = views.apply(host, frame)
            frame.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val pw = (w * d).toInt(); val ph = (h * d).toInt()
            frame.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, pw, ph)
            problems += check(name, frame, pw, ph)
            // On a quiet wallpaper, as on a home screen, with a margin.
            val m = (12 * d).toInt()
            val bmp = Bitmap.createBitmap(pw + 2 * m, ph + 2 * m, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawPaint(android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), 0xFF3B4C6B.toInt(), 0xFFB98A6B.toInt(), android.graphics.Shader.TileMode.CLAMP)
            })
            c.translate(m.toFloat(), m.toFloat())
            frame.draw(c)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            count++
        }

        val glass = WidgetOptions(background = WidgetBackground.GLASS, opacity = 78)
        val light = WidgetOptions(background = WidgetBackground.LIGHT, opacity = 92, accent = WidgetAccent.GOLD)
        for (lang in listOf("en", "ar")) {
            repo.setLanguage(if (lang == "ar") Language.ARABIC else Language.ENGLISH)
            for ((kind, list) in sizes) for ((w, h) in list) {
                val tag = "${kind.name.lowercase()}_${w}x${h}"
                shoot("${tag}_${lang}", kind, kind.defaults, w, h)
                if (kind != WidgetKind.SKY) {
                    shoot("${tag}_${lang}_light", kind, light, w, h)
                    if (kind.defaults.background == WidgetBackground.SKY) shoot("${tag}_${lang}_glass", kind, glass, w, h)
                }
            }
        }
        // A few options: teal on dark, no background, large text, without seconds or countdown, after a prayer.
        repo.setLanguage(Language.ENGLISH)
        val after = ZonedDateTime.of(2026, 11, 4, 15, 50, 0, 0, dubai).toInstant()
        for (kind in WidgetKind.entries) {
            val (w, h) = sizes[kind]!![if (kind == WidgetKind.TODAY) 2 else 1]
            val tag = "${kind.name.lowercase()}_${w}x${h}"
            shoot("${tag}_en_dark_teal", kind, WidgetOptions(background = WidgetBackground.DARK, opacity = 100, accent = WidgetAccent.TEAL), w, h)
            shoot("${tag}_en_clear_large", kind, WidgetOptions(background = WidgetBackground.CLEAR, textSize = WidgetTextSize.LARGE), w, h)
            shoot("${tag}_en_noseconds_rose", kind, kind.defaults.copy(seconds = false, accent = WidgetAccent.ROSE), w, h)
            shoot("${tag}_en_after", kind, kind.defaults, w, h, after)
            if (kind.options.contains(com.noor.app.widget.WidgetOption.COUNTDOWN)) shoot("${tag}_en_nocount", kind, kind.defaults.copy(countdown = false), w, h)
        }
        // The owner's phone: the phone in Arabic, Minhad in English with Western digits.
        val arabicPhone = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale("ar")); setLayoutDirection(Locale("ar")) })
        for (kind in WidgetKind.entries) {
            val (w, h) = sizes[kind]!![if (kind == WidgetKind.TODAY) 2 else 1]
            shoot("${kind.name.lowercase()}_${w}x${h}_en_arabicphone", kind, kind.defaults, w, h, host = arabicPhone)
        }
        // Arabic-Indic digits and 24 hours; then the largest font size, both languages.
        repo.setLanguage(Language.ARABIC); repo.setNumerals(Numerals.ARABIC_INDIC); repo.setTimeFormat(TimeFormat.H24)
        for (kind in WidgetKind.entries) {
            val (w, h) = sizes[kind]!![if (kind == WidgetKind.TODAY) 2 else 1]
            shoot("${kind.name.lowercase()}_${w}x${h}_ar_indic24", kind, kind.defaults, w, h)
        }
        repo.setNumerals(Numerals.WESTERN); repo.setTimeFormat(TimeFormat.H12)
        RuntimeEnvironment.setFontScale(1.3f)
        for (lang in listOf("en", "ar")) {
            repo.setLanguage(if (lang == "ar") Language.ARABIC else Language.ENGLISH)
            for ((kind, list) in sizes) for ((w, h) in list) shoot("${kind.name.lowercase()}_${w}x${h}_${lang}_font130", kind, kind.defaults, w, h)
        }
        RuntimeEnvironment.setFontScale(1f)
        println("widgets drawn: $count; problems: ${problems.size}")
        problems.forEach { println("  $it") }
        assertTrue("every widget fits:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** What does not fit: a text cut short or wider than its view, a view outside the widget. */
    private fun check(name: String, root: ViewGroup, w: Int, h: Int): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            if (v.width == 0 && v.height == 0) return
            if (v.id == View.NO_ID && v !is TextView) return
            val r = Rect(0, 0, v.width, v.height)
            root.offsetDescendantRectToMyCoords(v, r)
            val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault("?")
            // The wheel is far larger than its window on purpose; the background fills the widget.
            if (idName == "widget_count_wheel") return
            val slack = 1
            if (r.left < -slack || r.top < -slack || r.right > w + slack || r.bottom > h + slack) out += "$name: $idName outside the widget ($r in ${w}x$h)"
            if (v is TextView && v.text.isNotEmpty()) {
                val l = v.layout
                if (l != null) {
                    for (line in 0 until l.lineCount) if (l.getEllipsisCount(line) > 0) out += "$name: $idName cut short: \"${v.text}\""
                    val avail = v.width - v.totalPaddingLeft - v.totalPaddingRight
                    if (l.lineCount == 1 && l.getLineWidth(0) > avail + 1) out += "$name: $idName too wide (${l.getLineWidth(0)} > $avail): \"${v.text}\""
                }
                if (v.height < v.lineHeight * 0.8f) out += "$name: $idName squeezed (${v.height} < ${v.lineHeight}): \"${v.text}\""
            }
            if (v is ImageView && idName == "widget_count_main") {
                val dr = v.drawable
                if (dr != null && (dr.intrinsicWidth > v.width + 1)) out += "$name: countdown squeezed (${dr.intrinsicWidth} > ${v.width})"
            }
        }
        walk(root)
        return out
    }

    @After fun restore() { RuntimeEnvironment.setFontScale(1f) }
}
