package com.noor.app.ui.today

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
import com.noor.prayer.PrayerDay
import com.noor.prayer.Schedule
import com.noor.prayer.ScheduleState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class TodayUi(
    val settings: AppSettings,
    val place: ResolvedPlace?,
    val profile: CalculationProfile?,
    val now: Instant,
    val state: ScheduleState?,
    val today: LocalDate?,
    val refreshing: Boolean,
    val lastRefresh: LocationRepository.Result?,
)

/** Calculates any day on demand and remembers recent ones; the per-second clock never recalculates astronomy. */
class DayCache(private val capacity: Int = 96) {
    private var key: Any? = null
    private val map = object : LinkedHashMap<LocalDate, PrayerDay>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, PrayerDay>?) = size > capacity
    }

    @Synchronized
    fun get(s: AppSettings, place: ResolvedPlace, profile: CalculationProfile, date: LocalDate): PrayerDay {
        val k = listOf(place, profile.id, s.asrSchool, s.highLatitudeRule, s.offsets, s.hijriAdjust)
        if (k != key) { map.clear(); key = k }
        return map.getOrPut(date) { PrayerProvider.day(date, place, profile, s) }
    }
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as NoorApplication).container
    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<LocationRepository.Result?>(null)
    private val cache = DayCache()

    private val ticker = flow { while (true) { emit(Instant.now()); delay(1000L - System.currentTimeMillis() % 1000L) } }

    val ui: StateFlow<TodayUi?> = combine(container.settings.settings, ticker, refreshing, lastRefresh) { s, now, r, lr ->
        val place = PrayerProvider.place(s) ?: return@combine TodayUi(s, null, null, now, null, null, r, lr)
        val profile = PrayerProvider.profile(s, place)
        val state = Schedule.state(now, place.zone) { d -> cache.get(s, place, profile, d) }
        TodayUi(s, place, profile, now, state, now.atZone(place.zone).toLocalDate(), r, lr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Times for any date in [DateWindow], for the current place and settings. */
    fun day(u: TodayUi, date: LocalDate): PrayerDay? {
        val place = u.place ?: return null
        val profile = u.profile ?: return null
        return cache.get(u.settings, place, profile, date)
    }

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
