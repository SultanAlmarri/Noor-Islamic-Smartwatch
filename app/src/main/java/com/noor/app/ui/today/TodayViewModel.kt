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
import kotlinx.coroutines.flow.flowOn
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
    /** (0.21) The official timetables' version: the days on show are worked out again when a newer table is stored. */
    val tablesVersion: Int = 0,
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
        // (0.21: and the timetables' version: a newer table downloaded while the app runs is used at once. Until 0.20 the
        // days already worked out from the old one stayed until the settings changed or the app was closed.)
        val k = listOf(place, profile.id, s.asrSchool, s.highLatitudeRule, s.offsets, s.hijriAdjust, s.profileId, tables?.version)
        if (k != key) { map.clear(); key = k }
        return map.getOrPut(date) { PrayerProvider.resolve(date, place, profile, s, tables) }
    }

    fun get(s: AppSettings, place: ResolvedPlace, profile: CalculationProfile, date: LocalDate): PrayerDay = resolved(s, place, profile, date).day
}

/**
 * (0.18) The day's sun paths (241 positions of the Sun each, by the prayer engine's own solar position code), worked
 * out once, off the main thread where it can be ([TodayViewModel] does it with the day's times), and shared. Until
 * 0.17 the home screen worked its path out on the main thread as it first drew, and again at every return to it.
 */
internal object SunPaths {
    private val map = LinkedHashMap<Triple<LocalDate, com.noor.prayer.GeoPoint, java.time.ZoneId>, com.noor.prayer.astro.SunPath>()

    @Synchronized
    fun of(date: LocalDate, point: com.noor.prayer.GeoPoint, zone: java.time.ZoneId): com.noor.prayer.astro.SunPath {
        val k = Triple(date, point, zone)
        map[k]?.let { return it }
        val p = com.noor.prayer.astro.SunPath.of(date, point, zone)
        map[k] = p
        while (map.size > 4) map.remove(map.keys.first())
        return p
    }
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as NoorApplication).container
    private val refreshing = MutableStateFlow(false)
    private val lastRefresh = MutableStateFlow<LocationRepository.Result?>(null)
    private val cache = DayCache(container.timetables)

    /**
     * The moment, each second. (0.24, the owner: the countdown must change exactly with the phone's clock.) Each second is
     * given a little ahead of its start ([TICK_LEAD_MS]), as the instant just after it: the countdown's digits roll into
     * place (NoorMotion.Standard), and the roll, begun that much early, is half done, the new digit read, on the second.
     */
    private val ticker = flow {
        emit(Instant.now())
        var last = System.currentTimeMillis() / 1000L * 1000L
        while (true) {
            val now = System.currentTimeMillis()
            val next = maxOf((now / 1000L + 1) * 1000L, last + 1000L)
            // (Without animations the digits change at once: on the second itself.)
            val lead = if (com.noor.app.ui.components.animationsOn(getApplication())) TICK_LEAD_MS else 0L
            delay(maxOf(0L, next - lead - now))
            last = next
            emit(Instant.ofEpochMilli(next + 1))
        }
    }

    val ui: StateFlow<TodayUi?> = combine(container.settings.settings, ticker, refreshing, lastRefresh) { s, now, r, lr ->
        val place = PrayerProvider.place(s) ?: return@combine TodayUi(s, null, null, null, now, null, null, r, lr)
        val profile = PrayerProvider.profile(s, place)
        val state = Schedule.state(now, place.zone) { d -> cache.get(s, place, profile, d) }
        val today = now.atZone(place.zone).toLocalDate()
        // (0.18) The home screen's day is worked out here, off the main thread: its sun path, and the days on either
        // side that the list of times can be swiped to.
        SunPaths.of(today, place.point, place.zone)
        cache.get(s, place, profile, today.minusDays(1)); cache.get(s, place, profile, today.plusDays(1))
        TodayUi(s, place, profile, PrayerProvider.authority(s, place), now, state, today, r, lr, container.timetables.version)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /**
     * A prayer's alert on or off from its bell (0.15). With the alerts off altogether, turning one on turns them on
     * for that prayer alone, as on the Adhan tab.
     */
    fun setAlert(p: com.noor.prayer.Prayer, on: Boolean) {
        viewModelScope.launch {
            container.settings.updateAlerts { s ->
                if (on && !s.enabled) com.noor.prayer.Prayer.entries.fold(s.copy(enabled = true)) { x, q -> x.with(q, x.alert(q).copy(enabled = q == p)) }
                else s.with(p, s.alert(p).copy(enabled = on))
            }
        }
    }

    /** The sky has been drawn along: its hint has done its work (0.15). */
    fun scrubLearned() { viewModelScope.launch { container.settings.setScrubHintDone() } }

    fun refresh() = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        lastRefresh.value = container.location.refresh()
        refreshing.value = false
    }
}

/** How long before each second the home screen is given it (TodayViewModel's ticker): about the countdown roll's first half. */
internal const val TICK_LEAD_MS = 110L
