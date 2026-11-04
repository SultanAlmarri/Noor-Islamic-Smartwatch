package com.noor.prayer.qibla

import com.noor.prayer.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Direction and distance from a place to the Kaaba. */
data class QiblaResult(
    /** Initial bearing of the shortest path on the WGS-84 ellipsoid, degrees clockwise from true north, [0, 360). */
    val bearingDeg: Double,
    /** Initial bearing of the great circle on a sphere (the classical formula), for comparison. */
    val greatCircleDeg: Double,
    /** Length of the ellipsoidal shortest path, kilometres. */
    val distanceKm: Double,
    /** Within a few hundred kilometres of the point opposite Makkah, where every direction is nearly as short. */
    val nearAntipode: Boolean,
    /** Close enough that a compass bearing stops being a useful guide (inside Makkah). */
    val veryClose: Boolean,
)

object Qibla {
    /** Centre of the Kaaba (WGS-84). */
    val KAABA = GeoPoint(21.422487, 39.826206)

    private const val A = 6378137.0                 // WGS-84 semi-major axis, m
    private const val F = 1 / 298.257223563         // flattening
    private const val B = A * (1 - F)
    private const val MEAN_RADIUS_KM = 6371.0088

    fun from(p: GeoPoint): QiblaResult {
        val gc = greatCircleBearing(p, KAABA)
        val geo = vincentyInverse(p, KAABA)
        val distanceKm = geo?.second?.div(1000.0) ?: (greatCircleDistanceKm(p, KAABA))
        // Vincenty does not converge close to the antipode; there the ellipsoidal bearing is ill-defined anyway.
        val bearing = geo?.first ?: gc
        return QiblaResult(
            bearingDeg = bearing, greatCircleDeg = gc, distanceKm = distanceKm,
            nearAntipode = distanceKm > 19_700.0 || geo == null,
            veryClose = distanceKm < 5.0,
        )
    }

    /** Classical spherical initial bearing (degrees, [0, 360)). */
    fun greatCircleBearing(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    fun greatCircleDistanceKm(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude)
        val dp = p2 - p1; val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * MEAN_RADIUS_KM * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * Vincenty's inverse formula on the WGS-84 ellipsoid (T. Vincenty, Survey Review 23(176), 1975).
     * Returns (initial bearing in degrees, distance in metres), or null when the iteration does not converge.
     */
    fun vincentyInverse(a: GeoPoint, b: GeoPoint): Pair<Double, Double>? {
        val l = Math.toRadians(b.longitude - a.longitude)
        val u1 = atan((1 - F) * tan(Math.toRadians(a.latitude)))
        val u2 = atan((1 - F) * tan(Math.toRadians(b.latitude)))
        val sinU1 = sin(u1); val cosU1 = cos(u1); val sinU2 = sin(u2); val cosU2 = cos(u2)
        var lambda = l
        var iter = 0
        var sinSigma: Double; var cosSigma: Double; var sigma: Double; var cosSqAlpha: Double; var cos2SigmaM: Double
        var sinLambda: Double; var cosLambda: Double
        do {
            sinLambda = sin(lambda); cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
            if (sinSigma == 0.0) return 0.0 to 0.0 // same point
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = F / 16 * cosSqAlpha * (4 + F * (4 - 3 * cosSqAlpha))
            val prev = lambda
            lambda = l + (1 - c) * F * sinAlpha * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda) > PI * 1.5) return null
            if (abs(lambda - prev) < 1e-12) break
        } while (++iter < 200)
        if (iter >= 200) return null
        val uSq = cosSqAlpha * (A * A - B * B) / (B * B)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
            bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val s = B * bigA * (sigma - deltaSigma)
        val alpha1 = atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
        return normalize(Math.toDegrees(alpha1)) to s
    }

    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Signed smallest turn from [fromDeg] to [toDeg], in (-180, 180]. Positive = turn clockwise (right). */
    fun turn(fromDeg: Double, toDeg: Double): Double {
        var d = normalize(toDeg - fromDeg)
        if (d > 180.0) d -= 360.0
        return d
    }
}
