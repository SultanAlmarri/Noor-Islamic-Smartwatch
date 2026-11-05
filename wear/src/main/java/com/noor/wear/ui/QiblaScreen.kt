package com.noor.wear.ui

import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.prayer.WatchConfig
import com.noor.prayer.qibla.CompassIssue
import com.noor.prayer.qibla.CompassQuality
import com.noor.prayer.qibla.Qibla
import com.noor.prayer.qibla.QiblaResult
import com.noor.shared.compass.CompassAvailability
import com.noor.shared.compass.CompassReading
import com.noor.shared.compass.CompassSensor
import com.noor.wear.R
import com.noor.wear.WearText
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Within this many degrees the watch counts as facing the Qibla (the phone uses the same). */
private const val ALIGN_IN = 3.0
/** ...and stops counting only beyond this, so the confirmation does not flicker at the edge. */
private const val ALIGN_OUT = 6.0

/** The Qibla compass on the wrist: hold the watch level, turn until the Kaaba reaches the mark at 12 o'clock. */
@Composable
fun QiblaScreen(c: WatchConfig, t: WearText) {
    val ctx = LocalContext.current
    val sensor = remember { CompassSensor(ctx.applicationContext, SensorManager.SENSOR_DELAY_UI) }
    val flow = remember(c.point) { sensor.readings(c.point) }
    val reading by flow.collectAsStateWithLifecycle(initialValue = null)
    // Kept awake while the user holds the watch still and looks at it, but not forever.
    var keepOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(3 * 60_000L); keepOn = false }
    if (keepOn) KeepScreenOn()
    QiblaContent(c, t, reading, sensor.availability == CompassAvailability.Available)
}

@Composable
fun QiblaContent(c: WatchConfig, t: WearText, reading: CompassReading?, hasCompass: Boolean) {
    val q = remember(c.point) { Qibla.from(c.point) }
    val ctx = LocalContext.current
    val haptics = remember(ctx) { Haptics(ctx) }
    val heading = reading?.trueHeadingDeg
    val turn = heading?.let { Qibla.turn(it, q.bearingDeg) }
    val usable = reading != null && reading.assessment.quality != CompassQuality.POOR && CompassIssue.NOT_FLAT !in reading.assessment.issues
    val wasAligned = remember { mutableStateOf(false) }
    val aligned = usable && turn != null && abs(turn) <= (if (wasAligned.value) ALIGN_OUT else ALIGN_IN)
    SideEffect { wasAligned.value = aligned }
    LaunchedEffect(aligned) { if (aligned) haptics.success() }

    // No clock on this screen: 12 o'clock is the pointer.
    ScreenScaffold(timeText = {}) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!hasCompass || q.veryClose) {
                StaticQibla(q, t, if (q.veryClose) stringResource(R.string.qibla_in_makkah) else stringResource(R.string.qibla_no_compass))
                return@Box
            }
            Dial(heading, q.bearingDeg, aligned, usable, reading?.headingAccuracyDeg, t)
            Centre(q, t, turn, aligned, reading)
        }
    }
}

