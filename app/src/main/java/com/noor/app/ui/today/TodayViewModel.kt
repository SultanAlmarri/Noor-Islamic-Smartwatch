package com.noor.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.LocationRepository
import com.noor.app.data.PrayerProvider
import com.noor.shared.geo.OfficialTimetables
import com.noor.app.data.ResolvedDay
import com.noor.app.data.ResolvedPlace
import com.noor.prayer.authority.Authority
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
    /** The authority behind the times (null for a manually chosen method without one). */
    val authority: Authority? = null,
    val now: Instant,
    val state: ScheduleState?,
    val today: LocalDate?,
    val refreshing: Boolean,
    val lastRefresh: LocationRepository.Result?,
)

/**
 * Resolves any day on demand (official timetable where the app has one, otherwise calculated) and remembers recent
 * ones; the per-second clock never recalculates astronomy.
 */
class DayCache(private val tables: OfficialTimetables? = null, private val capacity: Int = 96) {
    private var key: Any? = null
    private val map = object : LinkedHashMap<LocalDate, ResolvedDay>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, ResolvedDay>?) = size > capacity
    }

    @Synchronized
    fun resolved(s: AppSettings, place: ResolvedPlace, profile: CalculationProfile, date: LocalDate): ResolvedDay {
        val k = listOf(place, profile.id, s.asrSchool, s.highLatitudeRule, s.offsets, s.hijriAdjust, s.profileId)
        if (k != key) { map.clear(); key = k }
        return map.getOrPut(date) { PrayerProvider.resolve(date, place, profile, s, tables) }
    }

    fun get(s: AppSettings, place: ResolvedPlace, profile: CalculationProfile, date: LocalDate): PrayerDay = resolved(s, place, profile, date).day
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as NoorApplication).container
    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<LocationRepository.Result?>(null)
    private val cache = DayCache(container.timetables)

    private val ticker = flow { while (true) { emit(Instant.now()); delay(1000L - System.currentTimeMillis() % 1000L) } }

    val ui: StateFlow<TodayUi?> = combine(container.settings.settings, ticker, refreshing, lastRefresh) { s, now, r, lr ->
        val place = PrayerProvider.place(s) ?: return@combine TodayUi(s, null, null, null, now, null, null, r, lr)
        val profile = PrayerProvider.profile(s, place)
        val state = Schedule.state(now, place.zone) { d -> cache.get(s, place, profile, d) }
        TodayUi(s, place, profile, PrayerProvider.authority(s, place), now, state, now.atZone(place.zone).toLocalDate(), r, lr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Times for any date in [DateWindow], for the current place and settings, with their source. */
    fun day(u: TodayUi, date: LocalDate): ResolvedDay? {
        val place = u.place ?: return null
        val profile = u.profile ?: return null
        return cache.resolved(u.settings, place, profile, date)
    }

    /** Fetches a newer official timetable when the authority publishes one as open data (at most weekly). */
    fun refreshTimetables() = viewModelScope.launch {
        val s = container.settings.current()
        val place = PrayerProvider.place(s) ?: return@launch
        container.timetableUpdater.refreshIfDue(PrayerProvider.authority(s, place))
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
