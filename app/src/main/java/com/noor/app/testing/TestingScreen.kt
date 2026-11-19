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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // (0.21) The page's text in the theme's own colour: with none given, the titles and the switches' names were black,
    // all but invisible on the dark theme's night sky.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides com.noor.app.ui.theme.NoorTheme.colors.textPrimary) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Testing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "For testing only. These options change what the home screen's sky shows, until Minhad is started again. " +
                "The prayer times, the countdown, the Adhan and the watch always stay real.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            SwitchRow("Use the options below", t.on) { if (it) t.on = true else t.reset() }
            // (0.21: the buttons wrap onto a second line when they do not fit side by side; "Back to real" was squeezed
            // into a column of single words on a 360 dp phone, and pushed off the card with large text.)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { t.on = true; onShowHome() }) { Text("Show the home screen") }
                OutlinedButton(onClick = { t.reset() }) { Text("Back to real") }
            }
        }

        Card {
            Title("Demo", "Every scenery in turn, each through a whole day in a season and a weather of its own: about nine minutes. It stops by itself.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { t.startDemo(context, scenery) { setScenery(it) }; onShowHome() }) { Text(if (t.demoLabel == null) "Start the demo" else "Start again") }
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
            val fog = t.fog
            Title("Morning fog", if (fog == null) "The day's own: some mornings have fog, from before dawn until an hour or two after sunrise." else "Fog is shown at any hour, ${if (fog <= 0f) "none" else "%d %%".format((fog * 100).toInt())}. It looks its best about sunrise.")
            Chips(listOf<Pair<String, Float?>>("The day's own" to null, "None" to 0f, "Light" to 0.4f, "Thick" to 0.75f, "Thickest" to 1f), selected = { it == fog }) { t.fog = it; if (it != null) t.on = true }
        }

        Card {
            Title("Scenery", "This changes the scenery in Settings (it stays after testing).")
            Chips(listOf("Automatic" to Scenery.AUTO) + Tester.SCENERY_NAMES.map { it.value to it.key }, selected = { it == scenery }) { setScenery(it) }
        }

        Card {
            Title("Place", "To see another place (its prayer times, its Sun, its night sky), choose it in Minhad's own place chooser, and choose your own place again afterwards.")
            OutlinedButton(onClick = onOpenLocation) { Text("Choose a place") }
        }

        NotificationTestCard()
        Spacer(Modifier.height(24.dp))
    }
    }
}

