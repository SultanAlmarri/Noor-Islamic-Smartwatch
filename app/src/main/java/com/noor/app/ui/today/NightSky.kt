package com.noor.app.ui.today

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.noor.app.R
import java.io.DataInputStream
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The real night sky (0.9.8): where the stars and the Milky Way are for this place at this moment.
 *
 * The home sky draws the day from left to right (right to left in Arabic): the side where the Sun rises is east. The
 * stars are a real, unmirrored view with east on that same side: facing south when the scene runs left to right,
 * facing north when it runs right to left (where Arabic readers see the Pole Star and the Plough). The horizon is the
 * scene's horizon and the sky is compressed upwards as the sky shader does (altitude 80° at the top edge). In that frame:
 * - [frame] gives the celestial axes (for the faint stars, which the shader places on the celestial sphere so they turn
 *   with the night) and the galactic pole and centre (for the Milky Way, drawn where it really is);
 * - [drawStars] draws the 1,600 stars brighter than magnitude 5 of the Yale Bright Star Catalogue (public domain) at
 *   their real places, sized by brightness and coloured by temperature, into a bitmap the sky shader shows at night.
 */
internal object NightSky {
    class Frame(
        /** The equatorial x, y, z axes (towards RA 0, RA 6 h, the celestial north pole) in view space. */
        val celX: FloatArray, val celY: FloatArray, val celZ: FloatArray,
        /** The galactic north pole and the galactic centre in view space. */
        val galN: FloatArray, val galC: FloatArray,
    )

    class Star(val ra: Double, val dec: Double, val mag: Float, val kelvin: Int)

    private const val DEG = PI / 180

    /** Greenwich mean sidereal time, degrees (IAU 1982, accurate to about a second for centuries around 2000). */
    fun gmst(at: Instant): Double {
        val jd = at.toEpochMilli() / 86_400_000.0 + 2440587.5
        val d = jd - 2451545.0
        val t = d / 36525.0
        return (280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38_710_000.0).mod(360.0)
    }

    /** A direction given in right ascension and declination (degrees), as seen in the view frame. */
    fun view(raDeg: Double, decDeg: Double, latDeg: Double, lstDeg: Double, centerAzDeg: Double): DoubleArray {
        val h = (lstDeg - raDeg) * DEG; val d = decDeg * DEG; val phi = latDeg * DEG
        val up = sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h)
        val north = cos(phi) * sin(d) - sin(phi) * cos(d) * cos(h)
        val east = -cos(d) * sin(h)
        val a0 = centerAzDeg * DEG
        // Forward: towards azimuth a0; right: a0 + 90°.
        val fwd = east * sin(a0) + north * cos(a0)
        val right = east * cos(a0) - north * sin(a0)
        return doubleArrayOf(right, up, fwd)
    }

    private fun f(v: DoubleArray) = floatArrayOf(v[0].toFloat(), v[1].toFloat(), v[2].toFloat())

    /** The azimuth the middle of the screen faces: east lies on the side where the scene's Sun rises. */
    fun centerAzimuth(rtl: Boolean) = if (rtl) 0.0 else 180.0

    fun frame(at: Instant, latDeg: Double, lonDeg: Double, rtl: Boolean = false): Frame {
        val lst = gmst(at) + lonDeg
        val a0 = centerAzimuth(rtl)
        return Frame(
            f(view(0.0, 0.0, latDeg, lst, a0)), f(view(90.0, 0.0, latDeg, lst, a0)), f(view(0.0, 90.0, latDeg, lst, a0)),
            f(view(192.85948, 27.12825, latDeg, lst, a0)), f(view(266.40499, -28.93617, latDeg, lst, a0)),
        )
    }

    @Volatile private var catalogue: List<Star>? = null

    fun stars(res: Resources): List<Star> = catalogue ?: synchronized(this) {
        catalogue ?: DataInputStream(res.openRawResource(R.raw.stars).buffered()).use { d ->
            List(d.readInt()) { Star(d.readFloat().toDouble(), d.readFloat().toDouble(), d.readFloat(), d.readShort().toInt()) }
        }.also { catalogue = it }
    }

    /** A star's colour from its temperature (a blackbody, gently: the eye sees star colours faintly). */
    fun colour(kelvin: Int): Int {
        val t = kelvin / 100.0
        var r: Double; var g: Double; var b: Double
        if (t <= 66) { r = 255.0; g = 99.47 * kotlin.math.ln(t) - 161.12; b = if (t <= 19) 0.0 else 138.52 * kotlin.math.ln(t - 10) - 305.04 }
        else { r = 329.70 * (t - 60).pow(-0.1332); g = 288.12 * (t - 60).pow(-0.0755); b = 255.0 }
        fun c(x: Double) = (x.coerceIn(0.0, 255.0) * 0.45 + 255 * 0.55).toInt()
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /**
     * Draws the catalogue's stars into [bmp] (the sky layer's size): [horizonY] the horizon (px), [fov] radians across
     * the width, [altTop]/[altPow] the sky's vertical mapping, [dp] px per dp.
     */
    fun drawStars(bmp: Bitmap, stars: List<Star>, at: Instant, latDeg: Double, lonDeg: Double, horizonY: Float, fov: Double,
                  altTop: Double, altPow: Double, dp: Float, rtl: Boolean = false) {
        bmp.eraseColor(0)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = bmp.width.toFloat()
        val lst = gmst(at) + lonDeg
        val a0 = centerAzimuth(rtl)
        for (s in stars) {
            val v = view(s.ra, s.dec, latDeg, lst, a0)
            val alt = asin(v[1].coerceIn(-1.0, 1.0)) / DEG
            if (alt < 0.5 || alt >= altTop) continue
            val az = atan2(v[0], v[2])
            if (kotlin.math.abs(az) > fov / 2 + 0.05) continue
            val x = (w * (0.5 + az / fov)).toFloat()
            val y = (horizonY - horizonY * (alt / altTop).pow(1.0 / altPow)).toFloat()
            // Brightness: each magnitude is 2.5 times dimmer; the air near the horizon dims them further.
            val air = ((alt - 0.5) / 12.0).coerceIn(0.0, 1.0).pow(0.7)
            val flux = 10.0.pow(-0.4 * (s.mag - 1.0)).coerceAtMost(4.0) * air
            val a = (0.12 + 0.60 * flux.pow(0.5)).coerceAtMost(1.0)
            val r = ((0.35 + 0.50 * flux.pow(0.4)) * dp).toFloat()
            if (a < 0.04) continue
            val col = colour(s.kelvin)
            paint.shader = RadialGradient(x, y, r * 2.4f, intArrayOf(col, (col and 0x00FFFFFF) or ((0.35 * 255).toInt() shl 24), 0), floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
            paint.alpha = (a * 255).toInt()
            canvas.drawCircle(x, y, r * 2.4f, paint)
        }
    }

    fun length(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
}
