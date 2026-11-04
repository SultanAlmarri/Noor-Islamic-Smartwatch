package com.noor.app.ui.prayers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.LocationRepository
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ResolvedPlace
import com.noor.prayer.CalculationProfile
import com.noor.prayer.HijriDate
import com.noor.prayer.PrayerDay
import com.noor.prayer.Schedule
import com.noor.prayer.ScheduleState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class PrayersUi(
    val settings: AppSettings,
    val place: ResolvedPlace?,
    val profile: CalculationProfile?,
    val state: ScheduleState?,
    val hijri: HijriDate?,
    val today: LocalDate?,
    val refreshing: Boolean,
    val lastRefresh: LocationRepository.Result?,
)

class PrayersViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<LocationRepository.Result?>(null)

    private val ticker = flow { while (true) { emit(Instant.now()); delay(1000L - System.currentTimeMillis() % 1000L) } }

    /** Days are cached by (date, inputs) so the per-second ticker never recomputes the astronomy. */
    private var cacheKey: Any? = null
    private val cache = HashMap<LocalDate, PrayerDay>()

    val ui: StateFlow<PrayersUi?> = combine(container.settings.settings, ticker, refreshing, lastRefresh) { s, now, r, lr ->
        val place = PrayerProvider.place(s)
        if (place == null) return@combine PrayersUi(s, null, null, null, null, null, r, lr)
        val profile = PrayerProvider.profile(s, place)
        val key = listOf(place, profile.id, s.asrSchool, s.highLatitudeRule, s.offsets, s.hijriAdjust)
        if (key != cacheKey) { cache.clear(); cacheKey = key }
        val state = Schedule.state(now, place.zone) { d -> cache.getOrPut(d) { PrayerProvider.day(d, place, profile, s) } }
        val today = now.atZone(place.zone).toLocalDate()
        PrayersUi(s, place, profile, state, HijriDate.of(today, s.hijriAdjust), today, r, lr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Refresh on open when automatic and the last fix is older than 30 minutes. */
    fun refreshIfStale() = viewModelScope.launch {
        val s = container.settings.current()
        if (s.locationMode != LocationMode.AUTO) return@launch
        val age = System.currentTimeMillis() - (s.lastLocation?.fixedAtEpochMs ?: 0L)
        if (age > 30 * 60_000L) refresh()
    }

    fun refresh() = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        lastRefresh.value = container.location.refresh()
        refreshing.value = false
    }
}