/**
 * (0.22.4) Notification tests: the status notification with chosen content, through the real builders, the same id,
 * service and ticker ([NotificationTests]). Leaving this page gives the real notification back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationTestCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val n = NotificationTests
    var preset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(NotificationTests.Preset.LONGEST_EN) }
    var live by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(android.os.Build.VERSION.SDK_INT >= 36) }
    var arabicDigits by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var h24 by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var arabic by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Leaving the page gives the real notification back; Minhad itself closing (swiped away, so that the owner can see the
    // test keep ticking) or turning the phone does not: the Testing strip on every page says the test is on.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            if (activity == null || !(activity.isFinishing || activity.isChangingConfigurations)) NotificationTests.stop(context)
        }
    }
    fun case() = NotificationTests.Case(preset, live, if (arabicDigits) com.noor.app.data.Numerals.ARABIC_INDIC else com.noor.app.data.Numerals.WESTERN,
        if (h24) com.noor.app.data.TimeFormat.H24 else com.noor.app.data.TimeFormat.H12, arabic)
    // A change while a test is shown shows it again at once (its countdown starts again).
    fun again() { if (n.active != null) n.show(context, case()) }
    Card {
        Title("Notification tests", "Shows the notification in the status bar and the shade with the content chosen here, made exactly as the real one is " +
            "(the same layout, fitting, digits and ticking), so you can see what your phone does. The real prayer times and alarms are not touched.")
        Chips(NotificationTests.Preset.entries.map { it.label to it }, selected = { it == preset }) { p ->
            preset = p
            p.arabic?.let { arabic = it }
            p.numerals?.let { arabicDigits = it == com.noor.app.data.Numerals.ARABIC_INDIC }
            again()
        }
        SwitchRow("Status-bar chip (Live Update)" + if (android.os.Build.VERSION.SDK_INT < 36) ": needs Android 16" else "", live) { live = it; again() }
        SwitchRow("Arabic-Indic digits", arabicDigits) { arabicDigits = it; again() }
        SwitchRow("24-hour clock", h24) { h24 = it; again() }
        SwitchRow("In Arabic", arabic) { arabic = it; again() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { n.show(context, case()) }) { Text(if (n.active == null) "Show this notification" else "Show again") }
            if (n.active != null) OutlinedButton(onClick = { n.stop(context) }) { Text("Back to the real notification") }
        }
        if (n.active != null) {
            Text(n.report ?: "Posting…", style = MaterialTheme.typography.bodySmall)
            if (n.active?.live == true) Text("The chip: " + n.promotion() + ".", style = MaterialTheme.typography.bodySmall)
            Text("Close Minhad from the recent apps to check that it keeps ticking: the test stays until you leave this page or tap the Testing strip.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TimerTiming()
    }
}

/**
 * (0.23.2) Fine-tuning the status-bar timer against the phone's clock: the phone's time to the second, drawn here on the
 * instant each second begins, to compare with the timer at the top of the screen; and how early Minhad hands each second
 * to Android (the shade takes a moment, which differs from phone to phone, to draw it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerTiming() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var early by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(com.noor.app.notify.StatusTicker.also { it.loadTiming(context.applicationContext) }.earlyMs) }
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L - ms % 1000L)
            // On the frame after the second begins.
            androidx.compose.runtime.withFrameMillis { }
            now = System.currentTimeMillis() / 1000L
        }
    }
    val time = java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    // (0.23.2) The icon slot: which way leaves no gap on this phone (StatusNotification.slotMode).
    var slot by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(com.noor.app.notify.StatusNotification.slotMode) }
    Text("The timer's icon slot", style = MaterialTheme.typography.titleSmall)
    Text("Your phone keeps a place for an icon beside the timer. Try each way and keep the one that leaves no gap " +
        "(Minhad's icon must be off in Settings). Tap \"Show again\" above, or wait for the next second.", style = MaterialTheme.typography.bodySmall)
    Chips(listOf("Transparent icon" to 0, "One pixel wide" to 1, "Samsung chip icon" to 2, "Both (default)" to 3, "Samsung Now Bar path" to 5,
        "Samsung live card" to 6, "Prayer's symbol" to 4), selected = { it == slot }) { m ->
        slot = m; com.noor.app.notify.StatusNotification.saveSlot(context.applicationContext, m)
        com.noor.app.notify.StatusNotification.refreshAsync(context.applicationContext)
    }
    Text("Timer timing", style = MaterialTheme.typography.titleSmall)
    Text("The phone's clock, to the second:", style = MaterialTheme.typography.bodySmall)
    Text("%02d:%02d:%02d".format(time.hour, time.minute, time.second), style = MaterialTheme.typography.displaySmall.copy(
        fontFeatureSettings = "tnum"))
    Text("Minhad hands each second to Android $early ms before it begins. If the timer at the top changes after the clock above, " +
        "make it earlier; if before, later. It is kept for the real timer too.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-50L to "50 ms later", -10L to "10 ms later", 10L to "10 ms earlier", 50L to "50 ms earlier").forEach { (d, label) ->
            OutlinedButton(onClick = { com.noor.app.notify.StatusTicker.saveTiming(context.applicationContext, early + d); early = com.noor.app.notify.StatusTicker.earlyMs }) { Text(label) }
        }
        OutlinedButton(onClick = { com.noor.app.notify.StatusTicker.saveTiming(context.applicationContext, com.noor.app.notify.StatusTicker.EARLY_DEFAULT); early = com.noor.app.notify.StatusTicker.earlyMs }) { Text("Reset (${com.noor.app.notify.StatusTicker.EARLY_DEFAULT} ms)") }
    }
}

/**
 * A strip over the tab bar while the tester's options are on, on every page: what is being shown, and a tap to go back
 * to the real sky. So that a test sky is never taken for the real one.
 */
@Composable
fun TesterBanner() {
    val t = Tester
    if (!t.on) return
    val what = t.demoLabel ?: buildList {
        t.time?.let { add(if (t.playing) "day running, ${clock(it)}" else clock(it)) }
        t.year?.let { y -> add(Tester.SEASONS.minByOrNull { kotlin.math.abs(it.second - y) }!!.first.lowercase()) }
        t.weather?.let { add(Tester.WEATHER_NAMES[it].lowercase()) }
        t.fog?.let { if (it > 0f) add("fog") }
        if (NotificationTests.active != null) add("notification test")
    }.joinToString(", ").ifEmpty { "on" }
    // (0.21) What is shown on a line of its own, wrapped if it must be, and what a touch does under it: on one line
    // with no ellipsis the demo's label ran off the screen. The frame keeps room for it (AppFrame).
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().clickable { t.reset() }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("Testing: $what", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("Tap here for the real sky", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f), maxLines = 1)
        }
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
