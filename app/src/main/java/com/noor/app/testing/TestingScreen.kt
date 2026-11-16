package com.noor.app.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noor.app.data.Scenery
import kotlin.math.roundToInt

/**
 * The Testing screen (0.14): the tester's options, in English only. FOR TESTING ONLY: to be removed before the app
 * is released, with [Tester] (the owner's word). Everything here changes only what the home screen's sky shows, and
 * only until the app is started again; the scenery is the one thing it changes in Settings.
 *
 * [scenery] is the scenery chosen in Settings now; [setScenery] changes it; [onShowHome] goes to the home screen;
 * [onOpenLocation] opens the app's own place chooser (the way to try another place, with its own prayer times).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestingScreen(
    contentPadding: PaddingValues,
    scenery: Scenery,
    setScenery: (Scenery) -> Unit,
    onBack: () -> Unit,
    onShowHome: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val t = Tester
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Testing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "For testing only. These options change what the home screen's sky shows, until Noor is started again. " +
                "The prayer times, the countdown, the Adhan and the watch always stay real.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            SwitchRow("Use the options below", t.on) { if (it) t.on = true else t.reset() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { t.on = true; onShowHome() }) { Text("Show the home screen") }
                OutlinedButton(onClick = { t.reset() }) { Text("Back to real") }
            }
        }

        Card {
            Title("Demo", "Every scenery in turn, each through a whole day in a season and a weather of its own: about nine minutes. It stops by itself.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { t.startDemo(scenery) { setScenery(it) }; onShowHome() }) { Text(if (t.demoLabel == null) "Start the demo" else "Start again") }
                if (t.demoLabel != null) OutlinedButton(onClick = { t.stopDemo() }) { Text("Stop") }
            }
            t.demoLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        Card {
            val shown = t.time
            Title("Time of day", if (shown == null) "The real time." else "The sky shows about ${clock(shown)}. Sunrise, sunset and the prayers stay where they really are for the place and date.")
            Chips(listOf("Now" to null, "Before dawn" to 0.20f, "Sunrise" to 0.275f, "Morning" to 0.36f, "Noon" to 0.5f, "Afternoon" to 0.62f, "Golden hour" to 0.70f, "Sunset" to 0.73f, "Dusk" to 0.755f, "Night" to 0.92f),
                selected = { it == shown }) { t.play(false); t.time = it; if (it != null) t.on = true }
            Slider(value = shown ?: 0.5f, onValueChange = { t.play(false); t.time = it; t.on = true }, valueRange = 0f..1f)
            SwitchRow("Let the day run by itself", t.playing) { t.play(it) }
            Chips(listOf("A day in 30 seconds" to 30f, "90 seconds" to 90f, "5 minutes" to 300f, "20 minutes" to 1200f), selected = { it == t.daySeconds }) { t.daySeconds = it }
        }

        Card {
            val y = t.year
            Title("Season", if (y == null) "As chosen in Settings (Home scenery → Seasons)." else "The land and the weather are shown for ${monthOf(y)}.")
            Chips(listOf<Pair<String, Float?>>("As in Settings" to null) + Tester.SEASONS.map { it.first to it.second }, selected = { it == y }) { t.year = it; if (it != null) t.on = true }
            Slider(value = y ?: 0.5f, onValueChange = { t.year = it; t.on = true }, valueRange = 0f..1f)
            Text("Sceneries that have pictures of their own for a season show them; the others show snow in winter where it would lie.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card {
            val w = t.weather
            Title("Weather", if (w == null) "The day's own weather for this place." else "A ${Tester.WEATHER_NAMES[w].lowercase()} day. It still changes through the day as such a day does.")
            Chips(listOf<Pair<String, Int?>>("The day's own" to null) + Tester.WEATHER_NAMES.mapIndexed { i, n -> n to i }, selected = { it == w }) { t.weather = it; if (it != null) t.on = true }
            OutlinedButton(onClick = { t.weatherDay++; t.on = true }) { Text("Another sky of this kind") }
        }

        Card {
            Title("Scenery", "This changes the scenery in Settings (it stays after testing).")
            Chips(listOf("Automatic" to Scenery.AUTO) + Tester.SCENERY_NAMES.map { it.value to it.key }, selected = { it == scenery }) { setScenery(it) }
        }

        Card {
            Title("Place", "To see another place (its prayer times, its Sun, its night sky), choose it in Noor's own place chooser, and choose your own place again afterwards.")
            OutlinedButton(onClick = onOpenLocation) { Text("Choose a place") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Title(title: String, note: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = on, onCheckedChange = set)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<String, T>>, selected: (T) -> Boolean, pick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in options) FilterChip(selected = selected(value), onClick = { pick(value) }, label = { Text(label) })
    }
}

private fun clock(f: Float): String {
    val m = (f * 1440).roundToInt().coerceIn(0, 1439)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** The month a phase of the year (0 = midwinter) falls in, for the northern hemisphere, and the other one for the southern. */
private fun monthOf(year: Float): String {
    val names = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val day = ((year * 365f) - 10f + 365f) % 365f          // (the phase counts from 21 December)
    val n = (day / 30.44f).toInt().coerceIn(0, 11)
    return "${names[n]} (${names[(n + 6) % 12]} south of the equator)"
}
