package com.noor.app.ui.qibla

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorMotion
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
    // (Made once for its settings, not with every compass reading: the screen recomposes with each one.)
    val f = remember(ar, p.settings.numerals, p.settings.timeFormat) { Formatter(ar, p.settings.numerals, p.settings.timeFormat) }
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
            GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenLocation)) {
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
            // 0.15: an unsure compass is not followed: the first thing said is what to do (the turn stays in the
            // readings below, for who wants it).
            reading.assessment.quality == CompassQuality.POOR -> stringResource(R.string.cal_title)
            aligned -> stringResource(R.string.qibla_aligned)
            turn!! > 0 -> stringResource(R.string.qibla_turn_right, f.degrees(abs(turn)))
            else -> stringResource(R.string.qibla_turn_left, f.degrees(abs(turn)))
        }
        val unsure = !noSensor && reading != null && reading.assessment.quality == CompassQuality.POOR && CompassIssue.NOT_FLAT !in reading.assessment.issues
        val statusColor by animateColorAsState(if (aligned) c.success else if (unsure) c.warning else c.textPrimary, label = "statusColor")
        Row(Modifier.fillMaxWidth().then(if (unsure) Modifier.clickable { showCalibration = true } else Modifier),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (aligned) Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.padding(end = 8.dp).size(22.dp))
            Text(status, style = MaterialTheme.typography.titleMedium, color = statusColor, textAlign = TextAlign.Center)
        }
        // 0.15: when the compass itself is unsure, the direction is not to be trusted yet: said here, where the eye
        // is, and not only in the accuracy card below. 0.22.1: shown, not only written (the owner: "it should display
        // the figure 8"): a phone gliding along a figure 8, with one line; it folds away once the compass is sure.
        AnimatedVisibility(unsure,
            enter = fadeIn(tween(NoorMotion.Standard)) + expandVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
            exit = fadeOut(tween(NoorMotion.Standard)) + shrinkVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
        ) { CalibrateNudge(onOpen = { showCalibration = true }) }

        // Readings, side by side: where the Qibla is, where the phone points, and the difference (two decimals each).
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Readout(stringResource(R.string.qibla_bearing), f.degrees(q.bearingDeg), c.goldText, Modifier.weight(1f))
                VerticalHairline()
                Readout(stringResource(R.string.qibla_heading), reading?.let { f.degrees(it.trueHeadingDeg) } ?: "—", c.textPrimary, Modifier.weight(1f))
                VerticalHairline()
                Readout(stringResource(R.string.qibla_difference), turn?.let { f.signedDegrees(it) } ?: "—", if (aligned) c.success else c.textPrimary, Modifier.weight(1f))
            }
        }

        // Accuracy
        if (reading != null && !noSensor) AccuracyCard(reading, f) { showCalibration = true }

        // (0.16: the note on the method (true north, the ellipsoid, the decimals, the declination) is gone: the owner,
        // "is this really even necessary to tell the user?" The method is in the docs; the screen says what to do.)
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
    // (0.21) A name may take two lines (with large text "Qibla direction" lost its second word, "Difference" its last
    // letter); the three values stay on one line with each other, at the foot.
    Column(modifier.fillMaxHeight().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 19.sp), color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun VerticalHairline() = Box(Modifier.width(0.5.dp).height(40.dp).background(NoorTheme.colors.separator))

/**
 * How sure the compass is, from what the phone's sensors report. 0.9.6: the level shown is always the one the latest
 * sensor reading gives, at once (no hold, no fade): a phone brought near metal reads Poor the moment the field is off,
 * and Good again the moment it is not. 0.9.7: the reading's verdict comes from a CompassTracker, so a measure sitting
 * near a limit no longer makes the label flicker; the label has a fixed slot, so nothing beside it moves. The box keeps one height whatever the text says, so nothing jumps. When the
 * sensor says it is unreliable, the label says to calibrate.
 */
