package com.noor.app.ui.location

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.data.FoundPlace
import com.noor.app.data.LocationMode
import com.noor.app.data.PickedPlace
import com.noor.app.data.WorldStore
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Noor
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.GeoPoint
import com.noor.prayer.Prayer
import com.noor.prayer.geo.Coordinates
import com.noor.prayer.geo.Flags
import com.noor.prayer.geo.WorldPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The steps of choosing a location (0.9.7). */
sealed interface LocStep {
    data object Home : LocStep
    data object Countries : LocStep
    data class Cities(val countryCode: String) : LocStep
    data class Areas(val city: WorldPlaces.Place, val areas: List<WorldPlaces.Place>) : LocStep
    data class MapPick(val start: GeoPoint?, val zoom: Double) : LocStep
    data object Confirm : LocStep
}

/**
 * Choosing where prayer times are for (0.9.7): one search box for everything (cities, towns, villages, districts,
 * addresses, coordinates and map links); the phone's own location; a satellite map with a pin; coordinates typed by
 * hand; and country → city → area browsing. Every way ends on one confirmation, which shows the exact point, its time
 * zone and today's times there, computed exactly as the app will compute them, before anything changes.
 */
@Composable
fun LocationScreen(contentPadding: PaddingValues, onDone: () -> Unit, vm: LocationViewModel = viewModel()) {
    val stack = remember { mutableStateListOf<LocStep>(LocStep.Home) }
    fun push(s: LocStep) { stack += s }
    fun pop() { if (stack.size > 1) { if (stack.last() == LocStep.Confirm) vm.clearPreview(); stack.removeAt(stack.lastIndex) } else onDone() }
    BackHandler(enabled = stack.size > 1) { pop() }
    var coordsDialog by rememberSaveable { mutableStateOf(false) }
    val top = stack.last()
    // The keyboard never stays open over the next step (it would cover the confirmation's buttons).
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(top) { keyboard?.hide(); focus.clearFocus() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedContent(top, transitionSpec = {
        val forward = stack.indexOf(targetState) >= stack.indexOf(initialState) && targetState != initialState
        val dir = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
        (slideInHorizontally(tween(260)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(160)))
    }, label = "locStep") { step ->
        // Lists and cards keep a readable width on wide screens (a foldable's inner screen, tablets); the map fills it.
        if (step is LocStep.MapPick) MapPane(vm, step.start, step.zoom, onBack = { pop() }, onPinned = { p -> vm.pin(p); push(LocStep.Confirm) })
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
        when (step) {
            LocStep.Home -> HomePane(contentPadding, vm, onBack = { pop() }, onCountries = { push(LocStep.Countries) },
                onMap = { push(LocStep.MapPick(it, if (it == null) 2.5 else 12.0)) }, onCoords = { coordsDialog = true },
                onPicked = { push(LocStep.Confirm) }, onAutomatic = onDone)
            LocStep.Countries -> CountriesPane(contentPadding, vm, onBack = { pop() }, onCountry = { push(LocStep.Cities(it)) })
            is LocStep.Cities -> CitiesPane(contentPadding, vm, step.countryCode, onBack = { pop() },
                onCity = { city, areas -> if (areas.isEmpty()) { vm.pick(city); push(LocStep.Confirm) } else push(LocStep.Areas(city, areas)) })
            is LocStep.Areas -> AreasPane(contentPadding, vm, step.city, step.areas, onBack = { pop() },
                onPick = { vm.pick(it); push(LocStep.Confirm) }, onMap = { push(LocStep.MapPick(step.city.point, 13.5)) })
            is LocStep.MapPick -> {}
            LocStep.Confirm -> ConfirmPane(contentPadding, vm, onBack = { pop() },
                onAdjust = { p -> push(LocStep.MapPick(p, 16.0)) }, onSaved = onDone)
        }
        } }
    }
    if (coordsDialog) CoordinatesDialog(onDismiss = { coordsDialog = false }, onPoint = { p -> coordsDialog = false; vm.pin(p); push(LocStep.Confirm) })
}

