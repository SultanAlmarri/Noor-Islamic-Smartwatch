package com.noor.app.ui.today

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.noor.app.R
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The GPU renderer of the home sky (0.9.6, Android 13 and later): two AGSL shaders, kept in res/raw so the design bench
 * can compile the very same code.
 *
 * - The sky (atmosphere table, Sun, moon, stars, Milky Way, cirrus, cloud banks, cumulus) is soft everywhere, so it is
 *   rendered at about 1.5 pixels per dp into its own cached layer and scaled up: a fraction of the cost, no visible loss.
 * - The land is rendered at full resolution (the skyline must be crisp) into its own cached layer.
 *
 * Cached layers are redrawn only when what they show changes (the moment shown, the slow ambient clock), not when the
 * countdown ticks elsewhere on the screen. Where a shader cannot be built or drawn (an old GPU driver, a software
 * canvas), the Canvas renderer takes over.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SkyShaders private constructor(val sky: RuntimeShader, val land: RuntimeShader) {
    companion object {
        @Volatile var failed = false
        @Volatile private var sources: Pair<String, String>? = null

        private const val SKY_HEAD = """
uniform shader skyLut;
float4 lutAt(float2 p) { return float4(skyLut.eval(p)); }
"""
        private const val LAND_HEAD = """
uniform shader skyLut;
uniform shader ridges;
float4 lutAt(float2 p) { return float4(skyLut.eval(p)); }
float4 ridgeTex(float2 p) { return float4(ridges.eval(p)); }
"""
        private const val FOOT = "\nhalf4 main(float2 fc) { return half4(render(fc)); }\n"

        private fun read(res: Resources, id: Int) = res.openRawResource(id).bufferedReader().use { it.readText() }

        fun create(res: Resources): SkyShaders? {
            if (failed) return null
            return try {
                val (s, l) = sources ?: run {
                    val common = read(res, R.raw.sky_common)
                    (SKY_HEAD + common + read(res, R.raw.sky_layer) + FOOT to LAND_HEAD + common + read(res, R.raw.land_layer) + FOOT).also { sources = it }
                }
                SkyShaders(RuntimeShader(s), RuntimeShader(l)).also { Log.i("NoorSky", "GPU sky shaders built") }
            } catch (e: Exception) {
                Log.w("NoorSky", "Sky shaders unavailable; using the Canvas sky", e)
                failed = true
                null
            }
        }

        fun usable(view: View): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !failed && view.isHardwareAccelerated
    }
}

/** Lays the child out at [scale] of the available size and draws it scaled back up, in its own cached layer. */
internal fun Modifier.reducedLayer(scale: Float, block: GraphicsLayerScope.() -> Unit = {}): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth; val h = constraints.maxHeight
    val sw = ceil(w * scale).toInt().coerceAtLeast(1); val sh = ceil(h * scale).toInt().coerceAtLeast(1)
    val p = measurable.measure(Constraints.fixed(sw, sh))
    layout(w, h) {
        p.placeWithLayer(0, 0) {
            scaleX = w / sw.toFloat(); scaleY = h / sh.toFloat()
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
            block()
        }
    }
}

