package com.noor.wear.ui

import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
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

/** The field the screen turns to on arrival: Ink reads on it at 7:1 and more. */
private val ArrivalCentre = Color(0xFF0E5E3B)
private val ArrivalEdge = Color(0xFF042417)

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
            Dial(heading, q.bearingDeg, turn, aligned, usable, reading?.headingAccuracyDeg, t)
            Centre(q, t, turn, aligned, reading)
        }
    }
}

@Composable
private fun Dial(heading: Double?, qibla: Double, turn: Double?, aligned: Boolean, usable: Boolean, accuracyDeg: Double?, t: WearText) {
    // On arrival the whole screen turns to a green field and the marks to white.
    val accent by animateColorAsState(if (aligned) Ink else Gold, tween(350), label = "qiblaAccent")
    val arrival by animateFloatAsState(if (aligned) 1f else 0f, tween(450), label = "qiblaArrival")
    val measurer = rememberTextMeasurer()
    val kaaba = painterResource(R.drawable.ic_qibla)
    val cardinals = if (t.arabic) listOf("ش", "ق", "ج", "غ") else listOf("N", "E", "S", "W")
    val cardinalStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        if (arrival > 0f) drawCircle(Brush.radialGradient(listOf(ArrivalCentre, ArrivalEdge), centre, r), r, centre, alpha = arrival)
        val h = (heading ?: 0.0).toFloat()
        val dim = if (usable && heading != null) 1f else 0.4f

        // The bezel turns with the world: a tick every 5°, longer and brighter every 30°, letters at the cardinal points.
        rotate(-h, centre) {
            for (i in 0 until 72) {
                val major = i % 6 == 0
                val len = if (major) 10.dp.toPx() else 5.dp.toPx()
                val a = Math.toRadians(i * 5.0 - 90.0)
                val outer = r - 5.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = (if (major) 0.9f else 0.32f) * dim),
                    Offset(centre.x + (outer * cos(a)).toFloat(), centre.y + (outer * sin(a)).toFloat()),
                    Offset(centre.x + ((outer - len) * cos(a)).toFloat(), centre.y + ((outer - len) * sin(a)).toFloat()),
                    strokeWidth = if (major) 2.2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            cardinals.forEachIndexed { i, s ->
                val a = Math.toRadians(i * 90.0 - 90.0)
                // Just inside the Kaaba's track, so the two never meet (in Dubai the Qibla is almost due West).
                val rr = r - 33.dp.toPx()
                val layout = measurer.measure(s, cardinalStyle.copy(color = (if (i == 0) Tint.Red else Ink).copy(alpha = dim)))
                val p = Offset(centre.x + (rr * cos(a)).toFloat(), centre.y + (rr * sin(a)).toFloat())
                // Letters stay upright for the reader.
                rotate(h, p) { drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f)) }
            }
        }

        val inset = 3.dp.toPx()
        val arcTopLeft = Offset(inset, inset) + Offset((size.width - size.minDimension) / 2f, (size.height - size.minDimension) / 2f)
        val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
        if (heading != null) {
            // The way to turn: a faint arc along the rim from the mark at 12 o'clock to the Kaaba.
            if (turn != null && usable && !aligned) {
                drawArc(Gold.copy(alpha = 0.30f), -90f, turn.toFloat(), useCenter = false, topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            // The Kaaba on the rim, with a beam as wide as the sensor's own uncertainty.
            val rel = (qibla - heading).toFloat()
            val spread = (accuracyDeg ?: 8.0).coerceIn(3.0, 30.0).toFloat()
            rotate(rel, centre) {
                drawArc(accent.copy(alpha = 0.9f * dim), -90f - spread, spread * 2, useCenter = false, topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                val k = 22.dp.toPx()
                val kc = Offset(centre.x, centre.y - r + 14.dp.toPx())
                drawCircle(accent.copy(alpha = dim), k / 2f, kc)
                val icon = 14.dp.toPx()
                translate(kc.x - icon / 2f, kc.y - icon / 2f) {
                    with(kaaba) { draw(Size(icon, icon), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (aligned) ArrivalEdge else OnGold)) }
                }
            }
        }

        // The fixed mark at 12 o'clock: where the arm points.
        val markTop = Offset(centre.x, 2.dp.toPx())
        drawLine(Ink, markTop, markTop + Offset(0f, 8.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
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
    // 0.25: while the compass is starting or the wrist is tilted there is no figure to trust: the instruction
    // stands alone. With a poor calibration the figure stays, quiet, under the instruction to calibrate.
    val notLevel = CompassIssue.NOT_FLAT in issues
    val main = when {
        turn == null || notLevel -> null
        aligned -> stringResource(R.string.qibla_facing)
        else -> t.degrees(abs(turn))
    }
    val sub = when {
        turn == null || aligned || notLevel -> null
        turn > 0 -> stringResource(R.string.qibla_turn_right)
        else -> stringResource(R.string.qibla_turn_left)
    }
    // Short on the dial, so it stays clear of the turning letters; in full for a screen reader.
    val summary = stringResource(R.string.qibla_summary_short, t.degrees(q.bearingDeg), t.count(q.distanceKm.toLong()))
    val spokenSummary = stringResource(R.string.qibla_summary, t.degrees(q.bearingDeg), t.count(q.distanceKm.toLong()))
    // The arrival: the check lands with a spring.
    val land by animateFloatAsState(if (aligned) 1f else 0.6f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "qiblaLand")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 52.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = listOfNotNull(main, sub, hint ?: spokenSummary).joinToString(". ")
        },
    ) {
        if (aligned && main != null) {
            Box(Modifier.padding(bottom = 4.dp).size(38.dp).scale(land).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(26.dp), tint = ArrivalCentre)
            }
            Text(main, style = Type.title, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
        } else if (main != null) {
            // Three figures ("120°") a size down, so they stay inside the ring of letters.
            Numeral(main, if (turn != null && abs(turn) >= 99.5) Screen.sp(38f, 50f) else Screen.sp(46f, 56f), Modifier.padding(bottom = 2.dp),
                color = if (hint != null) Quiet else Ink)
        }
        if (sub != null && turn != null) {
            // The arrow is on the side it points to, whatever the language: right is right.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (turn < 0) Icon(painterResource(R.drawable.ic_arrow), null, Modifier.size(16.dp).graphicsLayer { scaleX = -1f }, tint = Gold)
                    Text(sub, style = Type.row, color = Gold, textAlign = TextAlign.Center, maxLines = 1)
                    if (turn > 0) Icon(painterResource(R.drawable.ic_arrow), null, Modifier.size(16.dp), tint = Gold)
                }
            }
        }
        Text(
            hint ?: summary,
            style = if (hint != null && main == null) Type.row else if (hint != null) Type.detail else Type.note,
            color = if (hint != null) GoldSoft else if (aligned) Ink else Quiet, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp), maxLines = 3,
        )
    }
}

/** Without a compass (or inside Makkah): the bearing in words and numbers, nothing pretending to point. */
@Composable
private fun StaticQibla(q: QiblaResult, t: WearText, note: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        RoundSign(R.drawable.ic_qibla, Tint.Gold, size = 34.dp)
        Numeral(t.degrees(q.bearingDeg), Screen.sp(44f, 52f), Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.qibla_true_north), style = Type.row, color = Gold, textAlign = TextAlign.Center)
        Text(note, style = Type.note, color = Quiet, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
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
