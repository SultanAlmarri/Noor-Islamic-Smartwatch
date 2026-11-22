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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_settings")

/** All user settings, persisted on the device with DataStore. */
class SettingsRepository(
    private val context: Context,
    /** (0.18.2) Where the decoded settings are shared from (the app's own scope; tests may leave the default). */
    scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
) {
    /** 0.18: whether Noor's library has a sound (old recordings were replaced by the owner's five voices and five effects). */
    private val known: (String) -> Boolean = { id -> runCatching { com.noor.alerts.SoundLibrary.get(context).byId(id) != null }.getOrDefault(true) }


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
        val statusChip = booleanPreferencesKey("status_bar_chip")
        val statusChipIcon = booleanPreferencesKey("status_chip_icon")
        val statusChipSystem = booleanPreferencesKey("status_chip_system_timer")
        val elapsed = booleanPreferencesKey("elapsed_after_prayer")
        val scrubHint = booleanPreferencesKey("hint_scrub_done")
        val newsSeen = androidx.datastore.preferences.core.intPreferencesKey("news_seen")
        val scenery = stringPreferencesKey("scenery")
        val skyGraphics = stringPreferencesKey("sky_graphics")
        /** (0.19) The graphics level (SkyGraphics); "sky_graphics" is only read to carry an older choice over. */
        val skyLevel = stringPreferencesKey("sky_level")
        val seasons = booleanPreferencesKey("scenery_seasons")
        /** (0.23) The Sun's path (SunPathMode). */
        val sunPath = stringPreferencesKey("sun_path")
        val trueStars = booleanPreferencesKey("scenery_true_stars")
        fun offset(p: Prayer) = intPreferencesKey("offset_${p.name.lowercase()}")
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private val decoded: Flow<AppSettings> = context.dataStore.data.map { p ->
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
            alerts = phoneAlerts(AlertSettings.decode(p[K.alerts]), known),
            statusNotification = p[K.status] ?: true,
            statusBarChip = p[K.statusChip] ?: true,
            statusChipIcon = p[K.statusChipIcon] ?: false,
            statusChipSystemTimer = p[K.statusChipSystem] ?: false,
            elapsedAfterPrayer = p[K.elapsed] ?: true,
            scrubHintDone = p[K.scrubHint] ?: false,
            newsSeen = p[K.newsSeen] ?: 0,
            scenery = p.enum(K.scenery, Scenery.AUTO),
            skyGraphics = p[K.skyLevel]?.let { v -> SkyGraphics.entries.firstOrNull { it.name == v } } ?: SkyGraphics.fromOld(p[K.skyGraphics]),
            sunPath = p.enum(K.sunPath, SunPathMode.SIMPLE),
            seasons = p[K.seasons] ?: false,
            trueStars = p[K.trueStars] ?: false,
        )
    }

    /**
     * (0.18.2) The settings as they change, decoded once, off the main thread, and shared by everyone who watches them.
     * Until 0.18.1 each watcher (the whole app's screen, every page's view model) decoded every change itself, on the
     * main thread, the alerts and the sound library's list among it.
     */
    val settings: Flow<AppSettings> = decoded.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
        .shareIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    /** The settings now, read from the store itself (so a change just written is always seen). */
    suspend fun current(): AppSettings = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decoded.first() }

    // (0.25: someone who has just been welcomed is not then told what is new.)
    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true; it[K.newsSeen] = com.noor.app.ui.components.WhatsNew.EDITION }
    suspend fun setNewsSeen(edition: Int) = context.dataStore.edit { it[K.newsSeen] = edition }
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
    suspend fun setStatusBarChip(on: Boolean) = context.dataStore.edit { it[K.statusChip] = on }
    suspend fun setStatusChipIcon(on: Boolean) = context.dataStore.edit { it[K.statusChipIcon] = on }
    suspend fun setStatusChipSystemTimer(on: Boolean) = context.dataStore.edit { it[K.statusChipSystem] = on }
    suspend fun setElapsedAfterPrayer(on: Boolean) = context.dataStore.edit { it[K.elapsed] = on }
    suspend fun setScrubHintDone() = context.dataStore.edit { it[K.scrubHint] = true }
    suspend fun setScenery(v: Scenery) = context.dataStore.edit { it[K.scenery] = v.name }
    suspend fun setSkyGraphics(v: SkyGraphics) = context.dataStore.edit { it[K.skyLevel] = v.name }
    suspend fun setSunPath(v: SunPathMode) = context.dataStore.edit { it[K.sunPath] = v.name }
    suspend fun setSeasons(on: Boolean) = context.dataStore.edit { it[K.seasons] = on }
    suspend fun setTrueStars(on: Boolean) = context.dataStore.edit { it[K.trueStars] = on }
    suspend fun setHijriAdjust(d: Int) = context.dataStore.edit { it[K.hijri] = d.coerceIn(-2, 2) }

    /** Read-modify-write in one transaction, so quick successive changes never overwrite each other. */
    suspend fun updateAlerts(change: (AlertSettings) -> AlertSettings) = context.dataStore.edit {
        it[K.alerts] = change(phoneAlerts(AlertSettings.decode(it[K.alerts]), known)).encode()
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

/**
 * The phone's alert settings as read: 0.17, reminders before and after a prayer are alarms now
 * ([AlertSettings.remindersAsAlarms]); 0.18, sounds no longer in the library become their kind's default (a prayer set
 * to a sound, which 0.16 turned into a notification, plays the sound effects again).
 */
internal fun phoneAlerts(a: AlertSettings, known: (String) -> Boolean = { true }): AlertSettings {
    val ids = com.noor.prayer.alerts.SoundIds
    // 0.18: a sound no longer in the library (the recordings before 0.18, the tones before 0.16) becomes the default
    // of its kind: the Adhan's default voice, Fajr's own when there is one, the birds for a sound.
    fun keep(id: String?) = id == null || ids.isCustom(id) || id == ids.RANDOM || known(id)
    val adhan = ids.DEFAULT_ADHAN
    val fajr = ids.DEFAULT_FAJR_ADHAN.takeIf(known) ?: adhan
    var s = com.noor.prayer.Prayer.entries.fold(a.remindersAsAlarms()) { acc, p ->
        val x = acc.alert(p)
        if (keep(x.soundId)) acc else acc.with(p, x.copy(soundId = when {
            x.mode == com.noor.prayer.alerts.AlertMode.SOUND || p == com.noor.prayer.Prayer.SHURUQ -> ids.DEFAULT_SOUND
            p == com.noor.prayer.Prayer.FAJR -> fajr
            else -> adhan
        }))
    }
    if (s.favourites.any { !known(it) }) s = s.copy(favourites = s.favourites.filter(known))
    s.alarms.filter { it.sound == com.noor.prayer.alerts.AlarmSound.LIBRARY && !keep(it.customId) }.forEach { al ->
        s = s.withAlarm(al.copy(sound = com.noor.prayer.alerts.AlarmSound.PHONE_ALARM, customId = null))
    }
    return s
}