@Composable
internal fun AccuracyCard(reading: CompassReading, f: Formatter, onCalibrate: () -> Unit) {
    val c = NoorTheme.colors
    val shown = reading.assessment
    val label = accuracyLabel(shown)
    GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCalibrate), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.acc_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            AccuracyMeter(shown, small = false, modifier = Modifier.semantics { contentDescription = label })
            Spacer(Modifier.width(10.dp))
            // 0.9.7: the label sits in a slot as wide as the widest of the four, so the meter never moves sideways
            // when the word changes.
            val style = MaterialTheme.typography.titleSmall
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val labels = listOf(R.string.acc_good, R.string.acc_fair, R.string.acc_poor, R.string.acc_calibrate).map { stringResource(it) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            // (Measured once for these words, this style and this measurer, not with every compass reading.)
            val slot = remember(labels, style, density, measurer) {
                with(density) { labels.maxOf { measurer.measure(it, style, maxLines = 1).size.width }.toDp() }
            }
            Text(label, style = style, color = accuracyColor(shown), maxLines = 1, modifier = Modifier.width(slot))
            // (Mirrored in Arabic since 0.14: it pointed right in both languages.)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        val detail = buildList {
            (reading.shownAccuracyDeg ?: reading.headingAccuracyDeg?.let { com.noor.prayer.qibla.CompassDisplay.displayedEstimate(it) })?.let { add(stringResource(R.string.acc_estimate, f.number(it))) }
            if (CompassIssue.MAGNETIC_INTERFERENCE in shown.issues) add(stringResource(R.string.acc_interference))
            if (CompassIssue.SENSOR_UNRELIABLE in shown.issues || CompassIssue.LOW_SENSOR_ACCURACY in shown.issues)
                add(stringResource(R.string.acc_needs_calibration))
            if (isEmpty()) add(stringResource(R.string.acc_status_only))
        }.joinToString(" ")
        // Three lines are always reserved, so the card never grows or shrinks as the explanation changes.
        Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, minLines = 3, maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun accuracyLabel(a: com.noor.prayer.qibla.CompassAssessment): String = stringResource(when {
    CompassIssue.SENSOR_UNRELIABLE in a.issues -> R.string.acc_calibrate
    a.quality == CompassQuality.GOOD -> R.string.acc_good
    a.quality == CompassQuality.FAIR -> R.string.acc_fair
    else -> R.string.acc_poor
})

@Composable
private fun accuracyColor(a: com.noor.prayer.qibla.CompassAssessment): Color {
    val c = NoorTheme.colors
    return when (a.quality) { CompassQuality.GOOD -> c.success; CompassQuality.FAIR -> c.warning; CompassQuality.POOR -> c.danger }
}

