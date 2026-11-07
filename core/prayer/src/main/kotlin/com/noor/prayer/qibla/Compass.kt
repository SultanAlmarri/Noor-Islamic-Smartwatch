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

/** How the compass's own figures are shown (0.9.4), so the text stays steady while readings stream in. */
object CompassDisplay {
    /** The sensor's own heading estimate, in steady steps: whole degrees up to 5°, then fives (±7.4° → 5, ±12° → 10). */
    fun displayedEstimate(deg: Double): Int = if (deg < 5.5) kotlin.math.max(1, Math.round(deg).toInt()) else (Math.round(deg / 5.0) * 5).toInt()
}

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
                deviation > CompassTracker.FIELD_POOR -> { worst(CompassQuality.POOR); issues += CompassIssue.MAGNETIC_INTERFERENCE }
                deviation > CompassTracker.FIELD_FAIR -> { worst(CompassQuality.FAIR); issues += CompassIssue.MAGNETIC_INTERFERENCE }
            }
        }
        if (tiltDeg != null && tiltDeg > FLAT_LIMIT_DEG) issues += CompassIssue.NOT_FLAT
        return CompassAssessment(q, issues)
    }

    fun magnitude(v: FloatArray): Double = sqrt(v.take(3).sumOf { (it * it).toDouble() })
}

/**
 * The compass verdict over time (0.9.7), for a screen that shows it live. [CompassAssessor] judges one moment; a reading
 * that sits near one of its limits (the field 15 % off, the sensor's estimate 10°) crosses it back and forth many times a
 * second, and the label flickered Good · Fair · Poor although nothing had changed. Here:
 *
 * - The field strength, the sensor's own accuracy estimate and the tilt are each averaged over about a tenth of a
 *   second (an exponential average on the sensor's own clock): their noise is reduced to about a third, and a real
 *   change still shows within that tenth of a second.
 * - Each measure gets worse at once, at the same limits as [CompassAssessor]; it gets better only once it is clearly
 *   on the better side ([FIELD_FAIR_EXIT], [FIELD_POOR_EXIT], [ACCURACY_GOOD_EXIT], [ACCURACY_FAIR_EXIT], [FLAT_EXIT_DEG]).
 *   There is no timer: a phone taken away from metal reads Good the moment the field is right again.
 * - The sensor's own accuracy estimate is shown in steady steps with the same kind of margin ([shownEstimate]).
 */
class CompassTracker(private val timeConstantMs: Double = 100.0) {
    private val fieldAvg = Average(timeConstantMs)
    private val accuracyAvg = Average(timeConstantMs)
    private val tiltAvg = Average(timeConstantMs)
    private var fieldLevel = 0
    private var accuracyLevel = 0
    private var notFlat = false
    private var estimate: Int? = null

    /** The field strength now, averaged (µT), or null before the first reading. */
    val smoothedField: Double? get() = fieldAvg.value

    /** A magnetometer reading: its strength (µT) and the sensor's timestamp. */
    fun field(microTesla: Double, timestampNs: Long) { fieldAvg.add(microTesla, timestampNs) }

