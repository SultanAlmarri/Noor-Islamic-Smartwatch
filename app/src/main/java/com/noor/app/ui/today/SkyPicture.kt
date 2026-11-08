package com.noor.app.ui.today

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The home sky as one still picture (0.9.12), for the home-screen widgets, drawn by the home screen's own code and in
 * its order: the GPU sky (the atmosphere of this moment and place, the Sun, the moon, the real stars), the day's
 * photographed clouds, the Sun's path with the day so far in gold, the GPU land (the user's scenery), rain, and the
 * prayers on the path at the Sun's real position at each (below the horizon, in the land, at Fajr and Isha). 0.9.10's
 * widgets had a simplified painter of their own, which looked different and put Fajr and Isha on the horizon.
 *
 * Drawn off screen by Android's GPU renderer (Android 13 and later, as the home sky); null where that is not possible,
 * and the widget draws its Canvas picture instead.
 */
internal object SkyPicture {
    class Spec(
        val w: Int, val h: Int,
        /** px per dp of the picture. */
        val density: Float,
        val layout: SceneLayout,
        val rtl: Boolean,
        /** The Sun's path and the prayers on it. */
        val path: Boolean,
        /** Clouds fade out above this line (px), as under the home screen's text; null: they stay everywhere. */
        val textBottom: Float?,
        /** The page the nearest land fades into: Noor's night page. */
        val page: Color = Color(0xFF080C14),
        /** Corner radius (px) the picture is rounded to. */
        val corner: Float = 0f,
        /** The moment shown, as a fraction of the day (now unless given). */
        val display: Double? = null,
        /**
         * The Sun's disc. A widget without the path has no room below its words for the Sun's arc (the home screen
         * keeps the arc under them), so there the disc is left out; its light still colours the sky and the land.
         */
        val sun: Boolean = path,
    )

    /** What the layers need, as the home screen builds it for a moment. */
    fun inputs(scene: SkyScene, s: Spec): GpuSkyInputs {
        val view = SkyView(scene, s.w.toFloat(), s.h.toFloat(), s.density, s.layout, s.rtl)
        val display = s.display ?: scene.nowFraction
        val look = SkyLook.at(scene, view, display).let { l ->
            if (s.sun) l else SkyLook(l.elevation, l.sunX, -1e5f, l.sunAzimuth, l.drawAltitude, l.night, l.milkyWay, l.mist, l.moonX, l.moonY,
                l.moonVisibility, l.moonLight, l.moonIllumination)
        }
        val capAlt = view.altitudeAt(view.cloudTopY - 8 * s.density)
        val light = SkyLight.compute(SkyLight.key(look, view, capAlt))
        val weather = Weather.of(scene.seed, scene.terrain)
        return GpuSkyInputs(scene, view, look, light, weather, LandProfile.of(scene.landSeed, scene.terrain), s.page,
            view.cloudTopY - 8 * s.density, weather.at(display), scene.instantAt(display))
    }

    /** The ambient clock of the picture: where the clouds have drifted to (the same for the same day). */
    fun time(scene: SkyScene): Float = (((scene.seed % 997) + 997) % 997).toFloat() * 3.7f + 0.13f * 240f

