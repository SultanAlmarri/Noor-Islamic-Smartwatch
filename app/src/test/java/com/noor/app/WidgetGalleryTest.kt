package com.noor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.widget.Built
import com.noor.app.widget.WidgetAccent
import com.noor.app.widget.WidgetBackground
import com.noor.app.widget.WidgetInk
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

/**
 * Every widget (0.24) at every size class, in English, in Arabic with Western digits (the owner's set-up) and with
 * Arabic-Indic digits, by day, at the golden hour and at night, over the sky, dark and light, built as the home screen
 * gets it and drawn as Android draws it, into build/widgets (for review: software renders, not a launcher).
 *
 * The widgets' text is pictures drawn in Plex (a home screen ignores an app's fonts), so the checks read the model of
 * what was drawn ([com.noor.app.widget.Drawn]: each text's words, size and ink) and the views as laid out:
 * - nothing leaves the widget, every text's ink inside its own picture, no picture shown larger than drawn or
 *   stretched (it may only shrink), and no two texts (or a text and the countdown) touch;
 * - the readability floor (0.24, the owner: "everything is so small"): nothing under 11 dp, the marked prayer's time
 *   and the countdown 12 dp;
 * - the seconds wheel's window inside the widget, right beside the minutes, its wheel never drawn larger than made;
 * - the sky's moments differ: by day, at the golden hour, at night;
 * - only the app's digits; the same content in English and Arabic at the same size;
 * - every text against the pixels under it: 4.5:1 over plain backgrounds, 3:1 over the sky or the wallpaper;
 * - built for a reported size and shown at a shorter one (Samsung's 4×1), the layout made for it is shown, uncut.
 * One test method (DataStore allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGalleryTest {
    private val out = File("build/widgets").apply { mkdirs() }

    /** The sizes each kind is drawn at, in dp: the design's base sizes (2×1 176×84, 2×2 176×176, 4×1 360×84, 4×2 360×176, 4×4 360×372) and others a launcher may give. */
    private val sizes = mapOf(
        WidgetKind.NEXT to listOf(176 to 176, 360 to 176, 360 to 84, 176 to 84, 360 to 372, 176 to 372, 110 to 40, 250 to 84, 180 to 110, 300 to 130),
        WidgetKind.TODAY to listOf(176 to 176, 360 to 176, 360 to 372, 360 to 84, 176 to 84, 176 to 372, 300 to 130, 250 to 64),
        WidgetKind.STRIP to listOf(360 to 84, 300 to 64, 250 to 40),
        WidgetKind.RING to listOf(176 to 176, 110 to 110),
        WidgetKind.SKY to listOf(360 to 176, 176 to 176, 360 to 84),
        WidgetKind.DATE to listOf(176 to 176, 110 to 40, 360 to 84),
    )

    private val problems = ArrayList<String>()
    /** The middle of each widget's sky, to tell the moments apart. */
    private val skyAt = HashMap<String, Int>()
    private val lowContrast = ArrayList<String>()
    /** What each widget said (its texts' roles), by everything but the language: English and Arabic must agree. */
    private val said = HashMap<String, MutableMap<String, List<String>>>()

    @Test fun everyWidgetAtEverySize() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        repo.setOnboardingDone(); repo.setLocationMode(LocationMode.MANUAL); repo.setManualCity("dxb")
        // As the owner's phone names his place from its location: the area, and the city it is in (Dubai's own times).
        val dxb = com.noor.prayer.Places.byId("dxb")!!
        repo.setManualPlace(com.noor.app.data.KnownLocation(dxb.point, dxb.zoneId, "Madinat Al Mataar", dxb.countryCode, dxb.region, 0L,
            "مدينة المطار", "Dubai, United Arab Emirates", "دبي، الإمارات العربية المتحدة", precise = true))
        val dubai = ZoneId.of("Asia/Dubai")
        // Wednesday 4 November 2026 in Dubai: 14:10 (day, before Asr), 17:15 (the golden hour, before Maghrib), 21:00
        // (night, counting to tomorrow's Fajr); and 15:50, Asr's "+" period.
        fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, dubai).toInstant()
        val moments = listOf("day" to at(14, 10), "golden" to at(17, 15), "night" to at(21, 0))
        var count = 0
        val d = app.resources.displayMetrics.density

        suspend fun shoot(name: String, kind: WidgetKind, o: WidgetOptions, w: Int, h: Int, at: Instant, indic: Boolean, sayKey: String? = null) {
            var built: Built? = null
            val views = Widgets.previewBuilt(app, kind, o, w, h, at) { built = it }!!
            val pw = (w * d).toInt(); val ph = (h * d).toInt()
            val frame = lay(app, views, pw, ph)
            val b = built
            if (b != null) {
                b.design.back?.let { bk -> skyAt[name] = bk.getPixel(bk.width / 2, bk.height / 2) }
                problems += model(name, b, w, h, indic)
                sayKey?.let { said.getOrPut(it) { HashMap() }[name] = b.design.drawn.map { it.role }.sorted() }
                // What is under the text: the same widget drawn without it.
                WidgetInk.hideText = true
                val bare = try { lay(app, Widgets.preview(app, kind, o, w, h, at)!!, pw, ph) } finally { WidgetInk.hideText = false }
                bare.findViewById<View>(R.id.widget_count_row)?.visibility = View.INVISIBLE
                contrast(name, b, bare, pw, ph, o.background == WidgetBackground.SKY || o.background == WidgetBackground.CLEAR || kind == WidgetKind.SKY)
            }
            problems += views(name, frame, pw, ph)
            save(name, frame, pw, ph, d)
            count++
        }

        val dark = WidgetOptions(background = WidgetBackground.DARK, opacity = 100)
        val light = WidgetOptions(background = WidgetBackground.LIGHT, opacity = 100)
        for ((lang, digits) in listOf("en" to Numerals.WESTERN, "ar" to Numerals.WESTERN, "arind" to Numerals.ARABIC_INDIC)) {
            repo.setLanguage(if (lang == "en") Language.ENGLISH else Language.ARABIC); repo.setNumerals(digits)
            val indic = digits == Numerals.ARABIC_INDIC
            for ((kind, list) in sizes) for ((w, h) in list) {
                val tag = "${kind.name.lowercase()}_${w}x${h}"
                val sky = kind.defaults.copy(background = WidgetBackground.SKY)
                for ((mn, m) in moments) shoot("${tag}_${lang}_$mn", kind, sky, w, h, m, indic, "${tag}_$mn".takeIf { lang != "arind" })
                shoot("${tag}_${lang}_dark", kind, dark, w, h, moments[0].second, indic, "${tag}_dark".takeIf { lang != "arind" })
                shoot("${tag}_${lang}_light", kind, light, w, h, moments[0].second, indic, "${tag}_light".takeIf { lang != "arind" })
            }
        }
        repo.setLanguage(Language.ENGLISH); repo.setNumerals(Numerals.WESTERN)
        // A few options: teal, no background, large text, without seconds or countdown, after a prayer, 24 hours.
        for (kind in WidgetKind.entries) for ((w, h) in sizes[kind]!!.take(2)) {
            val tag = "${kind.name.lowercase()}_${w}x${h}"
            shoot("${tag}_en_teal", kind, WidgetOptions(background = WidgetBackground.DARK, opacity = 100, accent = WidgetAccent.TEAL), w, h, moments[0].second, false)
            shoot("${tag}_en_clear_large", kind, WidgetOptions(background = WidgetBackground.CLEAR, textSize = WidgetTextSize.LARGE), w, h, moments[0].second, false)
            shoot("${tag}_en_noseconds", kind, kind.defaults.copy(seconds = false), w, h, moments[0].second, false)
            shoot("${tag}_en_nocount", kind, kind.defaults.copy(countdown = false), w, h, moments[0].second, false)
            shoot("${tag}_en_after", kind, kind.defaults, w, h, at(15, 50), false)
        }
        repo.setTimeFormat(TimeFormat.H24)
        for (kind in WidgetKind.entries) for ((w, h) in sizes[kind]!!.take(2)) shoot("${kind.name.lowercase()}_${w}x${h}_en_24h", kind, kind.defaults, w, h, moments[0].second, false)
        repo.setTimeFormat(TimeFormat.H12)
        // Shown shorter than reported (Samsung's 4×1 is shorter than it says): the layout made for the real size, uncut.
        for (kind in WidgetKind.entries) for ((rep, real) in listOf((360 to 84) to (360 to 70), (176 to 176) to (176 to 150), (360 to 176) to (360 to 150))) {
            val views = Widgets.previewSized(app, kind, kind.defaults, listOf(rep), moments[0].second)!!
            val pw = (real.first * d).toInt(); val ph = (real.second * d).toInt()
            val frame = lay(app, views, pw, ph, SizeF(real.first.toFloat(), real.second.toFloat()))
            val name = "${kind.name.lowercase()}_${rep.first}x${rep.second}_shown_${real.first}x${real.second}"
            problems += views(name, frame, pw, ph)
            save(name, frame, pw, ph, d)
            count++
        }
        // The sky by day, at the golden hour and at night: three different skies.
        for ((kind, list) in sizes) for ((w, h) in list) {
            val tag = "${kind.name.lowercase()}_${w}x${h}_en"
            val day = skyAt["${tag}_day"]; val golden = skyAt["${tag}_golden"]; val night = skyAt["${tag}_night"]
            fun far(a: Int?, b: Int?) = a != null && b != null && (Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))) > 40
            if (!far(day, golden) || !far(golden, night) || !far(day, night)) problems += "$tag: the day, golden and night skies are not distinct"
        }
        // English and Arabic say the same things at the same size.
        for ((key, byName) in said) {
            val en = byName.entries.firstOrNull { "_en_" in it.key }?.value; val ar = byName.entries.firstOrNull { "_ar_" in it.key }?.value
            if (en != null && ar != null && en != ar) problems += "$key: English shows $en, Arabic $ar"
        }
        println("widgets drawn: $count; problems: ${problems.size}; low contrast: ${lowContrast.size}")
        problems.forEach { println("  $it") }
        lowContrast.forEach { println("  contrast $it") }
        assertTrue("every widget fits:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertTrue("every text reads:\n" + lowContrast.joinToString("\n"), lowContrast.isEmpty())
    }

    /** The views applied and laid out at [pw]×[ph] px (at [size], the layout a home screen picks for it from a map). */
    private fun lay(app: Context, views: RemoteViews, pw: Int, ph: Int, size: SizeF? = null): FrameLayout {
        val frame = FrameLayout(app)
        val v = if (size == null) views.apply(app, frame) else applyAt(views, app, frame, size)
        frame.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, pw, ph)
        return frame
    }

    /** As AppWidgetHostView applies a map of layouts: the one RemoteViews itself picks for [size] (a hidden method). */
    private fun applyAt(views: RemoteViews, app: Context, parent: ViewGroup, size: SizeF): View {
        val m = RemoteViews::class.java.declaredMethods.first { it.name == "getRemoteViewsToApply" && it.parameterTypes.size == 2 && it.parameterTypes[1] == SizeF::class.java }
        m.isAccessible = true
        return (m.invoke(views, app, size) as RemoteViews).apply(app, parent)
    }

    private fun save(name: String, frame: View, pw: Int, ph: Int, d: Float) {
        val m = (12 * d).toInt()
        val bmp = Bitmap.createBitmap(pw + 2 * m, ph + 2 * m, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), 0xFF2B3550.toInt(), 0xFFB08670.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        // The launcher's own corner, as on a phone (the widget draws none of its own).
        val r = 24f * d
        c.save(); c.translate(m.toFloat(), m.toFloat())
        c.clipPath(android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, pw.toFloat(), ph.toFloat()), r, r, android.graphics.Path.Direction.CW) })
        frame.draw(c); c.restore()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The model's checks: bounds, pictures, overlaps, floors, digits. */
    private fun model(name: String, b: Built, w: Int, h: Int, indic: Boolean): List<String> {
        val out = ArrayList<String>()
        val other = if (indic) Regex("[0-9]") else Regex("[٠-٩۰-۹]")
        val drawn = b.design.drawn
        for (t in drawn) {
            val i = t.ink
            if (i.left < -0.5f || i.top < -0.5f || i.right > w + 0.5f || i.bottom > h + 0.5f) out += "$name: ${t.role} \"${t.text}\" outside the widget ($i)"
            if (t.block >= 0) {
                val r = b.design.blocks[t.block].rect
                if (i.left < r.left - 0.5f || i.top < r.top - 0.5f || i.right > r.right + 0.5f || i.bottom > r.bottom + 0.5f) out += "$name: ${t.role} cut by its picture"
            }
            if (other.containsMatchIn(t.text)) out += "$name: ${t.role} has the other digits: \"${t.text}\""
            val floor = when {
                t.role == "countdown" -> 12f
                t.role.endsWith("time_marked") -> 12f
                else -> 11f
            }
            if (t.size < floor - 0.05f) out += "$name: ${t.role} under the floor (%.1f < %.0f dp): \"${t.text}\"".format(t.size, floor)
        }
        for (i in drawn.indices) for (j in i + 1 until drawn.size) {
            val a = RectF(drawn[i].ink).apply { inset(0.5f, 0.5f) }
            if (RectF.intersects(a, drawn[j].ink)) out += "$name: ${drawn[i].role} \"${drawn[i].text}\" overlaps ${drawn[j].role} \"${drawn[j].text}\""
        }
        return out
    }

    /** The views' checks: inside the widget, each picture shown no larger than drawn and not stretched. */
    private fun views(name: String, root: ViewGroup, w: Int, h: Int): List<String> {
        val out = ArrayList<String>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault("?")
            if (idName == "widget_count_wheel" || idName == "widget_bg") return
            if (v is ImageView && v.drawable != null && idName.startsWith("widget_")) {
                val dr = v.drawable
                val shown = RectF(0f, 0f, dr.intrinsicWidth.toFloat(), dr.intrinsicHeight.toFloat())
                v.imageMatrix.mapRect(shown)
                shown.offset(v.paddingLeft.toFloat(), v.paddingTop.toFloat())
                val sx = shown.width() / dr.intrinsicWidth; val sy = shown.height() / dr.intrinsicHeight
                if (sx > 1.01f || sy > 1.01f) out += "$name: $idName shown larger than drawn (%.2f)".format(maxOf(sx, sy))
                if (kotlin.math.abs(sx - sy) > 0.02f) out += "$name: $idName stretched"
                val r = Rect(shown.left.toInt(), shown.top.toInt(), shown.right.toInt(), shown.bottom.toInt())
                root.offsetDescendantRectToMyCoords(v, r)
                if (r.left < -1 || r.top < -1 || r.right > w + 1 || r.bottom > h + 1) out += "$name: $idName outside the widget ($r in ${w}x$h)"
            }
            if (idName == "widget_count_row") {
                val r = Rect(0, 0, v.width, v.height); root.offsetDescendantRectToMyCoords(v, r)
                if (r.left < -1 || r.top < -1 || r.right > w + 1 || r.bottom > h + 1) out += "$name: countdown outside the widget ($r in ${w}x$h)"
                if (v.layoutDirection != View.LAYOUT_DIRECTION_LTR) out += "$name: countdown not left to right"
                val main = v.findViewById<ImageView>(R.id.widget_count_main)
                if (main.drawable != null && main.drawable.intrinsicWidth > main.width + 1) out += "$name: countdown squeezed"
                // The seconds: their window inside the widget, right after the minutes, and as high as them.
                val win = v.findViewById<View>(R.id.widget_count_window)
                if (win.visibility == View.VISIBLE) {
                    val wr = Rect(0, 0, win.width, win.height); root.offsetDescendantRectToMyCoords(win, wr)
                    val mr = Rect(0, 0, main.width, main.height); root.offsetDescendantRectToMyCoords(main, mr)
                    if (win.width <= 0 || win.height <= 0) out += "$name: no window for the seconds"
                    if (wr.left < -1 || wr.top < -1 || wr.right > w + 1 || wr.bottom > h + 1) out += "$name: the seconds outside the widget ($wr in ${w}x$h)"
                    if (Math.abs(wr.left - mr.right) > 1) out += "$name: the seconds not beside the minutes ($wr, $mr)"
                    // The seconds stand on the minutes' baseline (within 1 dp): each picture's baseline is at the same
                    // share of its height (the countdown's box, res/values/noor_wheel.xml).
                    val share = v.resources.getDimension(R.dimen.noor_count_ascent) / v.resources.getDimension(R.dimen.noor_count_height)
                    val dp = v.resources.displayMetrics.density
                    val bm = mr.top + share * main.height; val bs = wr.top + share * win.height
                    if (Math.abs(bm - bs) > dp) out += "$name: the seconds off the minutes' baseline by %.1f dp".format(Math.abs(bm - bs) / dp)
                    val wheel = v.findViewById<View>(R.id.widget_count_wheel)
                    val made = v.resources.getDimension(R.dimen.noor_count_wheel)
                    if (wheel.width > made + 1) out += "$name: the seconds wheel drawn larger than made (${wheel.width} > $made)"
                }
            }
        }
        walk(root)
        return out
    }

    /** Texts whose colour is below 4.5:1 (or 3:1 over the sky or the wallpaper) against the pixels drawn under them. */
    private fun contrast(name: String, b: Built, bareRoot: ViewGroup, w: Int, h: Int, sky: Boolean) {
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Over the gallery's wallpaper, as drawn.
        Canvas(bg).apply { drawColor(0xFF7A6A72.toInt()); bareRoot.draw(this) }
        val d = bareRoot.resources.displayMetrics.density
        for (t in b.design.drawn) {
            val rr = Rect((t.ink.left * d).toInt(), (t.ink.top * d).toInt(), (t.ink.right * d).toInt(), (t.ink.bottom * d).toInt())
            if (!rr.intersect(0, 0, w, h)) continue
            var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
            for (y in rr.top until rr.bottom step 2) for (x in rr.left until rr.right step 2) {
                val p = bg.getPixel(x, y); rs += (p shr 16 and 255); gs += (p shr 8 and 255); bs += (p and 255); n++
            }
            if (n == 0) continue
            val under = android.graphics.Color.rgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
            val color = t.color
            val a = (color ushr 24) / 255f
            fun mix(c: Int, u: Int) = (c * a + u * (1 - a)).toInt()
            val fg = android.graphics.Color.rgb(mix(color shr 16 and 255, under shr 16 and 255), mix(color shr 8 and 255, under shr 8 and 255), mix(color and 255, under and 255))
            val k = ratio(fg, under)
            if (k < (if (sky) 3.0 else 4.5)) lowContrast += "$name: ${t.role} \"${t.text}\" %.1f:1".format(k)
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

    @After fun restore() { RuntimeEnvironment.setFontScale(1f); WidgetInk.hideText = false }
}
