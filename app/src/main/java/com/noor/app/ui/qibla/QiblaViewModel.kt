package com.noor.app.ui.qibla

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ResolvedPlace
import com.noor.app.data.compass.CompassReading
import com.noor.app.data.compass.CompassSensor
import com.noor.prayer.qibla.Qibla
import com.noor.prayer.qibla.QiblaResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class QiblaPlace(val settings: AppSettings, val place: ResolvedPlace?, val qibla: QiblaResult?)

class QiblaViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    val sensor = CompassSensor(app)

    val place: Flow<QiblaPlace> = container.settings.settings.map { s ->
        val p = PrayerProvider.place(s)
        QiblaPlace(s, p, p?.let { Qibla.from(it.point) })
    }

    /** Sensor readings; collected only while the screen is visible, which is when the sensors are registered. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val readings: Flow<CompassReading?> = container.settings.settings
        .map { PrayerProvider.place(it)?.point }
        .distinctUntilChanged()
        .flatMapLatest { point -> if (point == null) emptyFlow() else sensor.readings(point) }
}
