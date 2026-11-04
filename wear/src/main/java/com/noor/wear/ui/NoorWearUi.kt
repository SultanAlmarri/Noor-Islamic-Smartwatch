package com.noor.wear.ui

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import com.noor.prayer.WatchConfig
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor
import com.noor.wear.complication.ComplicationBuilder
import com.noor.wear.data.ConfigRepository
import com.noor.wear.facepush.FacePush
import com.noor.wear.facepush.FaceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

internal val Gold = Color(0xFFE3C27E)
internal val GoldSoft = Color(0xFFF4E2B8)
internal val Ink = Color(0xFFF7F2E7)
internal val Mist = Color(0xFFB7C0D2)
internal val Night = Color(0xFF16233A)
internal val Kufi = FontFamily(Font(R.font.reem_kufi))

private val NoorWearColors = ColorScheme(
    primary = Gold,
    primaryDim = Color(0xFFB98F45),
    primaryContainer = Color(0xFF3A2E17),
    onPrimary = Color(0xFF241A08),
    onPrimaryContainer = GoldSoft,
    secondary = Mist,
    surfaceContainerLow = Color(0xFF0D1626),
    surfaceContainer = Night,
    surfaceContainerHigh = Color(0xFF213150),
    onSurface = Ink,
    onSurfaceVariant = Mist,
    outline = Color(0x66E3C27E),
    outlineVariant = Color(0x33E3C27E),
    background = Color.Black,
    onBackground = Ink,
)

@Composable
fun NoorWearUi(repo: ConfigRepository, faceSetup: Boolean) {
    val config by repo.config.collectAsState(initial = null)
    NoorWearContent(config, faceSetup = faceSetup)
}

/** The whole watch UI for a given configuration; [clock] is replaceable so previews and tests are deterministic. */
@Composable
fun NoorWearContent(config: WatchConfig?, clock: () -> Instant = Instant::now, showFaceSection: Boolean = true, faceSetup: Boolean = false) {
    val t = remember(config) { WearText(config) }
    val base = LocalContext.current
    val ctx = remember(base, config?.language) { base.localizedFor(config) }
    // Keep the activity's result registry reachable after LocalContext is swapped for the localized one.
    val registry = LocalActivityResultRegistryOwner.current
    MaterialTheme(colorScheme = NoorWearColors) {
        CompositionLocalProvider(
            *listOfNotNull(registry?.let { LocalActivityResultRegistryOwner provides it }).toTypedArray(),
            LocalContext provides ctx,
            LocalConfiguration provides ctx.resources.configuration,
            LocalLayoutDirection provides if (t.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            AppScaffold {
                when {
                    faceSetup -> FaceSetupScreen()
                    config == null -> SetupScreen()
                    else -> PrayerScreen(config, t, clock, showFaceSection)
                }
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(56.dp), tint = Color.Unspecified) }
            item {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium, color = Gold, textAlign = TextAlign.Center)
            }
            item {
                Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
            item { Text(stringResource(R.string.setup_waiting), style = MaterialTheme.typography.labelSmall, color = Mist.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun PrayerScreen(c: WatchConfig, t: WearText, clock: () -> Instant, showFaceSection: Boolean) {
    val now by produceState(clock()) {
        while (true) { value = clock(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    // Recalculated once per minute at most: the countdown itself only needs the cached state's instants.
    val minuteKey = now.epochSecond / 60
    val st = remember(c, minuteKey) { c.state(now) }
    val untilNext = Duration.between(now, st.next.at)
    val progress = st.current?.let {
        val total = Duration.between(it.at, st.next.at).seconds.toFloat()
        (Duration.between(it.at, now).seconds / total).coerceIn(0f, 1f)
    } ?: 0f
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    ScreenScaffold(scrollState = listState) { padding ->
        Box(Modifier.fillMaxSize()) {
            // The interval to the next prayer, drawn around the edge of the screen (gap at the top for the clock).
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 300f, endAngle = 240f,
                strokeWidth = 4.dp,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = Gold, trackColor = Gold.copy(alpha = 0.16f)),
            )
            ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                item { Hero(c, t, st.next.prayer, st.next.at, untilNext) }
                item {
                    Text(
                        t.place(c) + "  ·  " + t.hijri(HijriDate.of(st.today.date, c.hijriAdjust)),
                        style = MaterialTheme.typography.labelSmall, color = Mist, textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
                Prayer.entries.forEach { p ->
                    item {
                        val at = st.today.instant(p)
                        PrayerRow(p, if (at != null) t.time(at, c.zone) else "—", t,
                            isCurrent = st.current?.prayer == p && st.current?.date == st.today.date,
                            isNext = st.next.prayer == p && st.next.date == st.today.date)
                    }
                }
                item {
                    Text(
                        (if (t.arabic) c.profile.nameAr else c.profile.nameEn) + "\n" + stringResource(R.string.offline_note),
                        style = MaterialTheme.typography.labelSmall, color = Mist.copy(alpha = 0.75f), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (showFaceSection) item { FaceSection(autoStart = false) }
            }
        }
    }
}

@Composable
private fun Hero(c: WatchConfig, t: WearText, next: Prayer, at: Instant, until: Duration) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(stringResource(R.string.next).uppercase(), style = MaterialTheme.typography.labelSmall, color = Mist)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(ComplicationBuilder.iconRes(next)), null, Modifier.size(22.dp), tint = Gold)
            Spacer(Modifier.width(6.dp))
            Text(
                t.name(next),
                style = if (t.arabic) TextStyle(fontFamily = Kufi, fontSize = 26.sp) else MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = Gold,
            )
        }
        Text(t.time(at, c.zone), style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(
            t.countdown(until),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
            color = Ink,
        )
        Text(stringResource(R.string.remaining), style = MaterialTheme.typography.labelSmall, color = Mist)
    }
}

@Composable
private fun PrayerRow(p: Prayer, time: String, t: WearText, isCurrent: Boolean, isNext: Boolean) {
    val bg = when {
        isCurrent -> Night
        else -> Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(painterResource(ComplicationBuilder.iconRes(p)), null, Modifier.size(18.dp),
            tint = if (isCurrent || isNext) Gold else Mist.copy(alpha = if (p.isPrayer) 1f else 0.7f))
        Text(
            t.name(p), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = when { isNext -> Gold; isCurrent -> Ink; else -> Ink.copy(alpha = if (p.isPrayer) 0.92f else 0.7f) },
        )
        Text(time, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isCurrent || isNext) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isNext) Gold else Ink)
    }
}
