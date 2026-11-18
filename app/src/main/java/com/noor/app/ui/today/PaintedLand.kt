package com.noor.app.ui.today

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The medium graphics' painted sceneries (0.22). The owner chose, from sixty concept paintings (ten styles for each
 * scenery), one painting for each of five sceneries (the dunes keep the layered land of `land_layer.agsl`, which he
 * likes): the Pacific Northwest and Red rock in the anime-background style, Green hills and the Ocean in the bold
 * illustration style, the Arctic in the painted-relief style. Each was painted again in seven lights (night, twilight,
 * the low Sun at the left and at the right, day at the left and at the right, noon), land and water only, the sky
 * left clear, with the true horizon on row 900 of 1080 x 1440 (`assets/medium/<id>/`, `tools/medium/pack.py`).
 *
 * The phone draws the sky, the Sun, the moon and the clouds itself, and over them the land: the two lights nearest the
 * moment, one faded over the other ([weights]), muted under an overcast sky. Two pictures decoded at a time, in the
 * background; nothing is computed per pixel. So it is lighter than the layered land, on every phone (it is a bitmap:
 * the Canvas path below Android 13 draws it too). (0.23) The scene is never mirrored; the low Sun's light
 * comes from the side the Sun is on (the right in the morning in the southern hemisphere).
 */
internal object PaintedLand {
    const val WIDTH = 1080
    const val HEIGHT = 1440
    const val HORIZON = 900
    val STATES = listOf("night", "twilight", "golden_left", "day_left", "noon", "day_right", "golden_right")

    /** The painted scenery for a terrain of the 0.9 sky, or null (the dunes and the 0.9 mountains keep the layered land). */
    fun idOf(t: Terrain): String? = when (t) {
        Terrain.FOREST -> "pnw"; Terrain.HILLS -> "meadow"; Terrain.ARCTIC -> "arctic"; Terrain.SEA -> "ocean"; Terrain.CANYON -> "canyon"
        Terrain.DUNES, Terrain.MOUNTAINS -> null
    }

    /**
     * The two lights to show for the Sun at [elevation] (degrees) in the [evening] (after noon) or the morning, and how
     * much of the second one: smooth in the Sun's height, never a jump. Night under -10 degrees; twilight about -3;
     * the low Sun about 3; day about 15; noon from about 45 (the morning's and the evening's sides meet there).
     */
    fun weights(elevation: Double, evening: Boolean): Pair<Pair<String, String>, Float> {
        val golden = if (evening) "golden_right" else "golden_left"
        val day = if (evening) "day_right" else "day_left"
        fun s(a: Double, b: Double) = smooth(a, b, elevation)
        return when {
            elevation < -3.0 -> ("night" to "twilight") to s(-11.0, -3.0)
            elevation < 3.0 -> ("twilight" to golden) to s(-3.0, 3.0)
            elevation < 15.0 -> (golden to day) to s(3.0, 15.0)
            else -> (day to "noon") to s(22.0, 50.0)
        }
    }

    private fun smooth(a: Double, b: Double, x: Double): Float { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return (t * t * (3 - 2 * t)).toFloat() }

    /** A scenery's skyline: the top of the land in [n] strips across the unmirrored picture, as heights over the horizon in widths. */
    class Meta(val skyline: FloatArray)

    fun readMeta(assets: AssetManager, id: String): Meta? = runCatching {
        val j = org.json.JSONObject(assets.open("medium/$id/meta.json").bufferedReader().use { it.readText() })
        val a = j.getJSONArray("skyline")
        Meta(FloatArray(a.length()) { a.getDouble(it).toFloat() })
    }.onFailure { Log.w("NoorSky", "Painted land $id unavailable", it) }.getOrNull()
}

/**
 * Draws a painted scenery ([PaintedLand]): reads its pictures in the background (two at a time), fades the lights of
 * the moment into one another, and fades the whole in when it first appears.
 */
