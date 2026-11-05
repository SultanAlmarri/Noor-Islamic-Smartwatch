package com.noor.shared.compass

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Surface
import android.view.Display
import com.noor.prayer.GeoPoint
import com.noor.prayer.qibla.CompassAssessment
import com.noor.prayer.qibla.CompassAssessor
import com.noor.prayer.qibla.CompassMath
import com.noor.prayer.qibla.HeadingFilter
import com.noor.prayer.qibla.Qibla
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** One compass reading, already corrected to true north. */
data class CompassReading(
    /** Heading of the screen's top edge (the phone's top, the watch's 12 o'clock), degrees clockwise from true north, smoothed. */
    val trueHeadingDeg: Double,
    /** Magnetic declination applied (east positive), from the World Magnetic Model for this place and date. */
    val declinationDeg: Double,
    /** The sensor's own estimate of heading accuracy, when the device reports one (rotation-vector sensor). */
    val headingAccuracyDeg: Double?,
    val magnetometerStatus: Int?,
    val fieldMicroTesla: Double?,
    val expectedFieldMicroTesla: Double,
    val tiltDeg: Double,
    val assessment: CompassAssessment,
    /** Which sensors produced the heading. */
    val source: Source,
) {
    enum class Source { ROTATION_VECTOR, ACCELEROMETER_MAGNETOMETER }
}

sealed interface CompassAvailability {
    data object Available : CompassAvailability
    /** No magnetometer: the device cannot measure direction at all. */
    data object NoSensor : CompassAvailability
}

/**
 * Wraps Android's motion sensors. Sensors are registered only while the flow is collected (the Qibla screen is on
 * screen), so the compass costs no battery otherwise. [samplingUs] is the rotation-vector rate (the watch asks for a
 * slower one than the phone).
 */
class CompassSensor(private val context: Context, private val samplingUs: Int = SensorManager.SENSOR_DELAY_GAME) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetic: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val availability: CompassAvailability = if (magnetic == null && rotation == null) CompassAvailability.NoSensor else CompassAvailability.Available

    fun readings(at: GeoPoint): Flow<CompassReading> = callbackFlow {
        val geo = GeomagneticField(at.latitude.toFloat(), at.longitude.toFloat(), at.elevationM.toFloat(), System.currentTimeMillis())
        val declination = geo.declination.toDouble()
        val expectedUt = geo.fieldStrength / 1000.0 // nT → µT
        val filter = HeadingFilter()
        val r = FloatArray(9)
        val remapped = FloatArray(9)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        var haveGravity = false; var haveGeomag = false
        var magStatus: Int? = null
        var fieldUt: Double? = null
        var headingAccuracy: Double? = null

        val displays = context.getSystemService(DisplayManager::class.java)
        fun displayRotation(): Int = displays?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

        fun emit(timestamp: Long, source: CompassReading.Source) {
            // Keep the heading tied to the top of the screen even if the screen is rotated.
            val (ax, ay) = when (displayRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(r, ax, ay, remapped)
            val att = CompassMath.attitude(remapped)
            val trueHeading = Qibla.normalize(att.azimuthDeg + declination)
            val smoothed = filter.update(trueHeading, timestamp)
            val assessment = CompassAssessor.assess(headingAccuracy, magStatus, fieldUt, expectedUt, att.tiltDeg)
            trySend(CompassReading(smoothed, declination, headingAccuracy, magStatus, fieldUt, expectedUt, att.tiltDeg, assessment, source))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        // values[4]: estimated heading accuracy in radians, or -1 when unavailable.
                        headingAccuracy = if (e.values.size > 4 && e.values[4] >= 0) Math.toDegrees(e.values[4].toDouble()) else null
                        emit(e.timestamp, CompassReading.Source.ROTATION_VECTOR)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(e.values, 0, geomag, 0, 3); haveGeomag = true
                        fieldUt = CompassAssessor.magnitude(e.values)
                        magStatus = e.accuracy
                        if (rotation == null && haveGravity && SensorManager.getRotationMatrix(r, null, gravity, geomag)) {
                            emit(e.timestamp, CompassReading.Source.ACCELEROMETER_MAGNETOMETER)
                        }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Low-pass the accelerometer to approximate gravity for the fallback path.
                        if (!haveGravity) System.arraycopy(e.values, 0, gravity, 0, 3)
                        else for (i in 0..2) gravity[i] += 0.15f * (e.values[i] - gravity[i])
                        haveGravity = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magStatus = accuracy
            }
        }

        rotation?.let { sm.registerListener(listener, it, samplingUs) }
        magnetic?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotation == null) accel?.let { sm.registerListener(listener, it, samplingUs) }
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()
}