// ---------------------------------------------------------------------------------------------------------------------
// Home: what is chosen now, one search box, and the ways to choose.

@OptIn(FlowPreview::class)
@Composable
private fun HomePane(
    contentPadding: PaddingValues, vm: LocationViewModel, onBack: () -> Unit, onCountries: () -> Unit, onMap: (GeoPoint?) -> Unit,
    onCoords: () -> Unit, onPicked: () -> Unit, onAutomatic: () -> Unit,
) {
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val s = vm.settings.collectAsStateWithLifecycle().value
    val search = vm.search.collectAsStateWithLifecycle().value
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.useAutomatic(onAutomatic) }
    val current = s?.let { com.noor.app.data.PrayerProvider.place(it) }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Header(stringResource(R.string.loc_title), onBack)
        SearchField(search.query, stringResource(R.string.loc_search_hint), onChange = { vm.setQuery(it, ar) })
        if (search.query.isNotBlank()) {
            SearchResults(vm, search, ar, onPoint = { p, title -> vm.pin(p, title); onPicked() }, onPlace = { vm.pick(it); onPicked() })
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(Noor.Gap)) {
            if (current != null) GlassCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text(stringResource(R.string.loc_now_for), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Flags.emoji(current.countryCode) ?: "", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) current.nameAr ?: current.nameEn else current.nameEn, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        val sub = if (s.locationMode == LocationMode.AUTO) stringResource(R.string.loc_now_auto)
                            else listOfNotNull(if (ar) current.contextAr ?: current.contextEn else current.contextEn, coords(current.point)).joinToString("  ·  ")
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                OptionRow(Icons.Rounded.MyLocation, stringResource(R.string.loc_current), stringResource(R.string.loc_current_desc),
                    selected = s?.locationMode == LocationMode.AUTO) {
                    if (vm.hasLocationPermission()) vm.useAutomatic(onAutomatic) else permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                Hairline()
                OptionRow(Icons.Rounded.Map, stringResource(R.string.loc_map), stringResource(R.string.loc_map_desc)) { onMap(current?.point) }
                Hairline()
                OptionRow(Icons.Rounded.Tune, stringResource(R.string.loc_coords), stringResource(R.string.loc_coords_desc), onClick = onCoords)
                Hairline()
                OptionRow(Icons.Rounded.Public, stringResource(R.string.loc_browse), null, onClick = onCountries)
            }
            Text(stringResource(R.string.geonames_credit) + "  " + stringResource(R.string.loc_tz_credit), style = MaterialTheme.typography.bodySmall,
                color = c.textMuted, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun SearchResults(vm: LocationViewModel, search: SearchState, ar: Boolean, onPoint: (GeoPoint, String?) -> Unit, onPlace: (WorldPlaces.Place) -> Unit) {
    val c = NoorTheme.colors
    LazyColumn(Modifier.fillMaxSize()) {
        search.coordinates?.let { p ->
            item { PlaceRow(icon = Icons.Rounded.Place, title = stringResource(R.string.loc_use_coords), subtitle = coords(p)) { onPoint(p, null) } }
        }
        if (search.offline.isNotEmpty()) item { SectionLabel(stringResource(R.string.loc_places)) }
        items(search.offline, key = { "o" + it.row }) { p -> PlaceItem(p, ar) { onPlace(p) } }
        if (search.online.isNotEmpty() || search.searchingOnline) item { SectionLabel(stringResource(R.string.loc_online_results)) }
        items(search.online, key = { "n" + it.point.latitude + it.point.longitude + it.title }) { f ->
            PlaceRow(flag = Flags.emoji(f.countryCode), title = f.title, subtitle = f.subtitle) { onPoint(f.point, f.title) }
        }
        if (search.searchingOnline) item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.loc_online_searching), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        if (!search.searchingOnline && search.offline.isEmpty() && search.online.isEmpty() && search.coordinates == null && search.query.trim().length >= 2) item {
            Text(stringResource(R.string.search_no_results, search.query), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Country → city → area.

@Composable
private fun CountriesPane(contentPadding: PaddingValues, vm: LocationViewModel, onBack: () -> Unit, onCountry: (String) -> Unit) {
    val ar = isArabicUi()
    val world = vm.world.collectAsStateWithLifecycle().value
    val s = vm.settings.collectAsStateWithLifecycle().value
    var filter by rememberSaveable { mutableStateOf("") }
    val here = s?.let { com.noor.app.data.PrayerProvider.place(it)?.countryCode }
    val all = remember(world, ar) {
        val collator = Collator.getInstance(if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH)
        world?.countries()?.map { it to WorldStore.countryName(it, ar) }?.sortedWith { a, b -> collator.compare(a.second, b.second) }.orEmpty()
    }
    val shown = remember(all, filter) {
        val f = WorldPlaces.fold(filter.trim())
        if (f.isEmpty()) all else all.filter { (cc, name) -> WorldPlaces.fold(name).contains(f) || WorldPlaces.fold(WorldStore.countryName(cc, false)).contains(f) || cc.equals(filter.trim(), true) }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Header(stringResource(R.string.loc_browse), onBack)
        SearchField(filter, stringResource(R.string.loc_countries_filter), onChange = { filter = it })
        if (world == null) Loading()
        LazyColumn(Modifier.fillMaxSize()) {
            if (filter.isBlank() && here != null) all.firstOrNull { it.first == here }?.let { (cc, name) ->
                item(key = "here") { PlaceRow(flag = Flags.emoji(cc), title = name, subtitle = null, chevron = true) { onCountry(cc) } }
                item(key = "hereSep") { Hairline() }
            }
            items(shown, key = { it.first }) { (cc, name) -> PlaceRow(flag = Flags.emoji(cc), title = name, subtitle = null, chevron = true) { onCountry(cc) } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun CitiesPane(contentPadding: PaddingValues, vm: LocationViewModel, countryCode: String, onBack: () -> Unit,
                       onCity: (WorldPlaces.Place, List<WorldPlaces.Place>) -> Unit) {
    val ar = isArabicUi()
    val world = vm.world.collectAsStateWithLifecycle().value
    var filter by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf<Int?>(null) }
    val cities by produceState<List<WorldPlaces.Place>?>(null, world, countryCode) { value = world?.let { w -> withContext(Dispatchers.Default) { w.cities(countryCode) } } }
    val filtered by produceState<List<WorldPlaces.Place>?>(null, world, filter, cities) {
        val f = filter.trim()
        value = if (f.length < 2) null else world?.let { w -> withContext(Dispatchers.Default) { w.search(f, 200, countryCode) } }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Header((Flags.emoji(countryCode)?.let { "$it  " } ?: "") + WorldStore.countryName(countryCode, ar), onBack)
        SearchField(filter, stringResource(R.string.loc_cities_filter), onChange = { filter = it })
        val list = filtered ?: cities
        if (list == null) Loading()
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.orEmpty(), key = { it.row }) { p ->
                PlaceItem(p, ar, inCountry = true, busy = opening == p.row) {
                    if (opening != null) return@PlaceItem
                    if (p.neighbourhood) { onCity(p, emptyList()); return@PlaceItem }
                    opening = p.row
                    scope.launch {
                        val areas = withContext(Dispatchers.Default) { world!!.areas(p) }
                        opening = null
                        onCity(p, areas)
                    }
                }
            }
            if (filtered != null && filtered!!.isEmpty()) item {
                Text(stringResource(R.string.search_no_results, filter), style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun AreasPane(contentPadding: PaddingValues, vm: LocationViewModel, city: WorldPlaces.Place, areas: List<WorldPlaces.Place>, onBack: () -> Unit,
                      onPick: (WorldPlaces.Place) -> Unit, onMap: () -> Unit) {
    val ar = isArabicUi()
    var filter by rememberSaveable { mutableStateOf("") }
    val cityName = if (ar) city.nameAr ?: city.name else city.name
    val shown = remember(areas, filter) {
        val f = WorldPlaces.fold(filter.trim())
        if (f.isEmpty()) areas else areas.filter { WorldPlaces.fold(it.name).contains(f) || it.nameAr?.let { a -> WorldPlaces.fold(a).contains(f) } == true }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Header(stringResource(R.string.loc_areas_title, cityName), onBack)
        SearchField(filter, stringResource(R.string.loc_areas_filter), onChange = { filter = it })
        LazyColumn(Modifier.fillMaxSize()) {
            if (filter.isBlank()) {
                item { PlaceRow(icon = Icons.Rounded.Place, title = stringResource(R.string.loc_city_centre, cityName), subtitle = stringResource(R.string.loc_city_centre_desc)) { onPick(city) } }
                item { PlaceRow(icon = Icons.Rounded.Map, title = stringResource(R.string.loc_pin_exact), subtitle = null) { onMap() } }
                item { Hairline() }
            }
            items(shown, key = { it.row }) { p -> PlaceItem(p, ar, showRegion = false) { onPick(p) } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Confirmation: the exact point, its time zone and today's times there.

@Composable
private fun ConfirmPane(contentPadding: PaddingValues, vm: LocationViewModel, onBack: () -> Unit, onAdjust: (GeoPoint) -> Unit, onSaved: () -> Unit) {
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val s = vm.settings.collectAsStateWithLifecycle().value
    val preview = vm.preview.collectAsStateWithLifecycle().value
    val f = Formatter(ar, s?.numerals ?: com.noor.app.data.Numerals.WESTERN, s?.timeFormat ?: com.noor.app.data.TimeFormat.H12)
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Header(stringResource(R.string.loc_confirm_title), onBack)
        if (preview == null) {
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.gold, strokeWidth = 2.5.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loc_naming), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            return@Column
        }
        val p = preview.picked
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Noor.Gap)) {
            // The point on the satellite picture.
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(Noor.CardRadius))) {
                val state = remember(p.point) { MapState(p.point, 15.5) }
                TileMap(state, MapLayer.SATELLITE, rememberTileCache(), Modifier.fillMaxSize())
                Pin(Modifier.align(Alignment.Center), lifted = false)
            }
            Column(Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flags.emoji(p.countryCode)?.let { Text(it, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp)) }
                    Text(if (ar) p.nameAr ?: p.nameEn else p.nameEn, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                }
                (if (ar) p.contextAr ?: p.contextEn else p.contextEn)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
            GlassCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Fact(stringResource(R.string.loc_coordinates), coords(p.point))
                Spacer(Modifier.height(12.dp))
                val zone = ZoneId.of(p.zoneId)
                val now = Instant.now()
                Fact(stringResource(R.string.loc_time_zone), zoneText(p.zoneId, ar, f, now))
                val dst = zone.rules.isDaylightSavings(now)
                val changes = zone.rules.nextTransition(now) != null && zone.rules.transitionRules.isNotEmpty()
                Text(stringResource(when { dst -> R.string.loc_dst_now; changes -> R.string.loc_dst_rule; else -> R.string.loc_dst_none }) +
                    if (dst) " " + stringResource(R.string.loc_dst_rule) else "",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 2.dp))
            }
            preview.day?.let { day ->
                GlassCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Text(stringResource(R.string.loc_today_times), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    for (pr in Prayer.entries) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(f.prayerName(pr, day.date), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                            Text(day.minutes[pr]?.let { f.timeText(it) } ?: "—", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = c.textPrimary)
                        }
                    }
                    val method = if (ar) preview.methodAr else preview.methodEn
                    Text(stringResource(if (preview.official) R.string.loc_official else R.string.loc_method, method), style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.loc_adjust_pin), Modifier.weight(1f)) { onAdjust(p.point) }
            PrimaryButton(stringResource(R.string.loc_use_this), Modifier.weight(1f)) { vm.save(p, onSaved) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = NoorTheme.colors
    Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Text(value, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
}

// ---------------------------------------------------------------------------------------------------------------------
// The map: satellite or street, a fixed pin in the middle, search, my location.

@OptIn(FlowPreview::class)
@Composable
private fun MapPane(vm: LocationViewModel, start: GeoPoint?, startZoom: Double, onBack: () -> Unit, onPinned: (GeoPoint) -> Unit) {
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val state = remember { MapState(start ?: GeoPoint(21.4225, 39.8262), if (start == null) 2.5 else startZoom) }
    val cache = rememberTileCache()
    var layer by rememberSaveable { mutableStateOf(MapLayer.SATELLITE) }
    val scope = rememberCoroutineScope()
    val world = vm.world.collectAsStateWithLifecycle().value
    var label by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val search = vm.search.collectAsStateWithLifecycle().value
    var searching by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun closeSearch() { searching = false; keyboard?.hide(); focus.clearFocus() }
    LaunchedEffect(state.moving) { if (state.moving) closeSearch() }
    // The place under the pin, from the offline list, once the map rests.
    LaunchedEffect(world) {
        snapshotFlow { Triple(state.x, state.y, state.zoom) }.distinctUntilChanged().debounce(250).collect {
            val w = world ?: return@collect
            val p = state.center
            val near = withContext(Dispatchers.Default) { w.nearest(p, if (state.zoom > 11) 8.0 else 40.0) }
            label = near?.let { n -> (if (ar) n.nameAr ?: n.name else n.name) + ", " + WorldStore.countryName(n.countryCode, ar) }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1A2A))) {
        TileMap(state, layer, cache, Modifier.fillMaxSize())
        Pin(Modifier.align(Alignment.Center), lifted = state.moving)
        // Top: back and search.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, null, onBack)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    SearchField(query, stringResource(R.string.loc_map_search), raised = true, onChange = { query = it; searching = it.isNotBlank(); vm.setQuery(it, ar) })
                }
            }
            if (searching && query.isNotBlank()) {
                GlassCard(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 6.dp), padding = 0.dp) {
                    LazyColumn {
                        search.coordinates?.let { p -> item { PlaceRow(icon = Icons.Rounded.Place, title = stringResource(R.string.loc_use_coords), subtitle = coords(p)) {
                            closeSearch(); scope.launch { state.animateTo(p, 17.0) } } } }
                        items(search.offline.take(25), key = { "o" + it.row }) { p -> PlaceItem(p, ar) {
                            closeSearch(); scope.launch { state.animateTo(p.point, if (p.neighbourhood) 15.5 else if (p.population > 200_000) 12.0 else 14.0) } } }
                        items(search.online, key = { "n" + it.point.latitude + it.point.longitude + it.title }) { f ->
                            PlaceRow(flag = Flags.emoji(f.countryCode), title = f.title, subtitle = f.subtitle) { closeSearch(); scope.launch { state.animateTo(f.point, 16.5) } }
                        }
                        if (search.searchingOnline) item {
                            Text(stringResource(R.string.loc_online_searching), style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        // Right: layers, zoom, my location.
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(Icons.Rounded.Layers, stringResource(if (layer == MapLayer.SATELLITE) R.string.loc_map_streets else R.string.loc_map_satellite)) {
                layer = if (layer == MapLayer.SATELLITE) MapLayer.STREETS else MapLayer.SATELLITE
            }
            RoundButton(Icons.Rounded.Add, stringResource(R.string.loc_zoom_in)) { scope.launch { state.animateTo(state.center, state.zoom + 1) } }
            RoundButton(Icons.Rounded.Remove, stringResource(R.string.loc_zoom_out)) { scope.launch { state.animateTo(state.center, state.zoom - 1) } }
            if (vm.hasLocationPermission()) RoundButton(Icons.Rounded.MyLocation, stringResource(R.string.loc_my_location)) {
                scope.launch { vm.lastPosition()?.let { state.animateTo(it, 16.5) } }
            }
        }
        // Bottom: what is under the pin, and the button.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            if (cache.offline) Text(stringResource(R.string.loc_map_offline), style = MaterialTheme.typography.bodySmall, color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(10.dp))
            GlassCard(Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(Noor.CardRadius)), padding = 16.dp) {
                Text(label ?: stringResource(R.string.loc_map_hint), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(coords(state.center), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.loc_use_this), Modifier.fillMaxWidth()) { onPinned(state.center) }
            }
            Text(layer.attribution, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 2,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
    }
}

/** The pin: its point is exactly the centre of the map. It lifts while the map moves. */
@Composable
internal fun Pin(modifier: Modifier, lifted: Boolean) {
    val c = NoorTheme.colors
    val lift by animateFloatAsState(if (lifted) 1f else 0f, tween(160), label = "pinLift")
    Canvas(modifier.size(44.dp, 88.dp)) {
        // The tip at the centre of this box (which is the centre of the map); the head above it.
        val tip = Offset(size.width / 2, size.height / 2)
        val up = 10.dp.toPx() * lift
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 3.dp.toPx() + 2.dp.toPx() * lift, center = tip)
        val head = Offset(tip.x, tip.y - 30.dp.toPx() - up)
        val r = 13.dp.toPx()
        val path = Path().apply {
            moveTo(tip.x, tip.y - up)
            lineTo(head.x - r * 0.72f, head.y + r * 0.7f)
            lineTo(head.x + r * 0.72f, head.y + r * 0.7f)
            close()
        }
        drawPath(path, c.gold)
        drawCircle(c.gold, r, head)
        drawCircle(Color.White, r, head, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, r * 0.38f, head)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Coordinates typed by hand.

@Composable
private fun CoordinatesDialog(onDismiss: () -> Unit, onPoint: (GeoPoint) -> Unit) {
    val c = NoorTheme.colors
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    val point = remember(lat, lon) { if (lon.isBlank()) Coordinates.parse(lat) else Coordinates.parse("$lat, $lon") }
    val tried = lat.isNotBlank() && (lon.isNotBlank() || lat.length > 6)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text(stringResource(R.string.loc_coords), color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CoordField(lat, stringResource(R.string.loc_coords_lat)) { lat = it }
                CoordField(lon, stringResource(R.string.loc_coords_lon)) { lon = it }
                Text(stringResource(R.string.loc_coords_example), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                if (point != null) Text(coords(point), style = MaterialTheme.typography.bodyMedium, color = c.goldText)
                else if (tried) Text(stringResource(R.string.loc_coords_invalid), style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { point?.let(onPoint) }, enabled = point != null) {
                Text(stringResource(R.string.loc_continue), color = if (point != null) c.goldText else c.textMuted)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel), color = c.textSecondary) } },
    )
}

@Composable
private fun CoordField(value: String, label: String, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    TextField(value, onChange, singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.gold,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedLabelColor = c.goldText),
        modifier = Modifier.fillMaxWidth())
}

// ---------------------------------------------------------------------------------------------------------------------
// Pieces.

/**
 * Coordinates as maps and GPS apps write them: "25.20480° N, 55.27080° E"; in Arabic "25.20480° ش، 55.27080° ق"
 * (each number kept whole, left to right, inside the Arabic sentence), with the digits chosen in the settings.
 */
@Composable
internal fun coords(p: GeoPoint): String {
    val ar = isArabicUi()
    if (!ar) return Coordinates.format(p)
    val numerals = (androidx.lifecycle.viewmodel.compose.viewModel<LocationViewModel>().settings.collectAsStateWithLifecycle().value?.numerals)
        ?: com.noor.app.data.Numerals.WESTERN
    val f = Formatter(true, numerals, com.noor.app.data.TimeFormat.H24)
    // Left-to-right marks around each number keep "31.20180°" whole inside the Arabic sentence (as Formatter.degrees).
    fun n(v: Double) = "\u200E" + f.digits("%.5f".format(Locale.ROOT, kotlin.math.abs(v))) + "°\u200E"
    return n(p.latitude) + " " + (if (p.latitude >= 0) "ش" else "ج") + "، " + n(p.longitude) + " " + (if (p.longitude >= 0) "ق" else "غ")
}

/** The time zone in words: "Gulf Standard Time (Asia/Dubai) · UTC+04:00"; in Arabic its Arabic name and offset only. */
internal fun zoneText(zoneId: String, ar: Boolean, f: Formatter, now: Instant = Instant.now()): String {
    val zone = ZoneId.of(zoneId)
    val offset = zone.rules.getOffset(now)
    val tz = java.util.TimeZone.getTimeZone(zone)
    val name = tz.getDisplayName(zone.rules.isDaylightSavings(now), java.util.TimeZone.LONG, if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH)
        .takeIf { it.isNotBlank() && !it.startsWith("GMT") && !(ar && it.any { c -> c in 'A'..'z' }) }
    val off = if (offset.totalSeconds == 0) "+00:00" else offset.id
    return if (ar) listOfNotNull(name, "\u200E" + f.digits(off) + "\u200E").joinToString("  ·  ")
    else (name?.let { "$it (${zoneId.replace('_', ' ')})" } ?: zoneId.replace('_', ' ')) + "  ·  UTC$off"
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val c = NoorTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.textPrimary) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchField(value: String, hint: String, raised: Boolean = false, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = c.textMuted) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, null, tint = c.textMuted) } }) else null,
        placeholder = { Text(hint, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = if (raised) c.surfaceRaised else c.surface, cursorColor = c.gold,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = if (raised) 0.dp else 6.dp, bottom = if (raised) 0.dp else 10.dp)
            .then(if (raised) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier),
    )
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String?, selected: Boolean = false, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.goldText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        }
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.success))
        else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted)
    }
}

