package com.noor.app.ui.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.FoundPlace
import com.noor.app.data.PickedPlace
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ResolvedPlace
import com.noor.prayer.GeoPoint
import com.noor.prayer.PrayerDay
import com.noor.prayer.geo.Coordinates
import com.noor.prayer.geo.WorldPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** What the search box finds: the offline list at once, the phone's geocoder a moment later, and typed coordinates. */
data class SearchState(
    val query: String = "",
    val coordinates: GeoPoint? = null,
    val offline: List<WorldPlaces.Place> = emptyList(),
    val online: List<FoundPlace> = emptyList(),
    val searchingOnline: Boolean = false,
)

/**
 * A place about to be chosen, with what its times will be (computed exactly as the app will compute them). [city]: for
 * a point (the map, coordinates) that took its name from a district of the offline list, the city that district is in,
 * so the confirmation leads with the city and gives the district under it (0.25, second review: the Kaaba's coordinates
 * were titled "Ḩārat al Bāb wa ash Shāmīyah"). Display only: what is saved is [picked], as before.
 */
data class Preview(val picked: PickedPlace, val day: PrayerDay?, val official: Boolean, val methodEn: String, val methodAr: String,
                   val city: WorldPlaces.Place? = null)

class LocationViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    val store = container.world
    val settings: StateFlow<AppSettings?> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val world = MutableStateFlow<WorldPlaces?>(null)
    val search = MutableStateFlow(SearchState())
    val preview = MutableStateFlow<Preview?>(null)
    /** True while a picked place is being named or its times computed. */
    val resolving = MutableStateFlow(false)

    init { viewModelScope.launch { world.value = store.places() } }

    private var searchJob: Job? = null

    fun setQuery(q: String, arabic: Boolean) {
        search.value = search.value.copy(query = q, coordinates = Coordinates.parse(q))
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            val w = world.value ?: store.places().also { world.value = it }
            val offline = withContext(Dispatchers.Default) { w.search(q, 60) }
            search.value = search.value.copy(offline = offline, online = emptyList(), searchingOnline = q.trim().length >= 3 && search.value.coordinates == null)
            if (q.trim().length < 3 || search.value.coordinates != null) return@launch
            delay(650)
            val online = runCatching { store.searchOnline(q, arabic) }.getOrDefault(emptyList())
            // Online results that are the same place as an offline one (within 1 km, same name) are not repeated.
            val fresh = online.filter { o -> offline.none { it.name.equals(o.title, true) && com.noor.prayer.geo.Gazetteer.distanceKm(it.point, o.point) < 1.0 } }
            search.value = search.value.copy(online = fresh, searchingOnline = false)
        }
    }

    fun pick(p: WorldPlaces.Place) = resolve(pinned = false) { store.picked(p) }
    fun pin(point: GeoPoint, title: String? = null) = resolve(pinned = title == null) { store.pinned(point, title) }

    private var resolveJob: Job? = null

    /**
     * Only the place asked for last is resolved (0.14): an earlier one still being named (a slow geocoder) is given up,
     * so it can no longer arrive late and put itself under the confirmation of the place chosen after it.
     */
    private fun resolve(pinned: Boolean, block: suspend () -> PickedPlace) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            resolving.value = true
            preview.value = null
            try {
                val picked = block()
                preview.value = previewOf(picked).let { if (pinned) it.copy(city = cityOfDistrict(picked)) else it }
            } finally {
                if (resolveJob == coroutineContext[Job]) resolving.value = false
            }
        }
    }

    private suspend fun previewOf(p: PickedPlace): Preview = withContext(Dispatchers.Default) {
        val s = settings.value ?: container.settings.current()
        val place = ResolvedPlace(p.point, ZoneId.of(p.zoneId), p.nameEn, p.nameAr, false, p.countryCode, p.regionCode, null, p.contextEn, p.contextAr, true)
        val profile = PrayerProvider.profile(s, place)
        val auth = PrayerProvider.authority(s, place)
        val day = runCatching { PrayerProvider.resolve(LocalDate.now(place.zone), place, profile, s, container.timetables) }.getOrNull()
        Preview(p, day?.day, day?.official == true, auth?.nameEn ?: profile.nameEn, auth?.nameAr ?: profile.nameAr)
    }

    /**
     * The city of a pinned point named after a district of the offline list (the name [WorldStore.pinned] gives when
     * the phone's geocoder has none): the district must be the very place nearest the point, as it was when named.
     */
    private suspend fun cityOfDistrict(p: PickedPlace): WorldPlaces.Place? = withContext(Dispatchers.Default) {
        val w = world.value ?: store.places()
        val district = w.nearest(p.point, 25.0)?.takeIf { it.neighbourhood && it.name == p.nameEn } ?: return@withContext null
        w.cityOf(p.point, district.countryCode)?.takeIf { it.row != district.row && it.name != district.name }
    }

    fun clearPreview() { resolveJob?.cancel(); preview.value = null }

    fun save(p: PickedPlace, done: () -> Unit) = viewModelScope.launch {
        container.settings.setManualPlace(p.toKnown())
        done()
    }

    fun hasLocationPermission() = container.location.hasForegroundPermission()
    /** Automatic mode at once; the first fix arrives in the background (the home screen shows it when it comes). */
    fun useAutomatic(done: () -> Unit) = viewModelScope.launch {
        container.settings.setLocationMode(com.noor.app.data.LocationMode.AUTO)
        container.scope.launch { runCatching { container.location.refresh() } }
        done()
    }

    /** The phone's last known position, for the map's "my location" (null without permission or a fix). */
    suspend fun lastPosition(): GeoPoint? = runCatching {
        if (!container.location.hasForegroundPermission()) return null
        @Suppress("MissingPermission")
        val l = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(getApplication<Application>()).lastLocation.await()
        l?.let { GeoPoint(it.latitude, it.longitude) }
    }.getOrNull()
}
