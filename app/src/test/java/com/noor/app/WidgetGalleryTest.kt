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
import org.junit.Assert.assertEquals
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
        // As the owner's phone names his place from its location: the area, and the city it is in (Dubai's own times).
        val dxb = com.noor.prayer.Places.byId("dxb")!!
        repo.setManualPlace(com.noor.app.data.KnownLocation(dxb.point, dxb.zoneId, "Madinat Al Mataar", dxb.countryCode, dxb.region, 0L,
            "مدينة المطار", "Dubai, United Arab Emirates", "دبي، الإمارات العربية المتحدة", precise = true))
        val dubai = ZoneId.of("Asia/Dubai")
        // Wednesday 4 November 2026 in Dubai: 14:10, before Asr; 15:50, Asr's "+" period.
        val before = ZonedDateTime.of(2026, 11, 4, 14, 10, 0, 0, dubai).toInstant()
        val problems = ArrayList<String>()
        var count = 0
        /** The layout each picture was built with. */
        val layouts = HashMap<String, String>()
        var indic = false

        suspend fun shoot(name: String, kind: WidgetKind, o: WidgetOptions, w: Int, h: Int, at: Instant = before, host: Context = app, preview: Boolean = false) {
            val views = Widgets.preview(app, kind, o, w, h, at)!!
            layouts[name] = app.resources.getResourceEntryName(views.layoutId)
            val d = app.resources.displayMetrics.density
            val frame = FrameLayout(host)
            val v = views.apply(host, frame)
            frame.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val pw = (w * d).toInt(); val ph = (h * d).toInt()
            frame.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, pw, ph)
            problems += check(name, frame, pw, ph, indic)
            problems += overlaps(name, frame)
            contrast(name, frame, pw, ph, o.background == WidgetBackground.SKY || o.background == WidgetBackground.CLEAR || kind == WidgetKind.SKY)
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
            if (preview) {
                // The widget alone, on transparency: the widget picker's picture for launchers before Android 12
                // (tools/widget/previews.py makes res/drawable-nodpi/widget_preview_*.webp from these).
                val alone = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                frame.draw(Canvas(alone))
                File(File(out, "previews").apply { mkdirs() }, "widget_preview_${kind.name.lowercase()}.png").outputStream().use { alone.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            count++
        }

        val glass = WidgetOptions(background = WidgetBackground.GLASS, opacity = 78)
        val light = WidgetOptions(background = WidgetBackground.LIGHT, opacity = 92, accent = WidgetAccent.GOLD)
        for (lang in listOf("en", "ar")) {
            repo.setLanguage(if (lang == "ar") Language.ARABIC else Language.ENGLISH)
            for ((kind, list) in sizes) for ((w, h) in list) {
                val tag = "${kind.name.lowercase()}_${w}x${h}"
                shoot("${tag}_${lang}", kind, kind.defaults, w, h, preview = lang == "en" && (w to h) == kind.previewDp.let { sizes[kind]!!.minByOrNull { s -> Math.abs(s.first - it.first) + Math.abs(s.second - it.second) } })
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
        indic = true
        for (kind in WidgetKind.entries) {
            val (w, h) = sizes[kind]!![if (kind == WidgetKind.TODAY) 2 else 1]
            shoot("${kind.name.lowercase()}_${w}x${h}_ar_indic24", kind, kind.defaults, w, h)
        }
        repo.setNumerals(Numerals.WESTERN); repo.setTimeFormat(TimeFormat.H12)
        indic = false
        RuntimeEnvironment.setFontScale(1.3f)
        for (lang in listOf("en", "ar")) {
            repo.setLanguage(if (lang == "ar") Language.ARABIC else Language.ENGLISH)
            for ((kind, list) in sizes) for ((w, h) in list) shoot("${kind.name.lowercase()}_${w}x${h}_${lang}_font130", kind, kind.defaults, w, h)
        }
        RuntimeEnvironment.setFontScale(1f)
        println("widgets drawn: $count; problems: ${problems.size}; low contrast: ${lowContrast.size}")
        problems.forEach { println("  $it") }
        lowContrast.forEach { println("  contrast $it") }
        assertTrue("every widget fits:\n" + problems.joinToString("\n"), problems.isEmpty())
        // Each size gets the layout made for it; light backgrounds the shadow-free twin.
        val expected = mapOf(
            "today_340x84_en" to "widget_today_row", "today_300x64_en" to "widget_today_row", "today_340x180_en" to "widget_today",
            "today_170x280_en" to "widget_today_column", "today_170x380_en" to "widget_today_column", "today_340x280_en" to "widget_today_grid",
            "today_340x380_en" to "widget_today_grid", "today_170x170_en" to "widget_next_tall", "today_340x180_en_light" to "widget_today_plain",
            "next_170x84_en" to "widget_next", "next_150x64_en" to "widget_next", "next_250x84_en" to "widget_next_wide",
            "next_170x170_en" to "widget_next_tall", "next_250x180_en_light" to "widget_next_tall_plain",
            "ring_150x150_en" to "widget_ring", "strip_340x84_en" to "widget_strip", "sky_340x180_en" to "widget_sky",
            "date_170x84_en" to "widget_date", "date_340x84_en" to "widget_date_wide", "date_340x180_en" to "widget_date_tall",
            "date_170x170_en" to "widget_date_tall", "sky_250x120_en" to "widget_next_tall", "sky_340x280_en" to "widget_sky",
        )
        for ((n, l) in expected) assertEquals(n, l, layouts[n])
    }

    /** What does not fit: a text cut short or wider than its view, a view outside the widget. */
    private fun check(name: String, root: ViewGroup, w: Int, h: Int, indic: Boolean): List<String> {
        val out = ArrayList<String>()
        // The app's digits only, whatever the phone's (Western here unless Arabic-Indic are chosen).
        val other = if (indic) Regex("[0-9]") else Regex("[\u0660-\u0669\u06F0-\u06F9]")
        // The countdown reads left to right in either language.
        root.findViewById<View>(com.noor.app.R.id.widget_count_row)?.let { if (it.layoutDirection != View.LAYOUT_DIRECTION_LTR) out += "$name: countdown not left to right" }
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
            if (v is TextView && other.containsMatchIn(v.text)) out += "$name: $idName has the other digits: \"${v.text}\""
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

    /** The text and countdown on a widget, each where its ink is (a text's line, not its whole view). */
    private fun content(root: ViewGroup): List<Pair<String, Rect>> {
        val out = ArrayList<Pair<String, Rect>>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault("?")
            if (idName == "widget_count_row") {
                val r = Rect(0, 0, v.width, v.height); root.offsetDescendantRectToMyCoords(v, r); out += idName to r; return
            }
            if (v is ViewGroup) { for (i in 0 until v.childCount) walk(v.getChildAt(i)); return }
            if (v is TextView && v.text.isNotEmpty() && v.layout != null) {
                val l = v.layout
                val left = v.totalPaddingLeft + l.getLineLeft(0).toInt(); val right = v.totalPaddingLeft + l.getLineRight(0).toInt()
                val r = Rect(left, v.totalPaddingTop, right, v.height - v.totalPaddingBottom)
                root.offsetDescendantRectToMyCoords(v, r); out += idName to r
            }
        }
        walk(root)
        return out
    }

    /** Two pieces of text (or text and the countdown) that touch or overlap. */
    private fun overlaps(name: String, root: ViewGroup): List<String> {
        val c = content(root)
        val out = ArrayList<String>()
        for (i in c.indices) for (j in i + 1 until c.size) {
            val a = Rect(c[i].second); val b = c[j].second
            if (a.width() <= 0 || b.width() <= 0) continue
            // A pixel's slack: neighbours may share an edge, never ink.
            a.inset(1, 1)
            if (Rect.intersects(a, b)) out += "$name: ${c[i].first} overlaps ${c[j].first}"
        }
        return out
    }

    /** Texts whose colour is below 4.5:1 (or 3:1 over the sky or the wallpaper) against the pixels drawn under them. */
    val lowContrast = ArrayList<String>()

    private fun contrast(name: String, root: ViewGroup, w: Int, h: Int, sky: Boolean) {
        val texts = ArrayList<View>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is ViewGroup) { for (i in 0 until v.childCount) walk(v.getChildAt(i)); if (runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() == "widget_count_row") texts += v; return }
            if (v is TextView && v.text.isNotEmpty()) texts += v
        }
        walk(root)
        val keep = texts.map { it.visibility }
        texts.forEach { it.visibility = View.INVISIBLE }
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Over the gallery's wallpaper, as drawn.
        Canvas(bg).apply { drawColor(0xFF7A6A72.toInt()); root.draw(this) }
        texts.forEachIndexed { i, v -> v.visibility = keep[i] }
        for ((id, r) in content(root)) {
            val v = texts.firstOrNull { t -> runCatching { t.resources.getResourceEntryName(t.id) }.getOrNull() == id } ?: continue
            // The countdown's colour: its picture's most solid pixel.
            val color = if (v is TextView) v.currentTextColor else run {
                val b = (v.findViewById<ImageView>(com.noor.app.R.id.widget_count_main)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                var best = 0xFFFFFFFF.toInt(); var alpha = -1
                if (b != null) for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) { val p = b.getPixel(x, y); if ((p ushr 24) > alpha) { alpha = p ushr 24; best = p } }
                best or 0xFF000000.toInt()
            }
            var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
            val rr = Rect(r); if (!rr.intersect(0, 0, w, h)) continue
            for (y in rr.top until rr.bottom step 2) for (x in rr.left until rr.right step 2) {
                val p = bg.getPixel(x, y); rs += (p shr 16 and 255); gs += (p shr 8 and 255); bs += (p and 255); n++
            }
            if (n == 0) continue
            val under = android.graphics.Color.rgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
            val a = (color ushr 24) / 255f
            fun mix(c: Int, b: Int) = (c * a + b * (1 - a)).toInt()
            val fg = android.graphics.Color.rgb(mix(color shr 16 and 255, under shr 16 and 255), mix(color shr 8 and 255, under shr 8 and 255), mix(color and 255, under and 255))
            val k = ratio(fg, under)
            if (k < (if (sky) 3.0 else 4.5)) lowContrast += "$name: $id %.1f:1".format(k)
        }
    }

    private fun ratio(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(c shr 16 and 255) + 0.7152 * ch(c shr 8 and 255) + 0.0722 * ch(c and 255)
        }
        val l1 = lum(a); val l2 = lum(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @After fun restore() { RuntimeEnvironment.setFontScale(1f) }
}
