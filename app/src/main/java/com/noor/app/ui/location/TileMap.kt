package com.noor.app.ui.location

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.noor.app.data.WorldStore
import com.noor.prayer.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The map layers (0.9.7): Esri's World Imagery (satellite and aerial photographs, sub-metre in most cities) with
 * Esri's boundaries and place names over it, or Esri's street map. Free for non-commercial use with attribution
 * (Esri's terms); tiles are fetched only while the map is open and kept in the app's cache.
 */
enum class MapLayer(val urls: List<String>, val attribution: String) {
    SATELLITE(listOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}",
    ), "Esri, Vantor, Earthstar Geographics, GIS User Community · Labels: Esri, HERE, Garmin, © OpenStreetMap contributors"),
    STREETS(listOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"),
        "Esri, HERE, Garmin, USGS, © OpenStreetMap contributors, GIS User Community"),
}

/** Web Mercator, the projection of every web map: x and y from 0 to 1 across the world. */
object Mercator {
    const val MAX_LAT = 85.05112878
    fun x(lon: Double) = (lon + 180.0) / 360.0
    fun y(lat: Double): Double { val r = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT)); return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 }
    fun lon(x: Double) = x * 360.0 - 180.0
    fun lat(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
}

/** Where the map looks: its centre (Mercator 0..1) and zoom (continuous; 2 is the world, 18 a street). */
@Stable
class MapState(center: GeoPoint, zoom: Double) {
    var x by mutableDoubleStateOf(Mercator.x(center.longitude))
    var y by mutableDoubleStateOf(Mercator.y(center.latitude))
    var zoom by mutableDoubleStateOf(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    /** True while a finger is on the map or it is still gliding. */
    var moving by mutableStateOf(false)
    internal var size = IntSize.Zero
    internal var tilePx = 256f

    val center: GeoPoint get() = GeoPoint(Mercator.lat(y), Mercator.lon(wrap(x)))

    /** World size in pixels at the current zoom. */
    internal fun worldPx(z: Double = zoom) = tilePx * 2.0.pow(z)

    fun moveBy(dx: Float, dy: Float) {
        val w = worldPx()
        x = wrap(x - dx / w); y = (y - dy / w).coerceIn(0.0, 1.0)
    }

    /** Zoom by [factor] keeping the point under [focus] (px from the top-left) where it is. */
    fun zoomBy(factor: Float, focus: Offset) {
        val fx = focus.x - size.width / 2f; val fy = focus.y - size.height / 2f
        val before = worldPx()
        val wx = x + fx / before; val wy = y + fy / before
        zoom = (zoom + ln(factor.toDouble()) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val after = worldPx()
        x = wrap(wx - fx / after); y = (wy - fy / after).coerceIn(0.0, 1.0)
    }

    suspend fun animateTo(p: GeoPoint, toZoom: Double = zoom) {
        val from = Triple(x, y, zoom)
        val tx = Mercator.x(p.longitude); val ty = Mercator.y(p.latitude)
        val dxw = (tx - from.first).let { if (it > 0.5) it - 1 else if (it < -0.5) it + 1 else it }
        val a = Animatable(0f)
        a.animateTo(1f, tween(650)) {
            val t = value.toDouble()
            x = wrap(from.first + dxw * t); y = from.second + (ty - from.second) * t
            zoom = from.third + (toZoom.coerceIn(MIN_ZOOM, MAX_ZOOM) - from.third) * t
        }
    }

    fun jumpTo(p: GeoPoint, toZoom: Double = zoom) { x = Mercator.x(p.longitude); y = Mercator.y(p.latitude); zoom = toZoom.coerceIn(MIN_ZOOM, MAX_ZOOM) }

    companion object {
        const val MIN_ZOOM = 1.5
        const val MAX_ZOOM = 19.0
        fun wrap(x: Double) = x - floor(x)
    }
}

/** Tiles in memory (about 40 MB at most) and on disk (the app's cache folder, kept 30 days). */
class TileCache(context: Context) {
    private val dir = File(context.cacheDir, "map-tiles")
    private val memory = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val failed = HashSet<String>()
    private val loading = HashSet<String>()
    private val gate = Semaphore(6)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Bumped when a tile arrives, to redraw. */
    var version by mutableIntStateOf(0)
    /** True once a tile could not be fetched (no network): the screen says so. */
    var offline by mutableStateOf(false)

    fun get(url: String): ImageBitmap? = memory.get(url)

    fun request(url: String) {
        if (!networkAllowed) { offline = true; return }
        synchronized(this) { if (memory.get(url) != null || url in loading || url in failed) return; loading += url }
        scope.launch {
            val bmp = gate.withPermit { runCatching { load(url) }.getOrNull() }
            synchronized(this@TileCache) { loading -= url; if (bmp == null) failed += url }
            if (bmp != null) { memory.put(url, bmp); offline = false } else offline = true
            withContext(Dispatchers.Main) { version++ }
        }
    }

    /** Lets tiles that failed be tried again (when the user moves the map after the network came back). */
    fun retryFailed() = synchronized(this) { failed.clear() }

    private fun load(url: String): ImageBitmap? {
        val f = File(dir, url.substringAfter("/services/").replace('/', '_'))
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 24 * 3600 * 1000) {
            BitmapFactory.decodeFile(f.path)?.let { return it.asImageBitmap() }
        }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000; setRequestProperty("User-Agent", WorldStore.USER_AGENT)
        }
        if (c.responseCode != 200) return null
        val bytes = c.inputStream.use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        runCatching { dir.mkdirs(); f.writeBytes(bytes) }
        return bmp.asImageBitmap()
    }

