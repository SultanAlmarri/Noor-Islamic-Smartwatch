package com.noor.app.widget

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.noor.app.data.AppSettings
import com.noor.app.data.ResolvedPlace
import com.noor.app.ui.today.Atmosphere
import com.noor.app.ui.today.CloudAtlas
import com.noor.app.ui.today.CloudField
import com.noor.app.ui.today.CloudPainter
import com.noor.app.ui.today.LandProfile
import com.noor.app.ui.today.SceneLayout
import com.noor.app.ui.today.SkyLight
import com.noor.app.ui.today.SkyLook
import com.noor.app.ui.today.SkyScene
import com.noor.app.ui.today.SkyView
import com.noor.app.ui.today.Terrain
import com.noor.app.ui.today.Weather
import com.noor.app.ui.today.terrainFor
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.prayer.astro.SunPath
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** The home sky's scene for the user's place at [now], as the home screen builds it (same Sun, weather, scenery). */
internal class WidgetScene(val settings: AppSettings, val place: ResolvedPlace, val config: WatchConfig, val now: Instant) {
    val zone = place.zone
    val today = now.atZone(zone).toLocalDate()!!
    val day = config.day(today)
    val state = config.state(now)
    val scene: SkyScene = run {
        val events = Prayer.entries.mapNotNull { p -> day.instant(p)?.let { p to it } }
        val landSeed = place.nameEn.hashCode().toLong() * 7919L + (place.point.latitude * 100).toLong()
        SkyScene(
            SunPath.of(today, place.point, zone), events, now.truncatedTo(ChronoUnit.MINUTES), state.next.prayer.takeIf { state.next.date == today },
            hijriDay = HijriDate.of(today, settings.hijriAdjust).day, southernHemisphere = place.point.latitude < 0,
            terrain = terrainFor(settings.scenery, place.countryCode, place.point.elevationM), seed = landSeed * 1_000_003L + today.toEpochDay(),
            landSeed = landSeed, latitude = place.point.latitude, longitude = place.point.longitude,
        )
    }
    val weather: Weather = Weather.of(scene.seed, scene.terrain)
}

/**
 * The picture behind the widgets (0.9.10): the home sky as it is now — the atmosphere's colours for this moment and
 * place, the day's weather with the photographed clouds (lit as on the home screen, never caught mid-flash), the stars
 * and the moon at night, the user's scenery, and (on the large widget) the Sun's path with the Sun and the prayers.
 * Drawn with the ordinary Canvas, off the main thread, a few times an hour; rounded at the corners.
 */
internal object WidgetSky {
    class Options(val land: Boolean = true, val path: Boolean = false, val horizon: Float = 0.74f, val arcTop: Float = 0.30f)