/** The three-segment meter: one, two or three lit, in the level's colour. [small] for the calibration tips. */
@Composable
internal fun AccuracyMeter(a: com.noor.prayer.qibla.CompassAssessment, small: Boolean, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    val color = accuracyColor(a)
    val lit = when (a.quality) { CompassQuality.GOOD -> 3; CompassQuality.FAIR -> 2; CompassQuality.POOR -> 1 }
    Row(horizontalArrangement = Arrangement.spacedBy(if (small) 3.dp else 4.dp), modifier = modifier) {
        repeat(3) { i ->
            Box(Modifier.width(if (small) 14.dp else 22.dp).height(if (small) 6.dp else 8.dp).clip(RoundedCornerShape(4.dp))
                .background(if (i < lit) color else c.textMuted.copy(alpha = 0.25f)))
        }
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
    // Made once, not with every frame (the dial is drawn again with each compass reading): the paints of the letters
    // and numbers, the numbers' text, and the two gradients (kept until their colours or the dial's size change).
    val paint = remember(density) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; textSize = with(density) { 18.sp.toPx() }; isFakeBoldText = true
        }
    }
    val small = remember(paint, c.textMuted) {
        android.graphics.Paint(paint).apply { textSize = with(density) { 11.sp.toPx() }; isFakeBoldText = false; color = c.textMuted.toArgb() }
    }
    val numbers = remember(f) { (30 until 360 step 30).filter { it % 90 != 0 }.map { it to f.number(it) } }
    val glowBrush = remember { RadialCache() }
    val beamBrush = remember { RadialCache() }
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
        if (glow > 0f) drawCircle(glowBrush.get(c.success.copy(alpha = 0.22f * glow), Color.Transparent, centre, r * 1.35f), r * 1.35f, centre)
        drawCircle(c.surface, r * 1.12f, centre)
        drawCircle(c.separator, r * 1.12f, centre, style = Stroke(1.dp.toPx()))
        drawCircle(c.separator, r * 0.62f, centre, style = Stroke(0.5.dp.toPx()))

        // The beam to the Kaaba.
        val beamStart = Math.toRadians(qibla - h - halfBeam - 90).toFloat()
        drawArc(
            beamBrush.get(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.06f), centre, r),
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
        labels.forEachIndexed { i, l ->
            val pt = pos(i * 90.0, r * 1.08f - 30.dp.toPx())
            paint.color = (if (i == 0) c.danger else c.textPrimary).toArgb()
            drawContext.canvas.nativeCanvas.drawText(l, pt.x, pt.y + paint.textSize / 3, paint)
        }
        // Degree numbers every 30° between the cardinals.
        for ((deg, text) in numbers) {
            val pt = pos(deg.toDouble(), r * 1.08f - 24.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(text, pt.x, pt.y + small.textSize / 3, small)
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

/** A two-colour radial gradient, made again only when its colours, centre or radius change (the dial's draw phase). */
private class RadialCache {
    private var from = Color.Unspecified
    private var to = Color.Unspecified
    private var centre = Offset.Unspecified
    private var radius = Float.NaN
    private var brush: androidx.compose.ui.graphics.Brush? = null

    fun get(from: Color, to: Color, centre: Offset, radius: Float): androidx.compose.ui.graphics.Brush {
        brush?.let { if (from == this.from && to == this.to && centre == this.centre && radius == this.radius) return it }
        this.from = from; this.to = to; this.centre = centre; this.radius = radius
        return androidx.compose.ui.graphics.Brush.radialGradient(listOf(from, to), centre, radius).also { brush = it }
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
        // Live, while calibrating: the same meter, small, so the user sees it improve as they move the phone.
        if (reading != null) {
            val shown = reading.assessment
            val label = accuracyLabel(shown)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.cal_live, ""), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                AccuracyMeter(shown, small = true)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = accuracyColor(shown))
            }
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

/** Where the still drawing (animations off) shows the phone: on the right lobe's lower curve, its trail behind it. */
internal const val NUDGE_STILL_PHASE = 0.16f

/** One loop of the figure 8, in milliseconds. */
private const val NUDGE_LOOP_MS = 2800

/**
 * (0.22.1) The unsure compass's message, under the dial: the gesture drawn, a phone gliding along a figure 8 with a
 * fading trail behind it, over one line that says the same; tapping opens the calibration guide. The loop runs only
 * while this is shown and animations are allowed ([LocalAmbientMotion]); otherwise it is a still drawing of the same
 * moment. [phase] fixes the moment (0..1 of a loop), for renders.
 */
@Composable
internal fun CalibrateNudge(onOpen: () -> Unit, modifier: Modifier = Modifier, phase: Float? = null) {
    val c = NoorTheme.colors
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val line = stringResource(R.string.qibla_figure8)
    GlassCard(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {},
        padding = 0.dp,
    ) {
        val drawing = Modifier.fillMaxWidth().height(98.dp).padding(top = 10.dp)
        when {
            phase != null -> FigureEightCanvas({ phase }, rtl, drawing)
            LocalAmbientMotion.current -> {
                val t = rememberInfiniteTransition(label = "nudge")
                val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(NUDGE_LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "nudgePhase")
                // (Read in the draw phase only: each frame draws again, nothing is composed again.)
                FigureEightCanvas({ p.value }, rtl, drawing)
            }
            else -> FigureEightCanvas({ NUDGE_STILL_PHASE }, rtl, drawing)
        }
        // The line centred in the card, the chevron (it opens the guide) at the card's end, as on a row.
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), contentAlignment = Alignment.CenterEnd) {
            Text(line, style = MaterialTheme.typography.titleSmall, color = c.warning, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        }
    }
}

@Composable
private fun FigureEightCanvas(phase: () -> Float, rtl: Boolean, modifier: Modifier) {
    val c = NoorTheme.colors
    Canvas(modifier) { nudgeFigure(phase(), rtl, c.gold, c.textMuted.copy(alpha = 0.30f), c.textPrimary, c.surface) }
}

/**
 * A lemniscate of Bernoulli (made a little fuller), drawn as a faint line; a phone, held screen up as in the hand,
 * travels it and tilts as it goes, with a trail that fades behind it. Strokes in plain colours, faded with alpha (a gradient brush on a stroke
 * stops the emulator's graphics: HANDOVER.md section 28).
 */
private fun DrawScope.nudgeFigure(phase: Float, rtl: Boolean, trail: Color, track: Color, body: Color, screen: Color) {
    val cx = size.width / 2; val cy = size.height / 2
    val phoneH = 26.dp.toPx(); val phoneW = 15.dp.toPx()
    // Half the width, so the figure (about 0.46 of it high on each side of the middle) leaves room for the phone.
    val a = minOf(size.width * 0.30f, (size.height / 2 - phoneH * 0.55f) / 0.46f, 96.dp.toPx())
    val dir = if (rtl) -1f else 1f
    fun pt(t: Double): Offset {
        val s = sin(t); val k = 1 + s * s
        return Offset(cx + dir * (a * cos(t) / k).toFloat(), cy + (1.3 * a * s * cos(t) / k).toFloat())
    }
    // The figure.
    val path = Path()
    for (i in 0..160) { val p = pt(2 * PI * i / 160); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    path.close()
    drawPath(path, track, style = Stroke(1.5.dp.toPx()))
    // The trail: short segments behind the phone, thinner and fainter the further back. Drawn from the tail to the
    // head in a layer of its own, each replacing what is under it (Src), so where two meet no seam is drawn twice.
    val t0 = 2 * PI * phase
    val n = 48; val span = 2 * PI * 0.40
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
    for (i in n downTo 1) {
        val fade = 1 - i / n.toFloat()
        val from = pt(t0 - span * i / n); val to = pt(t0 - span * (i - 1) / n)
        drawLine(trail.copy(alpha = trail.alpha * Math.pow(fade.toDouble(), 1.3).toFloat()), from, to, strokeWidth = (1.3f + 3.4f * fade).dp.toPx(),
            cap = StrokeCap.Round, blendMode = androidx.compose.ui.graphics.BlendMode.Src)
    }
    drawContext.canvas.restore()
    // The phone as it is held for this (the owner's reference video): in the hand, screen up, its top pointing away, seen
    // at a slant from above; it travels the figure and tilts as it goes, rolling towards the outside of each loop and
    // pitching as it climbs and falls (the gesture turns the phone about every axis). A true 3D turn (Android's
    // Camera), its thickness showing on the side it rolls away from. (0.22: leaning in the picture's plane, then held
    // upright, then turned along the line like a car: none of them looked like a phone in a hand.)
    val p = pt(t0)
    val roll = (30.0 * cos(t0) * dir).toFloat()
    val pitch = (38.0 + 14.0 * sin(2 * t0)).toFloat()
    // A soft glow under the phone (a filled circle: a gradient on a fill is safe, only on a stroke it is not).
    val glow = 24.dp.toPx()
    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(trail.copy(alpha = 0.30f), trail.copy(alpha = 0f)), p, glow), glow, p)
    val w = phoneW * 1.25f; val h = phoneH * 1.25f; val depth = 2.8.dp.toPx()
    // The 3D turn worked out here (not Android's Camera, which software renders ignore): a point of the phone (x right,
    // y down, z away from the viewer, the phone's middle at 0) pitched about its width (its top away from the viewer),
    // rolled about its length, then seen in perspective from a distance of a few phone heights.
    val ca = cos(Math.toRadians(pitch.toDouble())); val sa = sin(Math.toRadians(pitch.toDouble()))
    val cb = cos(Math.toRadians(roll.toDouble())); val sb = sin(Math.toRadians(roll.toDouble()))
    val dist = h * 4.5
    fun proj(x: Double, y: Double, z: Double): Offset {
        val y1 = y * ca - z * sa; val z1 = y * sa + z * ca
        val x2 = x * cb + z1 * sb; val z2 = -x * sb + z1 * cb
        val k = dist / (dist + z2)
        return Offset(p.x + (x2 * k).toFloat(), p.y + (y1 * k).toFloat())
    }
    // A rounded rectangle's outline (half sizes hx, hy, corner radius rr), at depth z, as a projected path.
    fun outline(hx: Double, hy: Double, rr: Double, z: Double, dy: Double = 0.0): Path {
        val path = Path()
        val corners = listOf(Triple(hx - rr, -hy + rr, -90.0), Triple(hx - rr, hy - rr, 0.0), Triple(-hx + rr, hy - rr, 90.0), Triple(-hx + rr, -hy + rr, 180.0))
        var first = true
        for ((cx0, cy0, start) in corners) for (k in 0..6) {
            val a = Math.toRadians(start + 15.0 * k)
            val q = proj(cx0 + rr * cos(a), dy + cy0 + rr * sin(a), z)
            if (first) { path.moveTo(q.x, q.y); first = false } else path.lineTo(q.x, q.y)
        }
        path.close()
        return path
    }
    val hx = w / 2.0; val hy = h / 2.0; val rr = 4.dp.toPx().toDouble()
    val side = body.copy(red = body.red * 0.70f, green = body.green * 0.70f, blue = body.blue * 0.70f)
    // Its back and sides (a few slices make a solid edge), then its face, screen and needle.
    for (k in 4 downTo 1) drawPath(outline(hx, hy, rr, depth * k / 4.0), side)
    drawPath(outline(hx, hy, rr, 0.0), body)
    val inset = 1.8.dp.toPx().toDouble()
    drawPath(outline(hx - inset, hy - inset * 1.8, 2.6.dp.toPx().toDouble(), 0.0), screen)
    // On its screen, a compass needle in gold, pointing to the phone's top.
    val nh = 7.dp.toPx().toDouble(); val nw = 2.8.dp.toPx().toDouble()
    drawPath(Path().apply {
        val a0 = proj(0.0, -nh, 0.0); val a1 = proj(nw, 0.0, 0.0); val a2 = proj(0.0, nh, 0.0); val a3 = proj(-nw, 0.0, 0.0)
        moveTo(a0.x, a0.y); lineTo(a1.x, a1.y); lineTo(a2.x, a2.y); lineTo(a3.x, a3.y); close()
    }, trail.copy(alpha = 1f))
}