@Composable
private fun Dial(heading: Double?, qibla: Double, aligned: Boolean, usable: Boolean, accuracyDeg: Double?, t: WearText) {
    val accent by animateColorAsState(if (aligned) Success else Gold, tween(350), label = "qiblaAccent")
    val measurer = rememberTextMeasurer()
    val kaaba = painterResource(R.drawable.ic_qibla)
    val cardinals = if (t.arabic) listOf("ش", "ق", "ج", "غ") else listOf("N", "E", "S", "W")
    val cardinalStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.radialGradient(listOf(Night.copy(alpha = 0.9f), Color.Black), centre, r), r, centre)
        if (aligned) drawCircle(Brush.radialGradient(listOf(Success.copy(alpha = 0.22f), Color.Transparent), centre, r), r, centre)
        val h = (heading ?: 0.0).toFloat()
        val dim = if (usable && heading != null) 1f else 0.45f

        // The bezel turns with the world: ticks every 6°, longer every 30°, letters at the cardinal points.
        rotate(-h, centre) {
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val len = if (major) 9.dp.toPx() else 4.dp.toPx()
                val a = Math.toRadians(i * 6.0 - 90.0)
                val outer = r - 4.dp.toPx()
                drawLine(
                    Mist.copy(alpha = (if (major) 0.75f else 0.35f) * dim),
                    Offset(centre.x + (outer * cos(a)).toFloat(), centre.y + (outer * sin(a)).toFloat()),
                    Offset(centre.x + ((outer - len) * cos(a)).toFloat(), centre.y + ((outer - len) * sin(a)).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            cardinals.forEachIndexed { i, s ->
                val a = Math.toRadians(i * 90.0 - 90.0)
                val rr = r - 24.dp.toPx()
                val layout = measurer.measure(s, cardinalStyle.copy(color = (if (i == 0) Gold else Mist).copy(alpha = dim)))
                val p = Offset(centre.x + (rr * cos(a)).toFloat(), centre.y + (rr * sin(a)).toFloat())
                // Letters stay upright for the reader.
                rotate(h, p) { drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f)) }
            }
        }

        if (heading != null) {
            // The Kaaba on the rim, with a beam as wide as the sensor's own uncertainty.
            val rel = (qibla - heading).toFloat()
            val spread = (accuracyDeg ?: 8.0).coerceIn(3.0, 30.0).toFloat()
            rotate(rel, centre) {
                val inset = 4.dp.toPx()
                drawArc(accent.copy(alpha = 0.85f * dim), -90f - spread, spread * 2, useCenter = false,
                    topLeft = Offset(inset, inset) + Offset((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f),
                    size = Size(size.minDimension - inset * 2, size.minDimension - inset * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                val k = 22.dp.toPx()
                val kc = Offset(centre.x, centre.y - r + 30.dp.toPx())
                drawCircle(accent.copy(alpha = dim), k / 2f + 3.dp.toPx(), kc)
                translate(kc.x - k / 2f + 3.dp.toPx(), kc.y - k / 2f + 3.dp.toPx()) {
                    with(kaaba) { draw(Size(k - 6.dp.toPx(), k - 6.dp.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF241A08))) }
                }
            }
        }

        // The fixed mark at 12 o'clock: where the arm points.
        val tip = Offset(centre.x, 3.dp.toPx())
        val w = 7.dp.toPx(); val hgt = 10.dp.toPx()
        drawPath(Path().apply { moveTo(tip.x, tip.y + hgt); lineTo(tip.x - w, tip.y); lineTo(tip.x + w, tip.y); close() }, if (aligned) Success else Ink)
    }
}

@Composable
private fun Centre(q: QiblaResult, t: WearText, turn: Double?, aligned: Boolean, reading: CompassReading?) {
    val issues = reading?.assessment?.issues.orEmpty()
    val hint = when {
        reading == null -> stringResource(R.string.qibla_starting)
        CompassIssue.NOT_FLAT in issues -> stringResource(R.string.qibla_hold_level)
        reading.assessment.quality == CompassQuality.POOR -> stringResource(R.string.qibla_calibrate)
        else -> null
    }
    val main = when {
        turn == null -> "—"
        aligned -> stringResource(R.string.qibla_facing)
        else -> t.degrees(abs(turn))
    }
    val sub = when {
        turn == null || aligned -> null
        turn > 0 -> stringResource(R.string.qibla_turn_right)
        else -> stringResource(R.string.qibla_turn_left)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = listOfNotNull(main, sub, hint).joinToString(". ")
        },
    ) {
        if (aligned) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(22.dp), tint = Success)
        Text(main, style = if (aligned) MaterialTheme.typography.titleMedium else TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = Screen.sp(38f, 44f)),
            color = if (aligned) Success else Ink, textAlign = TextAlign.Center, maxLines = 2)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = Gold, textAlign = TextAlign.Center)
        Text(
            hint ?: stringResource(R.string.qibla_summary, t.degrees(q.bearingDeg), t.count(q.distanceKm.toLong())),
            style = MaterialTheme.typography.bodySmall, color = if (hint != null) GoldSoft else Mist, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp), maxLines = 3,
        )
    }
}

/** Without a compass (or inside Makkah): the bearing in words and numbers, nothing pretending to point. */
@Composable
private fun StaticQibla(q: QiblaResult, t: WearText, note: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Icon(painterResource(R.drawable.ic_qibla), null, Modifier.size(30.dp), tint = Gold)
        Text(t.degrees(q.bearingDeg), style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 40.sp), color = Ink)
        Text(stringResource(R.string.qibla_true_north), style = MaterialTheme.typography.labelMedium, color = Gold, textAlign = TextAlign.Center)
        Text(note, style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Keeps the display on while this is on screen (the user is holding the watch still, not touching it). */
@Composable
private fun KeepScreenOn() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
