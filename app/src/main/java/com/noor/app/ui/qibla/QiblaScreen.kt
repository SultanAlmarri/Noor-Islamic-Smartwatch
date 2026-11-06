package com.noor.app.ui.qibla

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.shared.compass.CompassAvailability
import com.noor.shared.compass.CompassReading
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.Noor
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.ScreenTitle
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter
import com.noor.prayer.geo.Flags
import com.noor.prayer.qibla.CompassIssue
import com.noor.prayer.qibla.CompassQuality
import com.noor.prayer.qibla.Qibla
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Within this many degrees the phone counts as facing the Qibla. */
const val ALIGNED_DEG = 3.0

@Composable
fun QiblaScreen(contentPadding: PaddingValues, onOpenLocation: () -> Unit, vm: QiblaViewModel = viewModel()) {
    val place by vm.place.collectAsStateWithLifecycle(initialValue = null)
    val reading by vm.readings.collectAsStateWithLifecycle(initialValue = null)
    val p = place ?: return
    QiblaContent(
        contentPadding, p, reading, vm.sensor.availability == CompassAvailability.NoSensor, onOpenLocation,
    )
}

/** Stateless Qibla screen for any place and reading, so it can be previewed and tested without sensors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaContent(
    contentPadding: PaddingValues,
    p: QiblaPlace,
    reading: CompassReading?,
    noSensor: Boolean,
    onOpenLocation: () -> Unit,
    calibrationOpenInitially: Boolean = false,
) {
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val f = Formatter(ar, p.settings.numerals, p.settings.timeFormat)
    val q = p.qibla
    var showCalibration by rememberSaveable { mutableStateOf(calibrationOpenInitially) }
    val haptics = LocalHapticFeedback.current

    val turn = if (q != null && reading != null) Qibla.turn(reading.trueHeadingDeg, q.bearingDeg) else null
    val aligned = turn != null && abs(turn) <= ALIGNED_DEG && reading?.assessment?.quality != CompassQuality.POOR &&
        CompassIssue.NOT_FLAT !in reading!!.assessment.issues
    LaunchedEffect(aligned) { if (aligned) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = Noor.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Noor.Gap),
    ) {
        val place = p.place
        ScreenTitle(stringResource(R.string.qibla_title),
            subtitle = if (place != null && q != null) (if (ar) place.nameAr ?: place.nameEn else place.nameEn) + "  ·  " + stringResource(R.string.qibla_distance, f.kilometres(q.distanceKm)) else null,
            leading = place?.countryCode?.let { cc -> Flags.emoji(cc)?.let { e -> { Text(e, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp)) } } })
        if (place == null || q == null) {
            GlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpenLocation)) {
                Text(stringResource(R.string.qibla_needs_location), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            return@Column
        }
        // Dial
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            CompassDial(heading = reading?.trueHeadingDeg, qibla = q.bearingDeg, aligned = aligned, f = f, modifier = Modifier.fillMaxSize(),
                uncertaintyDeg = reading?.headingAccuracyDeg)
            val notFlat = reading != null && CompassIssue.NOT_FLAT in reading.assessment.issues
            if (notFlat) FlatWarning()
        }

        // Status line under the dial
        val status = when {
            noSensor -> stringResource(R.string.qibla_no_sensor)
            q.veryClose -> stringResource(R.string.qibla_very_close)
            q.nearAntipode -> stringResource(R.string.qibla_antipode)
            reading == null -> stringResource(R.string.qibla_starting)
            CompassIssue.NOT_FLAT in reading.assessment.issues -> stringResource(R.string.qibla_hold_flat)
            aligned -> stringResource(R.string.qibla_aligned)
            turn!! > 0 -> stringResource(R.string.qibla_turn_right, f.degrees(abs(turn)))
            else -> stringResource(R.string.qibla_turn_left, f.degrees(abs(turn)))
        }
        val statusColor by animateColorAsState(if (aligned) c.success else c.textPrimary, label = "statusColor")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (aligned) Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.padding(end = 8.dp).size(22.dp))
            Text(status, style = MaterialTheme.typography.titleMedium, color = statusColor, textAlign = TextAlign.Center)
        }

        // Readings, side by side: where the Qibla is, where the phone points, and the difference (two decimals each).
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Readout(stringResource(R.string.qibla_bearing), f.degrees(q.bearingDeg), c.goldText, Modifier.weight(1f))
                VerticalHairline()
                Readout(stringResource(R.string.qibla_heading), reading?.let { f.degrees(it.trueHeadingDeg) } ?: "—", c.textPrimary, Modifier.weight(1f))
                VerticalHairline()
                Readout(stringResource(R.string.qibla_difference), turn?.let { f.signedDegrees(it) } ?: "—", if (aligned) c.success else c.textPrimary, Modifier.weight(1f))
            }
        }

        // Accuracy
        if (reading != null && !noSensor) AccuracyCard(reading, f) { showCalibration = true }

        Text(
            buildString {
                append(stringResource(R.string.qibla_method_note))
                if (reading != null) append(" ").append(stringResource(R.string.qibla_declination_note, f.degrees(reading.declinationDeg)))
            },
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showCalibration) {
        ModalBottomSheet(onDismissRequest = { showCalibration = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface) { CalibrationGuide(reading) }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color, modifier: Modifier) {
    val c = NoorTheme.colors
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 19.sp), color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun VerticalHairline() = Box(Modifier.width(0.5.dp).height(40.dp).background(NoorTheme.colors.separator))

@Composable
private fun AccuracyCard(reading: CompassReading, f: Formatter, onCalibrate: () -> Unit) {
    val c = NoorTheme.colors
    val q = reading.assessment.quality
    val color = when (q) { CompassQuality.GOOD -> c.success; CompassQuality.FAIR -> c.warning; CompassQuality.POOR -> c.danger }
    val label = stringResource(when (q) { CompassQuality.GOOD -> R.string.acc_good; CompassQuality.FAIR -> R.string.acc_fair; CompassQuality.POOR -> R.string.acc_poor })
    GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius)).clickable(onClick = onCalibrate), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.acc_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            // Three-segment meter
            val lit = when (q) { CompassQuality.GOOD -> 3; CompassQuality.FAIR -> 2; CompassQuality.POOR -> 1 }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { contentDescription = label }) {
                repeat(3) { i ->
                    Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < lit) color else c.textMuted.copy(alpha = 0.25f)))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = color)
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        val detail = buildList {
            reading.headingAccuracyDeg?.let { add(stringResource(R.string.acc_estimate, f.number(Math.round(it).toInt()))) }
            if (CompassIssue.MAGNETIC_INTERFERENCE in reading.assessment.issues) add(stringResource(R.string.acc_interference))
            if (CompassIssue.SENSOR_UNRELIABLE in reading.assessment.issues || CompassIssue.LOW_SENSOR_ACCURACY in reading.assessment.issues)
                add(stringResource(R.string.acc_needs_calibration))
            if (isEmpty()) add(stringResource(R.string.acc_status_only))
        }
        Text(detail.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun FlatWarning() {
    val c = NoorTheme.colors
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface.copy(alpha = 0.94f)).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.qibla_hold_flat), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(stringResource(R.string.qibla_hold_flat_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * The dial turns with the phone so north stays north; the Kaaba sits on the rim at the Qibla bearing, and a beam of light
 * runs to it from the centre. The beam is as wide as the compass is uncertain (the sensor's own estimate), so a wide beam
 * honestly says "somewhere in here". The fixed mark at the top is where the phone points.
 */
