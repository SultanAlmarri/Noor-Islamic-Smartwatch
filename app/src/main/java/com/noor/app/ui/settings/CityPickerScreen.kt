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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.prayers.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.Places

/** Manual location: the bundled list works offline. */
@Composable
fun CityPickerScreen(contentPadding: PaddingValues, onDone: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value
    val ar = isArabicUi()
    val c = NoorTheme.colors
    var query by remember { mutableStateOf("") }
    val cities = Places.ALL.filter { query.isBlank() || it.nameEn.contains(query, true) || it.nameAr.contains(query) }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cancel), tint = c.textPrimary) }
            Text(stringResource(R.string.location_manual), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(R.string.search_city)) },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.gold, cursorColor = c.gold),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(cities, key = { it.id }) { city ->
                val selected = s?.manualCityId == city.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { vm.chooseCity(city.id); onDone() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) city.nameAr else city.nameEn, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(if (ar) city.nameEn else city.nameAr, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(20.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        Spacer(Modifier.width(1.dp))
    }
}
