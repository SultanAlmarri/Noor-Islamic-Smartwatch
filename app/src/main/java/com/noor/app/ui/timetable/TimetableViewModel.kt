package com.noor.app.ui.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ResolvedPlace
import com.noor.app.ui.today.DayCache
import com.noor.prayer.CalculationProfile
import com.noor.prayer.Prayer
import com.noor.prayer.Schedule
import com.noor.prayer.authority.Authority
import com.noor.shared.geo.OfficialTimetables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * Where the page gets a month's times: at once when the month has been worked out already, otherwise off the main
 * thread. [basis] changes when anything the times depend on does (the place, the method, an adjustment, a newer
 * official timetable): the page then asks again.
 */
interface TableSource {
    val basis: Any
    fun cached(month: TableMonth): Table?
    suspend fun load(month: TableMonth): Table
}

/** What the timetable page stands on: the place and what its times follow, today's date there and the next prayer. */
data class TimetableUi(
    val settings: AppSettings,
    val place: ResolvedPlace?,
    val profile: CalculationProfile?,
    val authority: Authority?,
    /** Today at the place; the row that is lit. */
    val today: LocalDate?,
    /** The next prayer and its day (tomorrow's Fajr after Isha): the cell in the accent. */
    val next: Pair<LocalDate, Prayer>?,
    val tablesVersion: Int,
)

/**
 * A month at a time, worked out once and kept ([TableSource]): the months lately seen come back at once. Each day is
 * the home screen's ([Timetables.build]).
 */
class MonthStore(
    private val place: ResolvedPlace, private val profile: CalculationProfile, private val settings: AppSettings,
    private val tables: OfficialTimetables?, private val capacity: Int = 36,
) : TableSource {
    override val basis: Any = Timetables.basis(place, profile, settings, tables?.version)
    private val map = object : LinkedHashMap<TableMonth, Table>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TableMonth, Table>?) = size > capacity
    }

    override fun cached(month: TableMonth): Table? = synchronized(map) { map[month] }

    override suspend fun load(month: TableMonth): Table = cached(month) ?: withContext(Dispatchers.Default) {
        Timetables.build(month, place, profile, settings, tables).also { synchronized(map) { map[month] = it } }
    }
}

class TimetableViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    /** The home screen's own cache of days, for today's date and the next prayer. */
    private val days = DayCache(container.timetables)

    /** The moment, each minute on the minute (the next prayer changes on a minute; nothing here counts seconds). */
    private val minutes = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 40L)
        }
    }

    val ui: StateFlow<TimetableUi?> = combine(container.settings.settings, minutes) { s, now ->
        val place = PrayerProvider.place(s) ?: return@combine TimetableUi(s, null, null, null, null, null, 0)
        val profile = PrayerProvider.profile(s, place)
        val state = Schedule.state(now, place.zone) { d -> days.get(s, place, profile, d) }
        TimetableUi(s, place, profile, PrayerProvider.authority(s, place), now.atZone(place.zone).toLocalDate(),
            state.next.date to state.next.prayer, container.timetables.version)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var store: MonthStore? = null

    /** The months for what [u] stands on; the same store (and its months) while that does not change. */
    @Synchronized
    fun source(u: TimetableUi): TableSource? {
        val place = u.place ?: return null
        val profile = u.profile ?: return null
        val basis = Timetables.basis(place, profile, u.settings, container.timetables.version)
        return store?.takeIf { it.basis == basis } ?: MonthStore(place, profile, u.settings, container.timetables).also { store = it }
    }
}
