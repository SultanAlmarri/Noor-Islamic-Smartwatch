package com.noor.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noor.prayer.AsrSchool
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_settings")

/** All user settings, persisted on the device with DataStore. */
class SettingsRepository(private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val locationMode = stringPreferencesKey("location_mode")
        val manualCity = stringPreferencesKey("manual_city")
        val manualPlace = stringPreferencesKey("manual_place")
        val lastFull = stringPreferencesKey("last_location_v2")
        val background = booleanPreferencesKey("background_updates")
        val lat = doublePreferencesKey("last_lat")
        val lon = doublePreferencesKey("last_lon")
        val elev = doublePreferencesKey("last_elev")
        val zone = stringPreferencesKey("last_zone")
        val place = stringPreferencesKey("last_place")
        val country = stringPreferencesKey("last_country")
        val region = stringPreferencesKey("last_region")
        val fixedAt = longPreferencesKey("last_fixed_at")
        val profile = stringPreferencesKey("profile_id")
        val asr = stringPreferencesKey("asr_school")
        val highLat = stringPreferencesKey("high_latitude")
        val language = stringPreferencesKey("language")
        val numerals = stringPreferencesKey("numerals")
        val timeFormat = stringPreferencesKey("time_format")
        val theme = stringPreferencesKey("theme")
        val hijri = intPreferencesKey("hijri_adjust")
        val alerts = stringPreferencesKey("alerts_v1")
        val status = booleanPreferencesKey("status_notification")
        val elapsed = booleanPreferencesKey("elapsed_after_prayer")
        val scenery = stringPreferencesKey("scenery")
        fun offset(p: Prayer) = intPreferencesKey("offset_${p.name.lowercase()}")
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val last = p[K.lastFull]?.let(KnownLocation::decode) ?: if (p[K.lat] != null && p[K.lon] != null) KnownLocation(
            point = GeoPoint(p[K.lat]!!, p[K.lon]!!, p[K.elev] ?: 0.0),
            zoneId = p[K.zone] ?: java.time.ZoneId.systemDefault().id,
            placeName = p[K.place], countryCode = p[K.country], regionCode = p[K.region],
            fixedAtEpochMs = p[K.fixedAt] ?: 0L,
        ) else null
        AppSettings(
            onboardingDone = p[K.onboarding] ?: false,
            locationMode = p.enum(K.locationMode, LocationMode.AUTO),
            manualCityId = p[K.manualCity],
            manualPlace = p[K.manualPlace]?.let(KnownLocation::decode),
            backgroundUpdates = p[K.background] ?: false,
            lastLocation = last,
            profileId = p[K.profile],
            asrSchool = p[K.asr]?.let { v -> AsrSchool.entries.firstOrNull { it.name == v } },
            highLatitudeRule = p.enum(K.highLat, HighLatitudeRule.ANGLE_BASED),
            offsets = Prayer.entries.associateWith { p[K.offset(it)] ?: 0 }.filterValues { it != 0 },
            language = p.enum(K.language, Language.SYSTEM),
            numerals = p.enum(K.numerals, Numerals.WESTERN),
            timeFormat = p.enum(K.timeFormat, TimeFormat.H12),
            theme = p.enum(K.theme, ThemeMode.SUN),
            hijriAdjust = p[K.hijri] ?: 0,
            alerts = AlertSettings.decode(p[K.alerts]),
            statusNotification = p[K.status] ?: false,
            elapsedAfterPrayer = p[K.elapsed] ?: true,
            scenery = p.enum(K.scenery, Scenery.AUTO),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true }
    suspend fun setLocationMode(mode: LocationMode) = context.dataStore.edit { it[K.locationMode] = mode.name }
    suspend fun setManualCity(id: String) = context.dataStore.edit {
        it[K.manualCity] = id; it.remove(K.manualPlace); it[K.locationMode] = LocationMode.MANUAL.name
    }
    suspend fun setManualPlace(place: KnownLocation) = context.dataStore.edit {
        it[K.manualPlace] = place.encode(); it[K.locationMode] = LocationMode.MANUAL.name
    }
    suspend fun setBackgroundUpdates(on: Boolean) = context.dataStore.edit { it[K.background] = on }
    suspend fun setProfile(id: String?) = context.dataStore.edit { if (id == null) it.remove(K.profile) else it[K.profile] = id }
    suspend fun setAsrSchool(s: AsrSchool?) = context.dataStore.edit { if (s == null) it.remove(K.asr) else it[K.asr] = s.name }
    suspend fun setHighLatitudeRule(r: HighLatitudeRule) = context.dataStore.edit { it[K.highLat] = r.name }
    suspend fun setOffset(p: Prayer, minutes: Int) = context.dataStore.edit { it[K.offset(p)] = minutes.coerceIn(-15, 15) }
    suspend fun resetOffsets() = context.dataStore.edit { e -> Prayer.entries.forEach { e.remove(K.offset(it)) } }
    suspend fun setLanguage(l: Language) = context.dataStore.edit { it[K.language] = l.name }
    suspend fun setNumerals(n: Numerals) = context.dataStore.edit { it[K.numerals] = n.name }
    suspend fun setTimeFormat(f: TimeFormat) = context.dataStore.edit { it[K.timeFormat] = f.name }
    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[K.theme] = t.name }
    suspend fun setStatusNotification(on: Boolean) = context.dataStore.edit { it[K.status] = on }
    suspend fun setElapsedAfterPrayer(on: Boolean) = context.dataStore.edit { it[K.elapsed] = on }
    suspend fun setScenery(v: Scenery) = context.dataStore.edit { it[K.scenery] = v.name }
    suspend fun setHijriAdjust(d: Int) = context.dataStore.edit { it[K.hijri] = d.coerceIn(-2, 2) }

    /** Read-modify-write in one transaction, so quick successive changes never overwrite each other. */
    suspend fun updateAlerts(change: (AlertSettings) -> AlertSettings) = context.dataStore.edit {
        it[K.alerts] = change(AlertSettings.decode(it[K.alerts])).encode()
    }

    suspend fun saveLocation(l: KnownLocation) = context.dataStore.edit {
        it[K.lat] = l.point.latitude
        it[K.lon] = l.point.longitude
        it[K.elev] = l.point.elevationM
        it[K.zone] = l.zoneId
        it[K.fixedAt] = l.fixedAtEpochMs
        if (l.placeName != null) it[K.place] = l.placeName else it.remove(K.place)
        if (l.countryCode != null) it[K.country] = l.countryCode else it.remove(K.country)
        if (l.regionCode != null) it[K.region] = l.regionCode else it.remove(K.region)
        it[K.lastFull] = l.encode()
    }
}