@Composable
fun CompassDial(heading: Double?, qibla: Double, aligned: Boolean, f: Formatter, modifier: Modifier = Modifier, uncertaintyDeg: Double? = null) {
    val c = NoorTheme.colors
    val kaaba = painterResource(R.drawable.ic_kaaba)
    val density = LocalDensity.current
    val labels = if (f.arabic) listOf("ش", "ق", "ج", "غ") else listOf("N", "E", "S", "W")
    val accent by animateColorAsState(if (aligned) c.success else c.gold, tween(400), label = "dialAccent")
    val glow by androidx.compose.animation.core.animateFloatAsState(if (aligned) 1f else 0f, tween(500), label = "alignedGlow")
    val halfBeam = (uncertaintyDeg ?: 8.0).coerceIn(3.0, 30.0)
    val qiblaLabel = stringResource(R.string.qibla_title) + " " + f.degrees(qibla)
    Canvas(modifier.semantics { contentDescription = qiblaLabel }) {
        val h = heading ?: 0.0
        val cx = size.width / 2; val cy = size.height / 2
        val r = size.minDimension / 2 * 0.80f
        val centre = Offset(cx, cy)
        fun pos(bearing: Double, radius: Float): Offset {
            val a = Math.toRadians(bearing - h)
            return Offset(cx + (radius * sin(a)).toFloat(), cy - (radius * cos(a)).toFloat())
        }
        // Face: a soft disc with a faint inner ring; glows gently when aligned.
        if (glow > 0f) drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.success.copy(alpha = 0.22f * glow), Color.Transparent), centre, r * 1.35f), r * 1.35f, centre)
        drawCircle(c.surface, r * 1.12f, centre)
        drawCircle(c.separator, r * 1.12f, centre, style = Stroke(1.dp.toPx()))
        drawCircle(c.separator, r * 0.62f, centre, style = Stroke(0.5.dp.toPx()))

        // The beam to the Kaaba.
        val beamStart = Math.toRadians(qibla - h - halfBeam - 90).toFloat()
        drawArc(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.06f)), centre, r),
            startAngle = Math.toDegrees(beamStart.toDouble()).toFloat(), sweepAngle = (2 * halfBeam).toFloat(), useCenter = true,
            topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
        )
        drawLine(accent, centre, pos(qibla, r - 30.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

        // Ticks: every 2°, longer every 10°, longest at the cardinals.
        for (deg in 0 until 360 step 2) {
            val cardinal = deg % 90 == 0; val ten = deg % 10 == 0
            val len = when { cardinal -> 14.dp.toPx(); ten -> 9.dp.toPx(); else -> 5.dp.toPx() }
            val outer = pos(deg.toDouble(), r * 1.08f); val inner = pos(deg.toDouble(), r * 1.08f - len)
            drawLine(
                when { deg == 0 -> c.danger; cardinal -> c.textPrimary; ten -> c.textSecondary; else -> c.textMuted.copy(alpha = 0.45f) },
                inner, outer, strokeWidth = if (cardinal) 2.dp.toPx() else if (ten) 1.2.dp.toPx() else 0.8.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; textSize = with(density) { 18.sp.toPx() }; isFakeBoldText = true
        }
        labels.forEachIndexed { i, l ->
            val pt = pos(i * 90.0, r * 1.08f - 30.dp.toPx())
            paint.color = (if (i == 0) c.danger else c.textPrimary).toArgb()
            drawContext.canvas.nativeCanvas.drawText(l, pt.x, pt.y + paint.textSize / 3, paint)
        }
        // Degree numbers every 30° between the cardinals.
        val small = android.graphics.Paint(paint).apply { textSize = with(density) { 11.sp.toPx() }; isFakeBoldText = false; color = c.textMuted.toArgb() }
        for (deg in 30 until 360 step 30) if (deg % 90 != 0) {
            val pt = pos(deg.toDouble(), r * 1.08f - 24.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(f.number(deg), pt.x, pt.y + small.textSize / 3, small)
        }

        // The Kaaba on the rim.
        val kaabaPos = pos(qibla, r * 1.08f + 2.dp.toPx())
        drawCircle(c.background, 24.dp.toPx(), kaabaPos)
        drawCircle(accent, 21.dp.toPx(), kaabaPos)
        val iconSize = 22.dp.toPx()
        translate(kaabaPos.x - iconSize / 2, kaabaPos.y - iconSize / 2) {
            with(kaaba) { draw(androidx.compose.ui.geometry.Size(iconSize, iconSize), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c.onGold)) }
        }
        // Centre hub.
        drawCircle(c.background, 9.dp.toPx(), centre)
        drawCircle(accent, 6.dp.toPx(), centre)
        // Fixed mark: where the phone points.
        val top = cy - r * 1.12f
        val notch = Path().apply {
            moveTo(cx, top + 6.dp.toPx())
            lineTo(cx - 9.dp.toPx(), top - 12.dp.toPx())
            lineTo(cx + 9.dp.toPx(), top - 12.dp.toPx())
            close()
        }
        drawPath(notch, if (aligned) c.success else c.textPrimary)
    }
}

/** Figure-eight calibration, animated, with plain instructions. */
@Composable
internal fun CalibrationGuide(reading: CompassReading?) {
    val c = NoorTheme.colors
    val t = rememberInfiniteTransition(label = "fig8")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.cal_title), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Canvas(Modifier.fillMaxWidth().height(170.dp)) { figureEight(phase, c.gold, c.textMuted.copy(alpha = 0.35f), c.textPrimary) }
        listOf(R.string.cal_step1, R.string.cal_step2, R.string.cal_step3, R.string.cal_step4, R.string.cal_step5).forEachIndexed { i, id ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.goldText, modifier = Modifier.width(22.dp))
                Text(stringResource(id), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        if (reading != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cal_live, stringResource(when (reading.assessment.quality) {
                CompassQuality.GOOD -> R.string.acc_good; CompassQuality.FAIR -> R.string.acc_fair; CompassQuality.POOR -> R.string.acc_poor })),
                style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

/** Lemniscate of Gerono traced by a small phone, with the path drawn faintly behind it. */
private fun DrawScope.figureEight(phase: Float, accent: Color, track: Color, phone: Color) {
    val cx = size.width / 2; val cy = size.height / 2
    val ax = size.width * 0.32f; val ay = size.height * 0.36f
    fun pt(t: Double) = Offset(cx + (ax * sin(t)).toFloat(), cy + (ay * sin(t) * cos(t)).toFloat())
    val path = Path()
    for (i in 0..200) { val p = pt(2 * PI * i / 200); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    drawPath(path, track, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    // Trail
    val trail = Path()
    for (i in 0..40) { val p = pt(2 * PI * (phase - i / 200.0)); if (i == 0) trail.moveTo(p.x, p.y) else trail.lineTo(p.x, p.y) }
    drawPath(trail, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    // Phone, turned along the path
    val t = 2 * PI * phase
    val p = pt(t); val p2 = pt(t + 0.01)
    val angle = Math.toDegrees(kotlin.math.atan2((p2.y - p.y).toDouble(), (p2.x - p.x).toDouble())).toFloat() + 90f
    rotate(angle, p) {
        val w = 18.dp.toPx(); val hh = 32.dp.toPx()
        drawRoundRect(phone, Offset(p.x - w / 2, p.y - hh / 2), androidx.compose.ui.geometry.Size(w, hh), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawRoundRect(accent, Offset(p.x - w / 2 + 2.dp.toPx(), p.y - hh / 2 + 3.dp.toPx()), androidx.compose.ui.geometry.Size(w - 4.dp.toPx(), hh - 8.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    }
}