    /** The verdict for a heading reading at [timestampNs] (the sensor's clock), with the latest field strength. */
    fun assess(headingAccuracyDeg: Double?, magnetometerStatus: Int?, expectedFieldMicroTesla: Double?, tiltDeg: Double?, timestampNs: Long): CompassAssessment {
        val accuracy = if (headingAccuracyDeg != null && headingAccuracyDeg >= 0) accuracyAvg.add(headingAccuracyDeg, timestampNs) else { accuracyAvg.reset(); null }
        val tilt = tiltDeg?.let { tiltAvg.add(it, timestampNs) }
        var q = CompassQuality.GOOD
        val issues = mutableSetOf<CompassIssue>()
        fun worst(level: Int) { if (level > q.ordinal) q = CompassQuality.entries[level] }

        if (accuracy != null) {
            accuracyLevel = band(accuracyLevel, accuracy, CompassAssessor.GOOD_ACCURACY_DEG, CompassAssessor.FAIR_ACCURACY_DEG, ACCURACY_GOOD_EXIT, ACCURACY_FAIR_EXIT)
            worst(accuracyLevel)
            if (accuracyLevel > 0) issues += CompassIssue.LOW_SENSOR_ACCURACY
        } else accuracyLevel = 0
        when (magnetometerStatus) {
            CompassAssessor.STATUS_UNRELIABLE -> { worst(2); issues += CompassIssue.SENSOR_UNRELIABLE }
            CompassAssessor.STATUS_LOW -> { worst(2); issues += CompassIssue.LOW_SENSOR_ACCURACY }
            CompassAssessor.STATUS_MEDIUM -> worst(1)
        }
        val f = fieldAvg.value
        if (f != null && expectedFieldMicroTesla != null && expectedFieldMicroTesla > 0) {
            fieldLevel = band(fieldLevel, abs(f - expectedFieldMicroTesla) / expectedFieldMicroTesla, FIELD_FAIR, FIELD_POOR, FIELD_FAIR_EXIT, FIELD_POOR_EXIT)
            worst(fieldLevel)
            if (fieldLevel > 0) issues += CompassIssue.MAGNETIC_INTERFERENCE
        } else fieldLevel = 0
        if (tilt != null) notFlat = if (notFlat) tilt > FLAT_EXIT_DEG else tilt > CompassAssessor.FLAT_LIMIT_DEG
        if (notFlat) issues += CompassIssue.NOT_FLAT
        return CompassAssessment(q, issues)
    }

    /**
     * The sensor's accuracy estimate as shown ([CompassDisplay.displayedEstimate]'s steps), changing only once the
     * estimate is three quarters of a step away from the figure on screen.
     */
    fun shownEstimate(headingAccuracyDeg: Double?): Int? {
        if (headingAccuracyDeg == null || headingAccuracyDeg < 0) { estimate = null; return null }
        val next = CompassDisplay.displayedEstimate(headingAccuracyDeg)
        val cur = estimate
        if (cur == null || next == cur) { estimate = next; return next }
        val step = if (cur < 5 || next < 5) 1.0 else 5.0
        if (abs(headingAccuracyDeg - cur) > 0.75 * step) estimate = next
        return estimate
    }

    /** An exponential average over time, on the sensor's clock, so it does not depend on the sensor's rate. */
    private class Average(private val timeConstantMs: Double) {
        var value: Double? = null; private set
        private var lastNs = -1L
        fun add(v: Double, timestampNs: Long): Double {
            val cur = value
            val next = if (cur == null || lastNs < 0) v else cur + (1 - exp(-((timestampNs - lastNs).coerceAtLeast(0L)) / 1e6 / timeConstantMs)) * (v - cur)
            value = next; lastNs = timestampNs
            return next
        }
        fun reset() { value = null; lastNs = -1L }
    }

    companion object {
        const val FIELD_FAIR = 0.15
        const val FIELD_POOR = 0.25
        /** Back to Good once the field is within 12 % of the expected strength; back to Fair once within 21 %. */
        const val FIELD_FAIR_EXIT = 0.12
        const val FIELD_POOR_EXIT = 0.21
        const val ACCURACY_GOOD_EXIT = 8.5
        const val ACCURACY_FAIR_EXIT = 21.0
        const val FLAT_EXIT_DEG = 25.0

        /** 0 good, 1 fair, 2 poor: worse at once past [fair] or [poor]; better only below [fairExit] or [poorExit]. */
        internal fun band(level: Int, v: Double, fair: Double, poor: Double, fairExit: Double, poorExit: Double): Int {
            val worse = when { v > poor -> 2; v > fair -> 1; else -> 0 }
            if (worse >= level) return worse
            val better = when { v > poorExit -> 2; v > fairExit -> 1; else -> 0 }
            return minOf(level, better)
        }
    }
}
