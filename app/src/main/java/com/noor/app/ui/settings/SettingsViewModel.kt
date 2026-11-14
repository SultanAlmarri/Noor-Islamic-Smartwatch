package com.noor.app.ui.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.LocationWorker
import com.noor.app.data.Numerals
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.prayer.AsrSchool
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as NoorApplication).container
    private val repo = container.settings

    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasBackgroundPermission() = container.location.hasBackgroundPermission()
    fun hasForegroundPermission() = container.location.hasForegroundPermission()
    fun hasPrecise() = container.location.hasPrecise()

    fun setLocationMode(m: LocationMode) = viewModelScope.launch {
        repo.setLocationMode(m)
        if (m == LocationMode.AUTO) container.location.refresh()
    }
    fun chooseCity(id: String) = viewModelScope.launch { repo.setManualCity(id) }

    /** A place from the worldwide list: stored with its coordinates, zone and names, so nothing needs looking up later. */
    fun choosePlace(g: com.noor.prayer.geo.GazPlace) = viewModelScope.launch {
        repo.setManualPlace(
            com.noor.app.data.KnownLocation(
                point = g.point, zoneId = g.zoneId, placeName = g.name, countryCode = g.countryCode, regionCode = g.region,
                fixedAtEpochMs = 0L, placeNameAr = g.nameAr, placeContext = g.admin1Name.takeIf { it.isNotBlank() && it != g.name },
                placeContextAr = null, precise = false,
            ),
        )
    }
    fun refreshLocation() = viewModelScope.launch { container.location.refresh() }
    fun setBackgroundUpdates(on: Boolean) = viewModelScope.launch {
        repo.setBackgroundUpdates(on)
        LocationWorker.sync(getApplication(), on && container.location.hasBackgroundPermission())
    }
    fun setProfile(id: String?) = viewModelScope.launch { repo.setProfile(id) }
    fun setAsr(s: AsrSchool?) = viewModelScope.launch { repo.setAsrSchool(s) }
    fun setHighLatitude(r: HighLatitudeRule) = viewModelScope.launch { repo.setHighLatitudeRule(r) }
    fun setOffset(p: Prayer, m: Int) = viewModelScope.launch { repo.setOffset(p, m) }
    fun resetOffsets() = viewModelScope.launch { repo.resetOffsets() }
    fun setNumerals(n: Numerals) = viewModelScope.launch { repo.setNumerals(n) }
    fun setTimeFormat(t: TimeFormat) = viewModelScope.launch { repo.setTimeFormat(t) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { repo.setTheme(t) }
    fun setHijriAdjust(d: Int) = viewModelScope.launch { repo.setHijriAdjust(d) }
    fun setStatusNotification(on: Boolean) = viewModelScope.launch { repo.setStatusNotification(on) }
    fun setElapsedAfterPrayer(on: Boolean) = viewModelScope.launch { repo.setElapsedAfterPrayer(on) }
    fun setScenery(v: com.noor.app.data.Scenery) = viewModelScope.launch { repo.setScenery(v) }
    fun setSkyGraphics(v: com.noor.app.data.SkyGraphics) = viewModelScope.launch { repo.setSkyGraphics(v) }
    fun setSeasons(on: Boolean) = viewModelScope.launch { repo.setSeasons(on) }
    fun setTrueStars(on: Boolean) = viewModelScope.launch { repo.setTrueStars(on) }
    fun completeOnboarding() = viewModelScope.launch { repo.setOnboardingDone() }

    fun setLanguage(l: Language) = viewModelScope.launch {
        repo.setLanguage(l)
        AppCompatDelegate.setApplicationLocales(
            if (l == Language.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(l.tag),
        )
        // Rename Noor's notification categories in Android's settings to the new language.
        com.noor.alerts.AlertNotifications.ensureChannels(getApplication())
    }
}