    fun picture(res: Resources, ws: WidgetScene, w: Int, h: Int, density: Float, rtl: Boolean, o: Options): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(8), h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = bmp.width.toFloat(); val hf = bmp.height.toFloat()
        val r = 22f * density
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, wf, hf), r, r, Path.Direction.CW) })
        val layout = SceneLayout(cloudTop = 0f, arcTop = o.arcTop, horizon = if (o.land) o.horizon else 0.97f, ground = 1f)
        val view = SkyView(ws.scene, wf, hf, density, layout, rtl)
        val fraction = ws.scene.nowFraction
        val look = SkyLook.at(ws.scene, view, fraction)
        val light = SkyLight.compute(SkyLight.key(look, view, 90.0))
        val state = ws.weather.at(fraction)
        val hy = view.horizonY

        // The sky: the atmosphere's table laid out over the picture.
        val cols = Atmosphere.NX; val rows = 48
        val px = IntArray(cols * rows)
        for (y in 0 until rows) {
            val v = Atmosphere.rowOf(view.altitudeAt((y + 0.5f) / rows * hf))
            for (x in 0 until cols) px[y * cols + x] = Atmosphere.sample(light.lut, x / (cols - 1.0), v)
        }
        c.drawBitmap(Bitmap.createBitmap(px, cols, rows, Bitmap.Config.ARGB_8888), null, RectF(0f, 0f, wf, hf), Paint(Paint.FILTER_BITMAP_FLAG))

        // Stars and the moon at night.
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (look.night > 0f) {
            val rnd = java.util.Random(ws.scene.seed)
            for (i in 0 until (w * h / (180 * density * density)).toInt().coerceAtMost(220)) {
                val x = rnd.nextFloat() * wf; val y = rnd.nextFloat() * hy
                val b = rnd.nextFloat().let { it * it }
                p.color = android.graphics.Color.argb((255 * look.night * (0.25f + 0.75f * b) * (y / hy).let { 1f - it * it * 0.8f }).toInt().coerceIn(0, 255), 255, 255, 255)
                c.drawCircle(x, y, (0.35f + 0.55f * b) * density, p)
            }
        }
        if (look.moonVisibility > 0f && look.moonY < hy) {
            val mr = 6f * density
            p.shader = RadialGradient(look.moonX, look.moonY, mr * 5, intArrayOf(argb(0.10f * look.moonVisibility * look.night, 0.85f, 0.9f, 1f), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(look.moonX, look.moonY, mr * 5, p); p.shader = null
            p.color = argb(0.92f * look.moonVisibility, 0.97f, 0.96f, 0.9f)
            c.drawCircle(look.moonX, look.moonY, mr, p)
        }
        // The Sun and its glare, behind the clouds.
        if (look.elevation > -1.5 && look.sunY < hy + 2 * density) {
            val disc = DoubleArray(3); val glow = DoubleArray(3)
            Atmosphere.tone(light.light.sunDisc.copyOf().also { for (k in 0..2) it[k] *= 0.004 }, disc)
            Atmosphere.tone(light.light.sunGlow.copyOf(), glow)
            val gr = 40f * density
            p.shader = RadialGradient(look.sunX, look.sunY, gr, intArrayOf(argb(0.55f, glow), argb(0.12f, glow), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(look.sunX, look.sunY, gr, p); p.shader = null
            p.color = argb(1f, maxOf(disc[0], 0.9), maxOf(disc[1], 0.8), maxOf(disc[2], 0.6))
            c.save(); c.clipRect(0f, 0f, wf, hy)
            c.drawCircle(look.sunX, look.sunY, 6.5f * density, p)
            c.restore()
        }
        // An overcast greys the whole sky.
        if (state.overcast > 0.02f) {
            val g = (0.62 * (1 - 0.75 * look.night)).toFloat()
            p.color = argb(0.85f * state.overcast, g, g * 1.01f, g * 1.04f)
            c.drawRect(0f, 0f, wf, hy, p)
        }
        // The clouds of the moment.
        CloudAtlas.preload(res)
        CloudAtlas.get(res)?.let { atlas ->
            val f = CloudField.Frame(wf, hf, hy, density, view.fov, 40f, ws.weather, state, look.sunAzimuth, look.elevation, light.light, light.lut,
                look.night, look.moonVisibility * look.moonIllumination * look.night)
            CloudPainter().draw(c, atlas, f, -1e6f, -1e6f + 1f, lightning = false)
        }
        if (o.land) drawLand(c, ws, view, light, look, wf, hf, density)
        if (o.path) drawPath(c, ws, view, look, wf, density)
        // Rain falling here.
        if (state.rain > 0.02f) {
            val rnd = java.util.Random(77)
            p.strokeWidth = 0.8f * density
            for (i in 0 until (140 * state.rain).toInt()) {
                val x = rnd.nextFloat() * wf; val y = rnd.nextFloat() * hf; val len = (8 + 10 * rnd.nextFloat()) * density
                p.color = argb((0.08f + 0.12f * rnd.nextFloat()) * state.rain, 0.9f, 0.92f, 0.95f)
                c.drawLine(x, y, x + len * 0.18f, y + len, p)
            }
        }
        return bmp
    }

    private fun drawLand(c: Canvas, ws: WidgetScene, view: SkyView, light: SkyLight, look: SkyLook, w: Float, h: Float, density: Float) {
        val terrain = ws.scene.terrain
        val land = LandProfile.of(ws.scene.landSeed, terrain)
        val g = land.geometry(view.horizonY, h, density)
        val l = light.light
        val sunUp = max(sin(Math.toRadians(look.elevation)), 0.0)
        // The air between us and the far land: the sky a little above the horizon, cooled towards grey-blue (the haze
        // over land is bluer than the glow along the horizon itself).
        val horizonArgb = Atmosphere.sample(light.lut, 0.5, Atmosphere.rowOf(4.0))
        val air = doubleArrayOf(((horizonArgb shr 16) and 0xFF) / 255.0, ((horizonArgb shr 8) and 0xFF) / 255.0, (horizonArgb and 0xFF) / 255.0)
        val grey = 0.2126 * air[0] + 0.7152 * air[1] + 0.0722 * air[2]
        val cool = doubleArrayOf(grey * 0.86, grey * 0.95, grey * 1.08)
        for (k in 0..2) air[k] = air[k] * 0.55 + cool[k] * 0.45
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val lin = DoubleArray(3); val out = DoubleArray(3)
        fun shade(albedo: DoubleArray, dist: Float, face: Double): Int {
            for (k in 0..2) lin[k] = albedo[k] * (l.sunGround[k] * (0.35 + 0.8 * sunUp) * face + l.ambTop[k] * 0.9 + l.ambLow[k] * 0.35)
            Atmosphere.tone(lin, out)
            val t = exp(-0.035 * dist)
            return argb(1f, air[0] + (out[0] - air[0]) * t, air[1] + (out[1] - air[1]) * t, air[2] + (out[2] - air[2]) * t)
        }
        for (layer in 0 until LandProfile.LAYERS) {
            val water = terrain == Terrain.SEA && layer in 1..2
            if (water) {
                // The sea: the sky mirrored, darker towards the shore, the Sun's path glittering across it.
                val top = view.horizonY
                val bottom = g.base[3]
                p.shader = LinearGradient(0f, top, 0f, bottom, intArrayOf(argb(1f, air[0] * 0.92, air[1] * 0.95, air[2]), argb(1f, air[0] * 0.55, air[1] * 0.68, air[2] * 0.8)), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, top, w, bottom, p); p.shader = null
                if (look.elevation > -1 && layer == 1) {
                    val gw = (14 + 24 * (1 - sunUp)).toFloat() * density
                    p.shader = RadialGradient(look.sunX, top + (bottom - top) * 0.35f, gw * 2.2f, intArrayOf(argb(0.55f, 1.0, 0.96, 0.85), 0), null, Shader.TileMode.CLAMP)
                    c.save(); c.scale(1f, 0.35f, look.sunX, top + (bottom - top) * 0.35f)
                    c.drawCircle(look.sunX, top + (bottom - top) * 0.35f, gw * 2.2f, p); c.restore(); p.shader = null
                }
                continue
            }
            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            val step = 2f * density
            while (x <= w + step) {
                val hh = if (terrain == Terrain.SEA && layer == 0) land.height(0, (x / w).coerceIn(0f, 1f)).let { if (it < 0.02f) 0f else it } else land.height(layer, (x / w).coerceIn(0f, 1f))
                path.lineTo(x, g.base[layer] - g.amp[layer] * hh)
                x += step
            }
            path.lineTo(w, h); path.close()
            val albedo = when (terrain) {
                Terrain.DUNES -> doubleArrayOf(0.55, 0.36, 0.19)
                Terrain.SEA -> if (layer == 0) doubleArrayOf(0.20, 0.24, 0.18) else doubleArrayOf(0.52, 0.46, 0.36)
                Terrain.MOUNTAINS -> if (layer <= 1) doubleArrayOf(0.20, 0.20, 0.22) else doubleArrayOf(0.11, 0.15, 0.07)
                else -> if (layer == 0) doubleArrayOf(0.14, 0.17, 0.13) else doubleArrayOf(0.11, 0.16 - 0.012 * layer, 0.06)
            }
            val top = g.base[layer] - g.amp[layer]
            // Each layer lit on its upper slopes, darker lower down.
            p.shader = LinearGradient(0f, top, 0f, g.base[layer] + 6 * density,
                shade(albedo, g.distKm[layer], 1.0), shade(albedo, g.distKm[layer], 0.55), Shader.TileMode.CLAMP)
            c.drawPath(path, p); p.shader = null
        }
    }

    private fun drawPath(c: Canvas, ws: WidgetScene, view: SkyView, look: SkyLook, w: Float, density: Float) {
        val geo = view.geometry
        val scene = ws.scene
        val hy = view.horizonY
        val path = Path(); var first = true
        for (i in 0..240) {
            val fr = i / 240.0
            val x = geo.x(fr); val y = geo.y(scene.elevationAt(fr))
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        c.save(); c.clipRect(0f, 0f, w, hy)
        p.color = argb(0.55f, 1.0, 1.0, 1.0); p.strokeWidth = 1.1f * density; p.pathEffect = DashPathEffect(floatArrayOf(2 * density, 4.5f * density), 0f)
        c.drawPath(path, p); p.pathEffect = null
        // The day so far, in gold.
        val sunX = geo.x(scene.nowFraction)
        c.save()
        if (view.rtl) c.clipRect(sunX, 0f, w, hy) else c.clipRect(0f, 0f, sunX, hy)
        p.color = argb(0.28f, 1.0, 0.84, 0.55); p.strokeWidth = 4.5f * density; c.drawPath(path, p)
        p.color = argb(0.95f, 1.0, 0.93, 0.76); p.strokeWidth = 1.4f * density; c.drawPath(path, p)
        c.restore(); c.restore()
        // The prayers on the path (on the horizon while the Sun is below it).
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((prayer, at) in scene.events) {
            val fr = scene.path.fraction(at)
            val x = geo.x(fr); val y = geo.y(scene.elevationAt(fr)).coerceAtMost(hy)
            val passed = !at.isAfter(ws.now)
            if (prayer == scene.next) {
                fill.color = argb(0.25f, 0.96, 0.84, 0.59); c.drawCircle(x, y, 7.5f * density, fill)
                p.color = argb(1f, 0.96, 0.84, 0.59); p.strokeWidth = 1.5f * density; c.drawCircle(x, y, 4f * density, p)
            } else if (passed) {
                fill.color = argb(0.95f, 1.0, 1.0, 1.0); c.drawCircle(x, y, 2.3f * density, fill)
            } else {
                p.color = argb(0.8f, 1.0, 1.0, 1.0); p.strokeWidth = 1f * density; c.drawCircle(x, y, 2.6f * density, p)
            }
        }
    }

    private fun argb(a: Float, r: Double, g: Double, b: Double): Int {
        fun ch(x: Double) = (x.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        return ((a.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
    private fun argb(a: Float, r: Float, g: Float, b: Float) = argb(a, r.toDouble(), g.toDouble(), b.toDouble())
    private fun argb(a: Float, c: DoubleArray) = argb(a, c[0], c[1], c[2])
}