    companion object {
        /** Off in tests: no tile is fetched and the map says it is offline. */
        @Volatile var networkAllowed = true
    }

    /** Keeps the disk cache under about 150 MB, removing the oldest tiles. */
    fun trim() = scope.launch {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return@launch
        var total = files.sumOf { it.length() }
        for (f in files) { if (total < 150L * 1024 * 1024) break; total -= f.length(); f.delete() }
    }
}

/**
 * A slippy map: drag to move, pinch or double-tap to zoom, fling to glide. Missing tiles are drawn from a coarser
 * zoom until they arrive, so the picture never goes blank.
 */
@Composable
fun TileMap(state: MapState, layer: MapLayer, cache: TileCache, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    state.tilePx = 256f * (density / 1.5f).coerceAtLeast(1f)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { cache.trim() }
    Box(modifier
        .pointerInput(state) {
            detectTapGestures(onDoubleTap = { p -> scope.launch {
                val a = Animatable(0f)
                var last = 0f
                a.animateTo(1f, tween(280)) { val f = 2.0.pow((value - last).toDouble()).toFloat(); last = value; state.zoomBy(f, p) }
            } })
        }
        .pointerInput(state) {
            val decay = exponentialDecay<Offset>(frictionMultiplier = 1.6f)
            val conv = TwoWayConverter<Offset, AnimationVector2D>({ AnimationVector2D(it.x, it.y) }, { Offset(it.v1, it.v2) })
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                state.moving = true
                cache.retryFailed()
                val tracker = VelocityTracker()
                var multi = false
                do {
                    val e = awaitPointerEvent()
                    val pan = e.calculatePan(); val zoom = e.calculateZoom()
                    if (e.changes.count { it.pressed } > 1) multi = true
                    if (zoom != 1f) state.zoomBy(zoom, e.calculateCentroid())
                    if (pan != Offset.Zero) state.moveBy(pan.x, pan.y)
                    e.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    e.changes.forEach { if (it.positionChanged()) it.consume() }
                } while (e.changes.any { it.pressed })
                val v = tracker.calculateVelocity()
                if (!multi && (kotlin.math.abs(v.x) + kotlin.math.abs(v.y)) > 300f) scope.launch {
                    var prev = Offset.Zero
                    Animatable(Offset.Zero, conv).animateDecay(Offset(v.x, v.y), decay) {
                        val d = value - prev; prev = value; state.moveBy(d.x, d.y)
                    }
                    state.moving = false
                } else state.moving = false
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            state.size = IntSize(size.width.toInt(), size.height.toInt())
            @Suppress("UNUSED_VARIABLE") val v = cache.version // redraw when a tile arrives
            drawRect(Color(0xFF0B1A2A))
            for (url in layer.urls) drawLayer(state, url, cache)
        }
    }
}

private fun DrawScope.drawLayer(state: MapState, template: String, cache: TileCache) {
    val zi = floor(state.zoom + 0.35).toInt().coerceIn(1, 19)
    val n = 1 shl zi
    val tile = (state.tilePx * 2.0.pow(state.zoom - zi)).toFloat()
    val worldX = state.x * n * tile; val worldY = state.y * n * tile
    val left = worldX - size.width / 2; val top = worldY - size.height / 2
    val x0 = floor(left / tile).toInt(); val x1 = floor((left + size.width) / tile).toInt()
    val y0 = floor(top / tile).toInt().coerceAtLeast(0); val y1 = floor((top + size.height) / tile).toInt().coerceAtMost(n - 1)
    for (ty in y0..y1) for (tx in x0..x1) {
        val wx = ((tx % n) + n) % n
        val dx = (tx * tile - left).toFloat(); val dy = (ty * tile - top).toFloat()
        val url = template.replace("{z}", "$zi").replace("{x}", "$wx").replace("{y}", "$ty")
        val img = cache.get(url)
        if (img != null) {
            drawImage(img, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize((dx + tile).roundToInt() - dx.roundToInt(), (dy + tile).roundToInt() - dy.roundToInt()))
            continue
        }
        cache.request(url)
        // Meanwhile, the same area from a coarser tile, enlarged.
        for (up in 1..4) {
            val pz = zi - up; if (pz < 0) break
            val k = 1 shl up
            val px = wx / k; val py = ty / k
            val parent = cache.get(template.replace("{z}", "$pz").replace("{x}", "$px").replace("{y}", "$py")) ?: continue
            val sub = parent.width / k
            drawImage(parent, srcOffset = IntOffset((wx % k) * sub, (ty % k) * sub), srcSize = IntSize(sub, sub),
                dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize((dx + tile).roundToInt() - dx.roundToInt(), (dy + tile).roundToInt() - dy.roundToInt()))
            break
        }
    }
}

/** A tile cache for the screen, kept while it is shown. */
@Composable
fun rememberTileCache(): TileCache {
    val context = LocalContext.current.applicationContext
    return remember { TileCache(context) }
}
