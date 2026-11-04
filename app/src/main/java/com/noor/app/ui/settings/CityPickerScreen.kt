package com.noor.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.geo.Flags
import com.noor.prayer.geo.GazPlace
import com.noor.prayer.geo.Gazetteer
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Manual location: search about 34,000 places worldwide (every town of 15,000+ people and many city districts),
 * offline, in English or Arabic. Each result carries its time zone, so times are right wherever it is.
 */
@Composable
fun CityPickerScreen(contentPadding: PaddingValues, onDone: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value
    val ar = isArabicUi()
    val c = NoorTheme.colors
    val context = LocalContext.current
    val store = remember { (context.applicationContext as NoorApplication).container.gazetteer }
    var gaz by remember { mutableStateOf<Gazetteer?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GazPlace>>(emptyList()) }
    LaunchedEffect(Unit) { gaz = store.get() }
    LaunchedEffect(query, gaz) {
        val g = gaz ?: return@LaunchedEffect
        delay(150) // debounce typing
        results = if (query.isBlank()) g.places.sortedByDescending { it.population }.take(40) else g.search(query, 60)
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cancel), tint = c.textPrimary) }
            Text(stringResource(R.string.location_manual), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(R.string.search_city_world)) },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.gold, cursorColor = c.gold),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { place ->
                val selected = s?.manualPlace?.let { Gazetteer.distanceKm(it.point, place.point) < 0.05 } == true
                val country = Locale("", Flags.displayCountry(place.countryCode) ?: place.countryCode)
                    .getDisplayCountry(if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { vm.choosePlace(place); onDone() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Flags.emoji(place.countryCode) ?: "", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) place.nameAr ?: place.name else place.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(listOf(place.admin1Name.takeIf { it.isNotBlank() && it != place.name }, country).filterNotNull().joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(20.dp))
                }
            }
            item {
                Text(stringResource(R.string.geonames_credit), style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    modifier = Modifier.padding(12.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
