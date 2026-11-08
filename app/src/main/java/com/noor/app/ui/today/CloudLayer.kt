package com.noor.app.ui.today

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.noor.app.R

/**
 * The clouds (0.9.9, tools/sky/photo_clouds.py): one image holding every cloud, cut out of its photograph, at several
 * sizes (grey = its light and shade, alpha = its opacity), and the table of where each is. Decoded once, off the main
 * thread at start ([preload]); until then (or if they cannot be read) the sky simply has no clouds.
 */
internal object CloudAtlas {
    class Loaded(val image: Bitmap, val table: CloudTable)

    /** Changes when the clouds are ready, so that a sky drawn before then redraws. */
    val ready = mutableIntStateOf(0)
    @Volatile private var loaded: Loaded? = null
    @Volatile private var failed = false

    @Synchronized fun preload(res: Resources) {
        if (loaded != null || failed) return
        runCatching {
            val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            fun one(id: Int) = res.openRawResource(id).use { BitmapFactory.decodeStream(it, null, o) }!!
            val table = res.openRawResource(R.raw.cloud_atlas).bufferedReader().use { CloudTable.parse(it.readText()) }
            loaded = Loaded(one(R.raw.clouds), table)
            android.os.Handler(android.os.Looper.getMainLooper()).post { ready.intValue++ }
        }.onFailure { Log.w("NoorSky", "Clouds unavailable", it); failed = true }
    }

    private val asked = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The clouds, if decoded. Never decodes on the caller's thread: the first call starts it in the background. */
    fun get(res: Resources): Loaded? = loaded ?: run {
        if (asked.compareAndSet(false, true)) Thread({ preload(res) }, "NoorClouds").apply { isDaemon = true }.start()
        null
    }
}

/**
 * Draws the clouds of [CloudField] onto a canvas: each its picture through its colour matrix. Where a cloud would rise
 * behind the text written on the sky it fades out, so the text stays readable (0.9.8 instead dimmed such clouds to
 * grey, which looked like grey boxes).
 */
internal class CloudPainter {
    private val clouds = ArrayList<CloudDraw>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val m = Matrix()

    /**
     * Lays out and draws the clouds of [f]. Where a cloud crosses [fadeTop]…[fadeBottom] (px) it fades from nothing at
     * [fadeTop] to full at [fadeBottom].
     */
    fun draw(canvas: Canvas, atlas: CloudAtlas.Loaded, f: CloudField.Frame, fadeTop: Float, fadeBottom: Float) {
        val n = CloudField.layout(atlas.table, f, clouds)
        for (i in 0 until n) {
            val c = clouds[i]
            src.set(c.src[0], c.src[1], c.src[0] + c.src[2], c.src[1] + c.src[3])
            dst.set(c.left, c.top, c.right, c.bottom)
            paint.colorFilter = ColorMatrixColorFilter(c.matrix)
            if (c.top < fadeBottom) {
                if (c.bottom <= fadeTop) continue
                // Mapped by a shader, so the fade can go with it.
                m.setRectToRect(RectF(src), dst, Matrix.ScaleToFit.FILL)
                if (c.flip) m.postScale(-1f, 1f, (dst.left + dst.right) / 2, 0f)
                val bs = BitmapShader(atlas.image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
                val mask = LinearGradient(0f, fadeTop, 0f, fadeBottom, 0x00000000, -0x1000000, Shader.TileMode.CLAMP)
                paint.shader = ComposeShader(bs, mask, PorterDuff.Mode.DST_IN)
                canvas.drawRect(dst, paint)
                paint.shader = null
            } else {
                if (c.flip) { canvas.save(); canvas.scale(-1f, 1f, (dst.left + dst.right) / 2, 0f) }
                canvas.drawBitmap(atlas.image, src, dst, paint)
                if (c.flip) canvas.restore()
            }
        }
    }
}

/**
 * The clouds of the home sky (0.9.9), over the sky and under the land, on every phone (GPU or Canvas sky). Drawn at
 * full resolution straight onto the sky (no layer of their own); redrawn when [time] ticks.
 */
@Composable
internal fun CloudLayer(inputs: () -> GpuSkyInputs?, time: () -> Float, parallax: () -> Float) {
    val res = LocalContext.current.resources
    val painter = remember { CloudPainter() }
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = parallax() * 0.5f }.drawBehind {
        CloudAtlas.ready.intValue
        val atlas = CloudAtlas.get(res) ?: return@drawBehind
        val i = inputs() ?: return@drawBehind
        val v = i.view; val look = i.look
        val s = size.width / v.w
        val f = CloudField.Frame(
            size.width, size.height, v.horizonY * s, v.density * s, v.fov, time(), i.weather,
            look.sunAzimuth, look.elevation, i.light.light, i.light.lut, look.night,
            look.moonVisibility * look.moonIllumination * look.night,
        )
        val textBottom = i.textBottom * s
        drawIntoCanvas { painter.draw(it.nativeCanvas, atlas, f, textBottom - 6f * f.dp, textBottom + 34f * f.dp) }
    })
}
