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
import com.noor.app.ui.today.LandProfile
import com.noor.app.ui.today.SceneLayout
import com.noor.app.ui.today.SkyLight
import com.noor.app.ui.today.SkyPicture
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
 * The picture behind the widgets: the home sky of the moment (0.9.10). Since 0.9.12 drawn by the home screen's own
 * code ([SkyPicture]: its GPU sky and land, clouds, the Sun's path and the prayers at the Sun's real position at each,
 * Fajr and Isha below the horizon); where the GPU cannot draw it (before Android 13), by the Canvas painter below, which
 * draws the path and the prayers with the home screen's own code too. Rounded at the corners; off the main thread.
 */
internal object WidgetSky {
    /**
     * Heights as fractions of the picture: [horizon], the noon Sun's [arcTop], the deepest night point [ground], the
     * clouds' [cloudTop]; [path]: the Sun's path and the prayers; [textBottomDp]: clouds fade out above it.
     */
    class Options(val horizon: Float, val arcTop: Float = 0.30f, val cloudTop: Float = 0f, val ground: Float = 0.985f, val path: Boolean = false, val textBottomDp: Float? = null)

    fun picture(res: Resources, ws: WidgetScene, w: Int, h: Int, density: Float, rtl: Boolean, o: Options): Bitmap {
        val layout = SceneLayout(cloudTop = o.cloudTop, arcTop = o.arcTop, horizon = o.horizon, ground = o.ground)
        val spec = SkyPicture.Spec(w.coerceAtLeast(8), h.coerceAtLeast(8), density, layout, rtl, o.path, o.textBottomDp?.let { it * density }, corner = 22f * density)
        SkyPicture.gpu(res, ws.scene, spec)?.let { android.util.Log.i("NoorWidgets", "Widget sky drawn on the GPU (${spec.w}x${spec.h})"); return it }
        android.util.Log.i("NoorWidgets", "Widget sky drawn with Canvas (${spec.w}x${spec.h})")
        return canvasPicture(res, ws, spec, o)
    }

    private fun canvasPicture(res: Resources, ws: WidgetScene, spec: SkyPicture.Spec, o: Options): Bitmap {
        val w = spec.w; val h = spec.h; val density = spec.density
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = bmp.width.toFloat(); val hf = bmp.height.toFloat()
        SkyPicture.round(c, spec)
        val inputs = SkyPicture.inputs(ws.scene, spec)
        val view = inputs.view
        val look = inputs.look
        val light = inputs.light
        val state = inputs.state
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
        // The Sun: drawn with the path, by the path layer, as the home screen draws it (without the path, a widget has
        // no room under its words for the Sun's arc: SkyPicture.Spec.sun).
        // An overcast greys the whole sky.
        if (state.overcast > 0.02f) {
            val g = (0.62 * (1 - 0.75 * look.night)).toFloat()
            p.color = argb(0.85f * state.overcast, g, g * 1.01f, g * 1.04f)
            c.drawRect(0f, 0f, wf, hy, p)
        }
        // The clouds of the moment, the path, the land, as on the home screen.
        SkyPicture.clouds(res, c, inputs, spec, SkyPicture.time(ws.scene))
        if (o.path) SkyPicture.path(c, inputs, spec, gpu = false)
        drawLand(c, ws, view, light, look, wf, hf, density)
        SkyPicture.rainOver(c, inputs, spec)
        if (o.path) SkyPicture.marks(c, inputs, spec)
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

    private fun argb(a: Float, r: Double, g: Double, b: Double): Int {
        fun ch(x: Double) = (x.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        return ((a.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
    private fun argb(a: Float, r: Float, g: Float, b: Float) = argb(a, r.toDouble(), g.toDouble(), b.toDouble())
    private fun argb(a: Float, c: DoubleArray) = argb(a, c[0], c[1], c[2])
}