@Composable
private fun PlaceItem(p: WorldPlaces.Place, ar: Boolean, showRegion: Boolean = true, inCountry: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val region = if (ar) p.regionNameAr else p.regionName.takeIf { it.isNotBlank() && it != p.name }
    val sub = listOfNotNull(
        if (p.neighbourhood) stringResource(R.string.loc_neighbourhood) else null,
        region.takeIf { showRegion }, WorldStore.countryName(p.countryCode, ar).takeIf { showRegion && !inCountry },
    ).joinToString(if (ar) "، " else ", ")
    PlaceRow(flag = Flags.emoji(p.countryCode).takeIf { showRegion && !inCountry }, icon = if (inCountry || !showRegion) Icons.Rounded.Place else null,
        title = if (ar) p.nameAr ?: p.name else p.name, subtitle = sub.ifBlank { null }, busy = busy, onClick = onClick)
}

@Composable
private fun PlaceRow(flag: String? = null, icon: ImageVector? = null, title: String, subtitle: String?, chevron: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.RowRadius)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        when {
            icon != null -> Icon(icon, null, tint = c.goldText, modifier = Modifier.width(36.dp).size(22.dp))
            flag != null -> Text(flag, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = c.gold, strokeWidth = 2.dp)
        else if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = NoorTheme.colors.textMuted,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(NoorTheme.colors.separator))

@Composable
private fun Loading() = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.size(24.dp), color = NoorTheme.colors.gold, strokeWidth = 2.dp)
}

@Composable
private fun RoundButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(Modifier.size(46.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(c.surfaceRaised).clickable(onClick = onClick)
        .semantics { if (description != null) contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.textPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(16.dp)).background(c.gold).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = c.onGold, maxLines = 1)
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.outline, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
    }
}
