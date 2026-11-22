package com.noor.app.adhan

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.alerts.AdhanService
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.ThemeMode
import com.noor.app.ui.adhan.ClockText
import com.noor.app.ui.adhan.FactsLine
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Sky
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter

/**
 * The Adhan screen (0.9.7): shown over the lock screen while the Adhan plays, as an alarm clock shows its ringing
 * screen, with the prayer and a large Stop. It closes by itself when the Adhan ends. Being on screen also satisfies
 * Android 17's rule that only apps the user can see (or that are properly in the foreground) may play sound.
 */
class AdhanAlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = (application as NoorApplication).container.settings.settings
        setContent {
            val now by AdhanService.nowPlaying.collectAsState()
            LaunchedEffect(now) { if (now == null) finish() }
            // (0.25) The time of day, as the phone's own clock tells it, in the digits and the 12 or 24 hours chosen in
            // Minhad: looked at again each second, so it turns with the minute.
            val s by settings.collectAsState(initial = null)
            var at by remember { mutableStateOf(java.time.ZonedDateTime.now()) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000 - System.currentTimeMillis() % 1000); at = java.time.ZonedDateTime.now() } }
            val arabic = isArabicUi()
            val clock = s?.let { Formatter(arabic, it.numerals, it.timeFormat).time(at.hour * 60 + at.minute) }
            NoorTheme(ThemeMode.DARK) {
                AdhanScreen(now, onStop = { AdhanService.stop(this); finish() }, onClose = { finish() },
                    onSnooze = { AdhanService.snooze(this); finish() }, clock = clock)
            }
        }
    }
}

/**
 * The facts under what is sounding. The alert's line is "time · place" for the Adhan (`AlertText.body`) and "[relation
 * ·] prayer time · place" for an alarm (`AlertText.alarmBody`), written for the notification; here, under the clock, the
 * Adhan's own time would say the time twice, so its first fact is left out.
 */
internal fun ringingFacts(p: AdhanService.Playing): List<String> {
    val facts = p.body.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    return if (p.alarmId == 0) facts.drop(1) else facts
}

/**
 * What the screen shows (0.25, as a clock's ringing screen is laid out): at the top the time it is now, large and
 * light, and under it what is sounding (the prayer, or the alarm and the prayer it belongs to); in the lower third,
 * where the thumb is, Stop, an alarm's Snooze, and a quiet third way that says what it does ("Hide and keep playing":
 * it was "Keep playing"). Until 0.24 no time was shown anywhere, and everything stood in the middle over an empty
 * lower half. [clock]: the time and its AM or PM; null leaves it out.
 */
@Composable
internal fun AdhanScreen(now: AdhanService.Playing?, onStop: () -> Unit, onClose: () -> Unit, onSnooze: () -> Unit = {}, clock: Pair<String, String?>? = null) {
    val sky = Sky.colors(-6.0, true)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky.zenith, sky.mid, sky.horizon))).systemBarsPadding()) {
        // (The time stands at one height, an alarm or the Adhan; the actions stand on the foot of the screen.)
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // (0.17: one of the user's alarms wears an alarm clock, so it is never taken for the Adhan.)
            if ((now?.alarmId ?: 0) != 0) Icon(Icons.Rounded.Alarm, null, tint = Sky.GoldOnSky, modifier = Modifier.size(32.dp))
            else Icon(painterResource(R.drawable.ic_notification), null, tint = Sky.GoldOnSky, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            if (clock != null) ClockText(clock, MaterialTheme.typography.displaySmall.merge(Tabular).copy(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
                Sky.OnSky, markerColor = Sky.OnSkySecondary)
            Spacer(Modifier.height(6.dp))
            Text(now?.title ?: "", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp), color = Sky.OnSky, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            // (Second review) The line under it: for the Adhan the place only ("Dubai"): the alert's line, "5:33 PM · Dubai",
            // repeated the time standing large above it; for an alarm, its prayer's time and the place. Facts break
            // between them, never after a dot.
            val facts = now?.let { ringingFacts(it) }.orEmpty()
            if (facts.isNotEmpty()) FactsLine(facts, MaterialTheme.typography.bodyLarge, Sky.OnSkySecondary, Modifier.padding(top = 4.dp), center = true)
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            Button(onClick = onStop, shape = CircleShape, modifier = Modifier.size(132.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky.GoldOnSky, contentColor = Color(0xFF14110A))) {
                Text(stringResource(R.string.adhan_screen_stop), fontSize = 22.sp, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            // 0.17: an alarm's Snooze ("Snooze 10 min"), wide and easy to hit half asleep. (Second review: 28 dp under
            // Stop and 52 dp high, so a half-awake thumb does not take one for the other.)
            now?.snoozeLabel?.let { label ->
                Spacer(Modifier.height(28.dp))
                OutlinedButton(onClick = onSnooze, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.5.dp, Sky.GoldOnSky), colors = ButtonDefaults.outlinedButtonColors(contentColor = Sky.GoldOnSky)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.adhan_screen_close), color = Sky.OnSkySecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, letterSpacing = 0.sp))
            }
        }
    }
}
