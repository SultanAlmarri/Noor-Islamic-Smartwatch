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
 * Draws the clouds of [CloudField] onto a canvas: each its picture through its colour matrix, the shafts of rain under
 * them, and lightning when it flashes. Where a cloud would rise behind the text written on the sky it fades out, so the
 * text stays readable (0.9.8 instead dimmed such clouds to grey, which looked like grey boxes).
 */
internal class CloudPainter {
    private val clouds = ArrayList<CloudDraw>()
    private val bolt = Bolt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val src = Rect()
    private val dst = RectF()
    private val m = Matrix()
    private val path = android.graphics.Path()

    /** Streaks of falling rain, thinning towards the shaft's sides; tiled downwards. */
    private val streaks: Bitmap by lazy {
        val w = 48; val h = 256
        val px = IntArray(w * h)
        val r = java.util.Random(11)
        for (x in 0 until w) {
            val side = kotlin.math.sin(Math.PI * (x + 0.5) / w).toFloat().let { it * it }
            val base = 0.45f + 0.55f * r.nextFloat()
            var y = r.nextInt(40)
            while (y < h) {
                val len = 24 + r.nextInt(90); val a = base * (0.55f + 0.45f * r.nextFloat()) * side
                for (yy in y until minOf(h, y + len)) px[yy * w + x] = ((a * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
                y += len + r.nextInt(18)
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Lays out and draws the clouds of [f]. Where a cloud crosses [fadeTop]…[fadeBottom] (px) it fades from nothing at
     * [fadeTop] to full at [fadeBottom].
     */
    fun draw(canvas: Canvas, atlas: CloudAtlas.Loaded, f: CloudField.Frame, fadeTop: Float, fadeBottom: Float) {
        val n = CloudField.layout(atlas.table, f, clouds, bolt)
        for (i in 0 until n) {
            val c = clouds[i]
            if (c.shaft) { drawShaft(canvas, c); continue }
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
        if (bolt.intensity > 0.01f) drawLightning(canvas, f)
    }

    /** A shaft of rain: streaks leaning with the wind, densest under the cloud, reaching the ground or (virga) not. */
    private fun drawShaft(canvas: Canvas, c: CloudDraw) {
        val h = c.bottom - c.top
        if (h <= 1f) return
        val col = c.color and 0xFFFFFF
        val a = (c.color ushr 24) and 0xFF
        fun with(alpha: Float) = (((a * alpha).toInt().coerceIn(0, 255)) shl 24) or col
        val grad = if (c.fade >= 1f) LinearGradient(0f, c.top, 0f, c.bottom, intArrayOf(with(0.35f), with(1f), with(0.85f)), floatArrayOf(0f, 0.15f, 1f), Shader.TileMode.CLAMP)
        else LinearGradient(0f, c.top, 0f, c.top + h * c.fade, intArrayOf(with(0.35f), with(1f), with(0f)), floatArrayOf(0f, 0.18f, 1f), Shader.TileMode.CLAMP)
        val sw = c.right - c.left
        m.setScale(sw / streaks.width, sw * 2.2f / streaks.width)
        m.postTranslate(c.left, c.top)
        m.postSkew(c.slant / h, 0f, 0f, c.top)
        val bs = BitmapShader(streaks, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT).apply { setLocalMatrix(m) }
        fill.shader = ComposeShader(grad, bs, PorterDuff.Mode.DST_IN)
        path.reset()
        path.moveTo(c.left, c.top); path.lineTo(c.right, c.top)
        path.lineTo(c.right + c.slant, c.bottom); path.lineTo(c.left + c.slant, c.bottom); path.close()
        canvas.drawPath(path, fill)
        fill.shader = null
    }

    /** The flash lighting the storm and the air round it, and the stroke itself. */
    private fun drawLightning(canvas: Canvas, f: CloudField.Frame) {
        val iBolt = bolt.intensity
        val strength = (0.05f + 0.16f * f.night) * iBolt
        glow.shader = android.graphics.RadialGradient(bolt.cx, bolt.cy, bolt.radius.coerceAtLeast(1f),
            intArrayOf(argb(strength, 0.85f, 0.82f, 1f), argb(strength * 0.35f, 0.75f, 0.75f, 1f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bolt.cx, bolt.cy, bolt.radius, glow)
        glow.shader = null
        if (bolt.inCloud || bolt.path.size < 4) return
        for ((pts, widthDp) in listOf(bolt.path to 1.3f, bolt.branch to 0.8f)) {
            if (pts.size < 4) continue
            path.reset(); path.moveTo(pts[0], pts[1])
            var q = 2
            while (q < pts.size) { path.lineTo(pts[q], pts[q + 1]); q += 2 }
            stroke.color = argb(0.35f * iBolt, 0.7f, 0.68f, 1f); stroke.strokeWidth = widthDp * 4.5f * f.dp
            canvas.drawPath(path, stroke)
            stroke.color = argb(0.95f * iBolt, 0.97f, 0.96f, 1f); stroke.strokeWidth = widthDp * f.dp
            canvas.drawPath(path, stroke)
        }
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
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
            size.width, size.height, v.horizonY * s, v.density * s, v.fov, time(), i.weather, i.state,
            look.sunAzimuth, look.elevation, i.light.light, i.light.lut, look.night,
            look.moonVisibility * look.moonIllumination * look.night,
        )
        val textBottom = i.textBottom * s
        drawIntoCanvas { painter.draw(it.nativeCanvas, atlas, f, textBottom - 6f * f.dp, textBottom + 34f * f.dp) }
    })
}

/**
 * Rain falling here (0.9.9), in front of the land: a grey veil over the scene and fine streaks, slanting a little with
 * the wind. Animated every frame while it rains (and only then); still when animations are off.
 */
@Composable
internal fun RainLayer(inputs: () -> GpuSkyInputs?, ambient: Boolean) {
    val clock = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val raining = (inputs()?.state?.rain ?: 0f) > 0.01f
    if (raining && ambient) androidx.compose.runtime.LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { clock.floatValue = (it - start) / 1e9f }
    }
    Box(Modifier.fillMaxSize().drawBehind {
        val i = inputs() ?: return@drawBehind
        val r = i.state.rain
        if (r < 0.01f) return@drawBehind
        val t = clock.floatValue
        val dp = density
        val night = i.look.night
        val veil = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color(0xFFB8C0C8), androidx.compose.ui.graphics.Color(0xFF1C222B), night)
        drawRect(veil.copy(alpha = 0.10f * r))
        val drop = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.Color(0xFFE6EBF0), androidx.compose.ui.graphics.Color(0xFF5E6874), night)
        val n = (260 * r).toInt()
        val w = size.width; val h = size.height
        for (k in 0 until n) {
            val h1 = CloudField.hash(77, k, 1); val h2 = CloudField.hash(77, k, 2); val h3 = CloudField.hash(77, k, 3)
            val len = (10f + 18f * h3) * dp
            val speed = (0.8f + 0.5f * h2) * h
            val y = ((h1 * 7.31f + t * speed / (h + len)) % 1f) * (h + len) - len
            val x = CloudField.hash(77, k, 4) * (w + 0.2f * h) - 0.1f * h + 0.18f * (y + len)
            drawLine(drop.copy(alpha = (0.07f + 0.13f * CloudField.hash(77, k, 5)) * r), androidx.compose.ui.geometry.Offset(x, y),
                androidx.compose.ui.geometry.Offset(x + 0.18f * len, y + len), strokeWidth = (0.6f + 0.7f * CloudField.hash(77, k, 6)) * dp)
        }
    })
}
