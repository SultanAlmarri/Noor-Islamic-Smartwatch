package com.noor.prayer.qibla

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Device attitude from a 3x3 rotation matrix (row-major, device → world, as Android's SensorManager produces). */
data class Attitude(val azimuthDeg: Double, val pitchDeg: Double, val rollDeg: Double, val tiltDeg: Double)

object CompassMath {
    /** Same convention as SensorManager.getOrientation; azimuth is clockwise from magnetic north, [0, 360). */
    fun attitude(r: FloatArray): Attitude {
        require(r.size == 9)
        val azimuth = Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble()))
        val pitch = Math.toDegrees(asin((-r[7]).toDouble().coerceIn(-1.0, 1.0)))
        val roll = Math.toDegrees(atan2((-r[6]).toDouble(), r[8].toDouble()))
        // Angle between the screen's normal and the vertical: 0 = lying flat face up.
        val tilt = Math.toDegrees(acos(r[8].toDouble().coerceIn(-1.0, 1.0)))
        return Attitude(Qibla.normalize(azimuth), pitch, roll, tilt)
    }
}

/**
 * Smooths a heading without the 359°→0° jump: averages the direction as a unit vector, with a time constant so the
 * result does not depend on the sensor rate.
 */
class HeadingFilter(private val timeConstantMs: Double = 180.0) {
    private var x = 0.0
    private var y = 0.0
    private var lastNs = -1L

    fun update(headingDeg: Double, timestampNs: Long): Double {
        val r = Math.toRadians(headingDeg)
        if (lastNs < 0) { x = cos(r); y = sin(r) } else {
            val dtMs = ((timestampNs - lastNs).coerceAtLeast(0L)) / 1e6
            val a = 1 - exp(-dtMs / timeConstantMs)
            x += a * (cos(r) - x); y += a * (sin(r) - y)
        }
        lastNs = timestampNs
        return Qibla.normalize(Math.toDegrees(atan2(y, x)))
    }

    fun reset() { lastNs = -1L }
}

enum class CompassQuality { GOOD, FAIR, POOR }

enum class CompassIssue { SENSOR_UNRELIABLE, LOW_SENSOR_ACCURACY, MAGNETIC_INTERFERENCE, NOT_FLAT }

data class CompassAssessment(val quality: CompassQuality, val issues: Set<CompassIssue>)

/**
 * Turns what the sensors report into a verdict, without inventing precision:
 * - the rotation-vector sensor's own estimate of heading accuracy (when the phone provides one),
 * - the magnetometer's accuracy status (Android's SENSOR_STATUS_* 0..3),
 * - the measured field strength against the strength the World Magnetic Model expects at this place,
 * - how far the phone is from lying flat.
 */
object CompassAssessor {
    const val STATUS_UNRELIABLE = 0
    const val STATUS_LOW = 1
    const val STATUS_MEDIUM = 2
    const val STATUS_HIGH = 3

    const val GOOD_ACCURACY_DEG = 10.0
    const val FAIR_ACCURACY_DEG = 25.0
    const val FLAT_LIMIT_DEG = 30.0

    fun assess(
        headingAccuracyDeg: Double?,
        magnetometerStatus: Int?,
        fieldMicroTesla: Double?,
        expectedFieldMicroTesla: Double?,
        tiltDeg: Double?,
    ): CompassAssessment {
        var q = CompassQuality.GOOD
        val issues = mutableSetOf<CompassIssue>()
        fun worst(level: CompassQuality) { if (level.ordinal > q.ordinal) q = level }

        if (headingAccuracyDeg != null && headingAccuracyDeg >= 0) {
            when {
                headingAccuracyDeg > FAIR_ACCURACY_DEG -> { worst(CompassQuality.POOR); issues += CompassIssue.LOW_SENSOR_ACCURACY }
                headingAccuracyDeg > GOOD_ACCURACY_DEG -> { worst(CompassQuality.FAIR); issues += CompassIssue.LOW_SENSOR_ACCURACY }
            }
        }
        when (magnetometerStatus) {
            STATUS_UNRELIABLE -> { worst(CompassQuality.POOR); issues += CompassIssue.SENSOR_UNRELIABLE }
            STATUS_LOW -> { worst(CompassQuality.POOR); issues += CompassIssue.LOW_SENSOR_ACCURACY }
            STATUS_MEDIUM -> worst(CompassQuality.FAIR)
        }
        if (fieldMicroTesla != null && expectedFieldMicroTesla != null && expectedFieldMicroTesla > 0) {
            val deviation = abs(fieldMicroTesla - expectedFieldMicroTesla) / expectedFieldMicroTesla
            when {
                deviation > 0.25 -> { worst(CompassQuality.POOR); issues += CompassIssue.MAGNETIC_INTERFERENCE }
                deviation > 0.15 -> { worst(CompassQuality.FAIR); issues += CompassIssue.MAGNETIC_INTERFERENCE }
            }
        }
        if (tiltDeg != null && tiltDeg > FLAT_LIMIT_DEG) issues += CompassIssue.NOT_FLAT
        return CompassAssessment(q, issues)
    }

    fun magnitude(v: FloatArray): Double = sqrt(v.take(3).sumOf { (it * it).toDouble() })
}