internal class PaintedLandPainter(private val assets: AssetManager, val id: String) {
    /** Changes when a picture is ready: read while drawing, so the layer redraws. */
    val version = mutableIntStateOf(0)
    /** True when the scenery's pictures cannot be read: the layered land is drawn instead. */
    val failed = mutableStateOf(false)
    /** The skyline in the real sky's form ([SunLift.skyline]: tangents, first strip on the right), for the Sun's ring. */
    @Volatile var skyline: FloatArray? = null
        private set
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private val busy = HashSet<String>()
    private var sample = 0
    private var shownAt = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(); private val dst = RectF()
    private var filterStep = -1
    private var filter: ColorMatrixColorFilter? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        LOAD.execute {
            val m = PaintedLand.readMeta(assets, id)
            main.post {
                if (m == null) failed.value = true
                else skyline = FloatArray(m.skyline.size) { m.skyline[m.skyline.size - 1 - it] / SunLift.FOCAL }
                version.intValue++
            }
        }
    }

    private fun request(state: String, sampleSize: Int, synchronous: Boolean) {
        if (bitmaps.containsKey(state) || state in busy) return
        busy += state
        val job = Runnable {
            val bmp = runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                assets.open("medium/$id/$state.webp").use { BitmapFactory.decodeStream(it, null, o) }
            }.onFailure { Log.w("NoorSky", "Painted land $id/$state unreadable", it) }.getOrNull()
            val done = Runnable {
                busy -= state
                if (bmp == null) failed.value = true
                else {
                    bitmaps[state] = bmp
                    // Two lights at a time, and the one a step away while the Sun is dragged: three at most.
                    while (bitmaps.size > 3) bitmaps.remove(bitmaps.keys.first())
                }
                version.intValue++
            }
            if (synchronous) done.run() else main.post(done)
        }
        if (synchronous) job.run() else LOAD.execute(job)
    }

    /**
     * Draws the land for the moment ([elevation], [evening]) over a layer [w] x [h] whose horizon is [horizonY], muted by
     * [overcast]. Returns false while nothing can be drawn yet.
     */
    fun draw(canvas: android.graphics.Canvas, w: Float, horizonY: Float, elevation: Double, evening: Boolean, overcast: Float, rtl: Boolean, synchronous: Boolean): Boolean {
        if (failed.value) return false
        // The pictures at about the size they are shown (a power of two smaller on a narrow screen).
        val want = max(1, Integer.highestOneBit(max(1, (PaintedLand.WIDTH / max(w, 1f)).toInt())))
        if (want != sample) { sample = want; bitmaps.clear() }
        val (pair, t) = PaintedLand.weights(elevation, evening)
        val (a, b) = pair
        val needA = t < 0.999f; val needB = t > 0.001f
        if (needA) request(a, sample, synchronous)
        if (needB) request(b, sample, synchronous)
        val ba = if (needA) bitmaps[a] else null
        val bb = if (needB) bitmaps[b] else null
        if ((needA && ba == null) || (needB && bb == null)) {
            // Until both are read, the one there is, whole.
            val one = ba ?: bb ?: return false
            return drawOne(canvas, one, 1f, w, horizonY, overcast, rtl, synchronous)
        }
        if (ba != null) drawOne(canvas, ba, 1f, w, horizonY, overcast, rtl, synchronous)
        if (bb != null) drawOne(canvas, bb, if (ba != null) t else 1f, w, horizonY, overcast, rtl, synchronous)
        return true
    }

    private fun drawOne(canvas: android.graphics.Canvas, bmp: Bitmap, alpha: Float, w: Float, horizonY: Float, overcast: Float, rtl: Boolean, synchronous: Boolean): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (shownAt == 0L) shownAt = now
        val fadeIn = if (synchronous) 1f else ((now - shownAt) / 600f).coerceIn(0f, 1f)
        if (fadeIn < 1f) main.postDelayed({ version.intValue++ }, 32)
        val k = w / PaintedLand.WIDTH
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(0f, horizonY - PaintedLand.HORIZON * k, w, horizonY + (PaintedLand.HEIGHT - PaintedLand.HORIZON) * k)
        paint.alpha = (255 * alpha * fadeIn * fadeIn * (3 - 2 * fadeIn)).toInt().coerceIn(0, 255)
        // Under an overcast sky the light is grey and flat: less colour, a little darker.
        // (Made again only when the weather has changed by a step: not on every frame.)
        val step = if (overcast > 0.02f) (overcast * 50f).toInt().coerceIn(1, 50) else 0
        if (step != filterStep) {
            filterStep = step
            val o = step / 50f
            filter = if (step == 0) null else ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(1f - 0.5f * o)
                val d = 1f - 0.22f * o
                postConcat(ColorMatrix(floatArrayOf(d, 0f, 0f, 0f, 0f, 0f, d, 0f, 0f, 0f, 0f, 0f, d, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            })
        }
        paint.colorFilter = filter
        if (rtl) { canvas.save(); canvas.scale(-1f, 1f, w / 2f, 0f) }
        canvas.drawBitmap(bmp, src, dst, paint)
        if (rtl) canvas.restore()
        return true
    }

    private companion object {
        val LOAD: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "NoorLand").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    }
}

/**
 * The medium graphics' painted land (0.22): over the sky and the clouds, lifted with the land when the page scrolls,
 * and faded into the page at the header's foot as the layered land is.
 */
@Composable
internal fun PaintedLandLayer(painter: PaintedLandPainter, inputs: () -> GpuSkyInputs?, display: () -> Double, lift: () -> Float, page: Color) {
    Box(Modifier.fillMaxSize().graphicsLayer { translationY = lift() }.drawBehind {
        painter.version.intValue
        val i = inputs() ?: return@drawBehind
        val d = display()
        val scale = size.width / i.view.w
        val hy = i.view.horizonY * scale
        // (The same overcast the medium sky's other layers read: the weather of the moment.)
        val overcast = i.state.overcast.coerceIn(0f, 1f)
        var drawn = false
        // (0.23) The light from the side the Sun is on: the right in the afternoon where it moves left to right, the
        // left in the afternoon in the southern hemisphere (the painting's "evening" side is its right).
        val sunRight = i.scene.eveningAt(d) == (i.view.geometry.travel > 0)
        drawIntoCanvas { drawn = painter.draw(it.nativeCanvas, size.width, hy, i.scene.elevationAt(d), sunRight, overcast, i.view.rtl, SkyCompute.synchronous) }
        if (drawn) {
            val fade = 28.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page), startY = size.height - fade, endY = size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade), size = androidx.compose.ui.geometry.Size(size.width, fade))
        }
    })
}