/** What the shaders need beyond the moment itself. */
internal class GpuSkyInputs(
    val scene: SkyScene, val view: SkyView, val look: SkyLook, val light: SkyLight, val weather: Weather, val land: LandProfile,
    val page: Color, val textBottom: Float,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun GpuSkyLayer(shaders: SkyShaders, inputs: () -> GpuSkyInputs?, time: () -> Float, parallax: () -> Float) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scale = (1.5f / density).coerceIn(0.4f, 1f)
    val paint = remember { Paint() }
    val lutCache = remember { LutShaderCache() }
    Box(Modifier.fillMaxSize().reducedLayer(scale) { translationY = parallax() * 0.5f }.drawBehind {
        val i = inputs() ?: return@drawBehind
        if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) return@drawBehind
        val sh = shaders.sky
        val s = size.width / i.view.w
        val t = time()
        val l = i.light.light; val look = i.look; val v = i.view
        sh.u("uSize", size.width, size.height)
        sh.u("uDp", v.density * s)
        sh.u("uHorizon", v.horizonY * s)
        sh.u("uAltTop", SkyView.ALT_TOP.toFloat()); sh.u("uAltPow", SkyView.ALT_POW.toFloat())
        sh.u("uFov", v.fov.toFloat())
        sh.u("uLutSize", Atmosphere.NX.toFloat(), Atmosphere.NY.toFloat()); sh.u("uLutPow", Atmosphere.LUT_POW.toFloat())
        sh.u("uSunPos", look.sunX * s, look.sunY * s)
        val ca = cos(look.drawAltitude); val az = look.sunAzimuth
        sh.u("uSunDir", (ca * sin(az)).toFloat(), sin(look.drawAltitude).toFloat(), (ca * cos(az)).toFloat())
        sh.u("uSunEl", look.elevation.toFloat())
        sh.u3("uSunDisc", l.sunGround, 2.2); sh.u3("uSunCloud", l.sunCloud); sh.u3("uSunHigh", l.sunHigh)
        sh.u3("uAmbTop", l.ambTop); sh.u3("uAmbLow", l.ambLow)
        sh.u("uNight", look.night)
        sh.u("uTime", t)
        sh.u("uWeather", i.weather.cumulus, i.weather.cirrus, i.weather.seedX, i.weather.seedY)
        sh.u("uCloudTop", v.cloudTopY * s)
        sh.u("uMoon", look.moonX * s, look.moonY * s, 10f * v.density * s, look.moonVisibility)
        sh.u("uMoonLight", look.moonLight[0], look.moonLight[1], look.moonLight[2])
        sh.u("uMilky", look.milkyWay)
        sh.u("uWind", t * 0.02f)
        sh.u("uTextBottom", i.textBottom * s)
        sh.setInputBuffer("skyLut", lutCache.shaderFor(i.light))
        paint.shader = sh
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    })
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun GpuLandLayer(shaders: SkyShaders, inputs: () -> GpuSkyInputs?, lift: () -> Float, time: () -> Float) {
    val paint = remember { Paint() }
    val lutCache = remember { LutShaderCache() }
    val ridgeCache = remember { RidgeShaderCache() }
    Box(Modifier.fillMaxSize().reducedLayer(1f) { translationY = lift() }.drawBehind {
        val i = inputs() ?: return@drawBehind
        if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) return@drawBehind
        drawLand(shaders.land, paint, i, time(), lutCache, ridgeCache)
    })
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawLand(sh: RuntimeShader, paint: Paint, i: GpuSkyInputs, t: Float, lutCache: LutShaderCache, ridgeCache: RidgeShaderCache) {
    val v = i.view; val look = i.look; val l = i.light.light
    val g = i.land.geometry(v.horizonY, v.h, v.density)
    sh.u("uSize", size.width, size.height)
    sh.u("uDp", v.density)
    sh.u("uHorizon", v.horizonY)
    sh.u("uBaseA", g.base[0], g.base[1], g.base[2], g.base[3]); sh.u("uBaseB", g.base[4])
    sh.u("uAmpA", g.amp[0], g.amp[1], g.amp[2], g.amp[3]); sh.u("uAmpB", g.amp[4])
    sh.u("uDistA", g.distKm[0], g.distKm[1], g.distKm[2], g.distKm[3]); sh.u("uDistB", g.distKm[4])
    sh.u("uRidgeN", LandProfile.N.toFloat())
    sh.u("uDunes", if (i.land.terrain == Terrain.DUNES) 1f else 0f)
    sh.u("uFov", v.fov.toFloat())
    sh.u("uSunPos", look.sunX, look.sunY)
    sh.u("uSunEl", look.elevation.toFloat())
    sh.u3("uSunGround", l.sunGround); sh.u3("uAmbTop", l.ambTop); sh.u3("uAmbLow", l.ambLow)
    sh.u("uPage", i.page.red, i.page.green, i.page.blue)
    sh.u("uMist", look.mist)
    sh.u("uNight", look.night)
    sh.u("uTime", t)
    sh.u("uWeather", i.weather.cumulus, i.weather.cirrus, i.weather.seedX, i.weather.seedY)
    sh.u("uWind", t * 0.02f)
    sh.u("uLutSize", Atmosphere.NX.toFloat(), Atmosphere.NY.toFloat()); sh.u("uLutPow", Atmosphere.LUT_POW.toFloat())
    sh.u("uSeed", (i.scene.landSeed % 97).toFloat())
    sh.setInputBuffer("skyLut", lutCache.shaderFor(i.light))
    sh.setInputBuffer("ridges", ridgeCache.shaderFor(i.land))
    paint.shader = sh
    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
}

/** One bitmap per atmosphere table, sampled with bilinear filtering and clamped at the edges. */
internal class LutShaderCache {
    private var key: SkyLight? = null
    private var shader: BitmapShader? = null
    fun shaderFor(l: SkyLight): BitmapShader {
        shader?.let { if (key === l) return it }
        val bmp = Bitmap.createBitmap(l.lut, Atmosphere.NX, Atmosphere.NY, Bitmap.Config.ARGB_8888)
        return linear(bmp).also { shader = it; key = l }
    }
}

internal class RidgeShaderCache {
    private var key: LandProfile? = null
    private var shader: BitmapShader? = null
    fun shaderFor(p: LandProfile): BitmapShader {
        shader?.let { if (key === p) return it }
        val bmp = Bitmap.createBitmap(p.table(), LandProfile.N, 2, Bitmap.Config.ARGB_8888)
        return linear(bmp).also { shader = it; key = p }
    }
}

private fun linear(bmp: Bitmap): BitmapShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_LINEAR
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.u(name: String, vararg v: Float) {
    try {
        when (v.size) {
            1 -> setFloatUniform(name, v[0]); 2 -> setFloatUniform(name, v[0], v[1])
            3 -> setFloatUniform(name, v[0], v[1], v[2]); else -> setFloatUniform(name, v[0], v[1], v[2], v[3])
        }
    } catch (e: IllegalArgumentException) {
        // A uniform the compiler removed as unused: nothing to set.
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.u3(name: String, c: DoubleArray, k: Double = 1.0) = u(name, (c[0] * k).toFloat(), (c[1] * k).toFloat(), (c[2] * k).toFloat())