    fun gpu(res: Resources, scene: SkyScene, s: Spec): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || s.w < 8 || s.h < 8) return null
        return runCatching { GpuPicture.draw(res, scene, s) }.onFailure { Log.w("NoorSky", "Widget sky on the GPU failed; Canvas instead", it) }.getOrNull()
    }

    /** The clouds of the moment over [canvas], as the home screen's cloud layer draws them (never mid-flash). */
    fun clouds(res: Resources, canvas: android.graphics.Canvas, i: GpuSkyInputs, s: Spec, time: Float) {
        CloudAtlas.preload(res)
        val atlas = CloudAtlas.get(res) ?: return
        val v = i.view; val look = i.look
        val f = CloudField.Frame(s.w.toFloat(), s.h.toFloat(), v.horizonY, s.density, v.fov, time, i.weather, i.state,
            look.sunAzimuth, look.elevation, i.light.light, i.light.lut, look.night, look.moonVisibility * look.moonIllumination * look.night)
        val tb = s.textBottom
        if (tb != null) CloudPainter().draw(canvas, atlas, f, tb - 6f * s.density, tb + 34f * s.density, lightning = false)
        else CloudPainter().draw(canvas, atlas, f, -1e6f, -1e6f + 1f, lightning = false)
    }

    /** The home screen's path layer (3) and prayers layer (5), and rain, through Compose's drawing on [canvas]. */
    private fun overlay(canvas: android.graphics.Canvas, i: GpuSkyInputs, s: Spec, block: DrawScope.(SkyGeometry, Light) -> Unit) {
        val scene = i.scene
        val display = s.display ?: scene.nowFraction
        val light = lightAt(scene.elevationAt(display), scene.eveningAt(display))
        CanvasDrawScope().draw(Density(s.density), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(s.w.toFloat(), s.h.toFloat())) {
            block(i.view.geometry, light)
        }
    }

    /** The Sun's path; with [gpu] false (the Canvas sky) also the Sun, its halo and rays, as the home screen draws them. */
    fun path(canvas: android.graphics.Canvas, i: GpuSkyInputs, s: Spec, gpu: Boolean) = overlay(canvas, i, s) { g, light ->
        sunPathLayer(i.scene, g, sunPath(i.scene, g), s.display ?: i.scene.nowFraction, light, gpu, pathAlpha = 1f, rayTurn = { 0.4f }, rtl = s.rtl)
    }

    fun marks(canvas: android.graphics.Canvas, i: GpuSkyInputs, s: Spec) = overlay(canvas, i, s) { g, light ->
        prayerMarksLayer(i.scene, g, prayerMarks(i.scene, g), s.display ?: i.scene.nowFraction, 1f, light.zenith) { 0.5f }
    }

    fun rainOver(canvas: android.graphics.Canvas, i: GpuSkyInputs, s: Spec) = overlay(canvas, i, s) { _, _ -> rain(i, 7.3f) }

    fun round(canvas: android.graphics.Canvas, s: Spec) {
        if (s.corner > 0f) canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, s.w.toFloat(), s.h.toFloat()), s.corner, s.corner, Path.Direction.CW) })
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GpuPicture {
    fun draw(res: Resources, scene: SkyScene, s: SkyPicture.Spec): Bitmap? {
        val shaders = SkyShaders.create(res) ?: return null
        val i = SkyPicture.inputs(scene, s)
        val t = SkyPicture.time(scene)
        val w = s.w; val h = s.h
        val lut = BitmapShader(Bitmap.createBitmap(i.light.lut, Atmosphere.NX, Atmosphere.NY, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
        val starBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (i.look.night > 0f) NightSky.drawStars(starBmp, NightSky.stars(res), i.shownAt, scene.latitude, scene.longitude, i.view.horizonY,
            i.view.fov, SkyView.ALT_TOP, SkyView.ALT_POW, s.density, s.rtl)
        val stars = BitmapShader(starBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val ridges = BitmapShader(Bitmap.createBitmap(i.land.table(), LandProfile.N, 2, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }

        val node = RenderNode("NoorSkyPicture").apply { setPosition(0, 0, w, h) }
        val c = node.beginRecording(w, h)
        try {
            SkyPicture.round(c, s)
            shaders.drawSky(c, w.toFloat(), h.toFloat(), i, t, lut, stars, Paint())
            SkyPicture.clouds(res, c, i, s, t)
            if (s.path) SkyPicture.path(c, i, s, gpu = true)
            shaders.drawLand(c, w.toFloat(), h.toFloat(), i, t, lut, ridges, Paint())
            SkyPicture.rainOver(c, i, s)
            if (s.path) SkyPicture.marks(c, i, s)
        } finally {
            node.endRecording()
        }
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.isOpaque = false
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            var image: android.media.Image? = null
            for (k in 0 until 50) { image = reader.acquireNextImage(); if (image != null) break; Thread.sleep(10) }
            return image?.use { img ->
                img.hardwareBuffer?.use { hb -> Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))?.copy(Bitmap.Config.ARGB_8888, false) }
            }
        } finally {
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
            starBmp.recycle()
        }
    }
}
