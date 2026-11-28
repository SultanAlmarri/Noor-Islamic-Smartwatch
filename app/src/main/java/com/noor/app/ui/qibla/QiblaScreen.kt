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
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.NoorSheet
import com.noor.app.ui.settings.SectionFooter
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

    // 0.26: the whole page on one screen (the owner: "make the compass tab fit all in the screen, like I don't have to
    // scroll up and down for the compass accuracy"), and nothing on it to read that a person does not need (the owner:
    // "remove unnecessary stuff ... people don't need to read this": the note under the readings, the exact bearing to
    // a tenth and the phone's estimate of its error in words, is gone; how sure the compass is stays, as its meter).
    // The dial takes what the place line, the line that says what to do and the readings leave, so it grows, never
    // squeezed (the owner: "don't make everything look bad and squished"); only a screen too short to give it a dial a hand can use
    // (QiblaFit.MinDial) scrolls. How far the page reaches is the screen less the tab's title and the bars.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val spacing = 12.dp
    val titleHeight = 16.dp + with(density) { 44.sp.toDp() }
    val room = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding() - titleHeight - spacing * 2 - 8.dp
    NoorPage(stringResource(R.string.qibla_title), contentPadding, spacing = spacing) {
        val place = p.place
        if (place == null || q == null) {
            GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenLocation)) {
                Text(stringResource(R.string.qibla_needs_location), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            return@NoorPage
        }
        val notFlat = reading != null && CompassIssue.NOT_FLAT in reading.assessment.issues
        // Whole degrees (0.25): a phone's compass is good to a few degrees at best.
        val status = when {
            noSensor -> stringResource(R.string.qibla_no_sensor)
            q.veryClose -> stringResource(R.string.qibla_very_close)
            q.nearAntipode -> stringResource(R.string.qibla_antipode)
            reading == null -> stringResource(R.string.qibla_starting)
            // (0.26: "Hold your phone flat" is said once, here under the dial; until then it was also written across the
            // dial's needle, with a line of how under it: the review found it said twice.)
            notFlat -> stringResource(R.string.qibla_hold_flat)
            // 0.15: an unsure compass is not followed: the first thing said is what to do.
            reading.assessment.quality == CompassQuality.POOR -> stringResource(R.string.cal_title)
            aligned -> stringResource(R.string.qibla_aligned)
            turn!! > 0 -> stringResource(R.string.qibla_turn_right, f.wholeDegrees(abs(turn)))
            else -> stringResource(R.string.qibla_turn_left, f.wholeDegrees(abs(turn)))
        }
        val unsure = !noSensor && reading != null && reading.assessment.quality == CompassQuality.POOR && !notFlat
        val statusColor by animateColorAsState(if (aligned) c.success else if (unsure) c.warning else c.textPrimary, label = "statusColor")
        val turning = !aligned && !unsure && !notFlat && turn != null && !noSensor && !q.veryClose && !q.nearAntipode

        QiblaFit(room) {
            // 1. The place and how far the Kaaba is: one quiet line that belongs to the title.
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Flags.emoji(place.countryCode)?.let { e -> Text(e, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp)) }
                PlaceAndDistance(if (ar) place.nameAr ?: place.nameEn else place.nameEn, stringResource(R.string.qibla_distance, f.kilometres(q.distanceKm)))
            }
            // 2. The dial, as large as the screen allows (QiblaFit gives it its size).
            Box(Modifier.testTag("qiblaDial"), contentAlignment = Alignment.Center) {
                CompassDial(heading = reading?.trueHeadingDeg, qibla = q.bearingDeg, aligned = aligned, f = f, modifier = Modifier.fillMaxSize(),
                    uncertaintyDeg = reading?.headingAccuracyDeg)
            }
            // 3. What to do: "Turn right 83°" with the way to turn drawn beside it, or "Facing the Qibla"; while the
            // compass is unsure, the figure 8 that fixes it (0.22.1) in the line's place. (0.27) Both are always laid out
            // in one slot as tall as the taller, one shown and the other invisible, so the compass never moves when the
            // accuracy changes between Fair and Poor (the owner: "why does it go up and down? It should stay stationary").
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val nudge by animateFloatAsState(if (unsure) 1f else 0f, tween(NoorMotion.Standard), label = "nudge")
                Box(Modifier.graphicsLayer { alpha = nudge }.then(if (unsure) Modifier else Modifier.clearAndSetSemantics { })) {
                    CalibrateNudge(onOpen = { if (unsure) showCalibration = true }, compact = true, phase = if (unsure) null else NUDGE_STILL_PHASE)
                }
                if (nudge < 1f) Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).graphicsLayer { alpha = 1f - nudge }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (aligned) Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.padding(end = 8.dp).size(24.dp))
                    if (turning) Icon(
                        if (turn!! > 0) Icons.Rounded.RotateRight else Icons.Rounded.RotateLeft, null, tint = c.goldText,
                        modifier = Modifier.padding(end = 8.dp).size(24.dp),
                    )
                    if (status != null) Text(status, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = statusColor, textAlign = TextAlign.Center)
                }
            }
            // 4. The readings in one card: where the Qibla is, where the phone points, and how sure the compass is
            // (until 0.25 the accuracy had a card of its own at the foot, under the fold of most phones). Tapping the
            // accuracy opens the calibration guide.
            GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Readout(stringResource(R.string.qibla_cell_qibla), f.wholeDegrees(q.bearingDeg), c.goldText, Modifier.weight(1f))
                    // (0.25, second review: a phone without a compass has no heading and no accuracy.)
                    if (!noSensor) {
                        VerticalHairline()
                        Readout(stringResource(R.string.qibla_cell_phone), reading?.let { f.wholeDegrees(it.trueHeadingDeg) } ?: "—", if (aligned) c.success else c.textPrimary, Modifier.weight(1f))
                        VerticalHairline()
                        AccuracyReadout(reading, Modifier.weight(1f)) { showCalibration = true }
                    }
                }
            }
        }
    }
    }

    if (showCalibration) {
        // 0.25: the app's own sheet (its grabber, its glass), three steps, and a button that closes it.
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        NoorSheet(onDismiss = { showCalibration = false }, state = sheet) {
            CalibrationGuide(reading, onDone = { scope.launch { sheet.hide() }.invokeOnCompletion { showCalibration = false } }, f = f)
        }
    }
}

/**
 * Where the user is and how far the Kaaba is: on one line, "Dubai · 1,633 km to the Kaaba", when it fits; otherwise
 * the distance on a line of its own, without the dot (0.25, second review: a long place name broke the line after the
 * dot, which then hung at the end of the first line).
 */
@Composable
private fun PlaceAndDistance(place: String, distance: String) {
    val c = NoorTheme.colors
    val style = MaterialTheme.typography.bodyMedium
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints {
        val one = "$place  ·  $distance"
        val room = constraints.maxWidth
        val fits = remember(one, style, room, measurer) { measurer.measure(one, style, maxLines = 1, softWrap = false).size.width <= room }
        if (fits) Text(one, style = style, color = c.textSecondary)
        else Column {
            Text(place, style = style, color = c.textSecondary)
            Text(distance, style = style, color = c.textSecondary)
        }
    }
}

private const val LRM = "‎"

/**
 * A direction in whole degrees, "258°" (0.25): what a phone's compass can honestly show. 359.6 reads 0, not 360.
 * Left-to-right marks keep the sign beside its number inside an Arabic sentence, as [Formatter.degrees] does.
 */
internal fun Formatter.wholeDegrees(d: Double): String = LRM + number(((Math.round(d) % 360 + 360) % 360).toInt()) + "°" + LRM

/** The Qibla's own bearing to a tenth of a degree, "258.3°": it is calculated, not measured by the phone. */
internal fun Formatter.tenthDegrees(d: Double): String {
    val tenths = (Math.round(d * 10) % 3600 + 3600) % 3600
    val whole = number((tenths / 10).toInt()); val frac = number((tenths % 10).toInt())
    // (The decimal mark of the digits in use: "٫" between Arabic-Indic digits.)
    return LRM + whole + (if (whole.any { it in '٠'..'٩' }) "٫" else ".") + frac + "°" + LRM
}

@Composable
private fun Readout(label: String, value: String, color: Color, modifier: Modifier) {
    val c = NoorTheme.colors
    // (0.21) A name may take two lines (with large text "Qibla direction" lost its second word); the values stay on one
    // line with each other, at the foot.
    Column(modifier.fillMaxHeight().padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 22.sp), color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * How sure the compass is, from what the phone's sensors report: (0.26) the third reading of the card, the
 * three-segment meter and its word in the level's colour; a button that opens the calibration guide. (Until 0.25 a
 * card of its own at the foot of the page.) 0.9.6: the level shown is always the one the latest sensor reading gives,
 * at once (no hold, no fade); 0.9.7: the verdict comes from a CompassTracker, so a measure near a limit does not make
 * it flicker, and the meter never moves when the word changes (here it stands centred over the word).
 */
@Composable
internal fun AccuracyReadout(reading: CompassReading?, modifier: Modifier, onCalibrate: () -> Unit) {
    val c = NoorTheme.colors
    val label = stringResource(R.string.qibla_cell_accuracy)
    val word = reading?.let { accuracyLabel(it.assessment) }
    Column(
        modifier.fillMaxHeight().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.cal_title), onClick = onCalibrate)
            .semantics(mergeDescendants = true) {}.padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 2, textAlign = TextAlign.Center)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (reading == null) Text("—", style = MaterialTheme.typography.titleLarge.merge(Tabular).copy(fontSize = 22.sp), color = c.textPrimary)
        else {
            AccuracyMeter(reading.assessment, small = true, modifier = Modifier.testTag("accuracyMeter"))
            Spacer(Modifier.height(5.dp))
            Text(word!!, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = accuracyColor(reading.assessment),
                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/**
 * (0.26) The Qibla page's column, made to fit the screen: its second child (the dial) is given the height the others
 * leave out of [room], square, no wider than the column nor than [QiblaFit.MaxDial], and centred in the room it has.
 * Only when that would make it smaller than [QiblaFit.MinDial] is it held there, and the page then scrolls.
 */
@Composable
private fun QiblaFit(room: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val w = constraints.maxWidth
        val gaps = listOf(QiblaFit.AfterPlace, QiblaFit.AfterDial, QiblaFit.AfterStatus).map { it.roundToPx() }
        val loose = androidx.compose.ui.unit.Constraints(maxWidth = w)
        val others = measurables.mapIndexedNotNull { i, m -> if (i == 1) null else i to m.measure(loose) }.toMap()
        val used = others.values.sumOf { it.height } + gaps.sum()
        val slot = (room.roundToPx() - used).coerceAtLeast(QiblaFit.MinDial.roundToPx())
        val side = minOf(slot, w - QiblaFit.DialInset.roundToPx() * 2, QiblaFit.MaxDial.roundToPx())
        val dial = measurables[1].measure(androidx.compose.ui.unit.Constraints.fixed(side, side))
        val height = used + slot
        layout(w, height) {
            var y = 0
            others.getValue(0).placeRelative(0, y); y += others.getValue(0).height + gaps[0]
            dial.placeRelative((w - side) / 2, y + (slot - side) / 2); y += slot + gaps[1]
            others.getValue(2).placeRelative(0, y); y += others.getValue(2).height + gaps[2]
            others.getValue(3).placeRelative(0, y)
        }
    }
}

internal object QiblaFit {
    val MinDial = 260.dp
    val MaxDial = 420.dp
    val DialInset = 8.dp
    val AfterPlace = 10.dp
    val AfterDial = 10.dp
    val AfterStatus = 14.dp
}

@Composable
private fun VerticalHairline() = Box(Modifier.width(0.5.dp).height(40.dp).background(NoorTheme.colors.separator))

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
    val qiblaLabel = stringResource(R.string.qibla_title) + " " + f.wholeDegrees(qibla)
    // Made once, not with every frame (the dial is drawn again with each compass reading): the paints of the letters
    // and numbers, the numbers' text, and the two gradients (kept until their colours or the dial's size change).
    // 0.25: in the app's own typeface (a bare Paint draws in the phone's, Roboto: the dial was the one place in the
    // app whose letters and figures were not Plex); the letters in its SemiBold, not a thickened Regular.
    val context = androidx.compose.ui.platform.LocalContext.current
    val paint = remember(density, context) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; textSize = with(density) { 18.sp.toPx() }
            val plex = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plex_arabic_semibold) }.getOrNull()
            if (plex != null) typeface = plex else isFakeBoldText = true
        }
    }
    val small = remember(paint, c.textMuted, context) {
        android.graphics.Paint(paint).apply {
            textSize = with(density) { 11.sp.toPx() }; isFakeBoldText = false; color = c.textMuted.toArgb()
            runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.plex_arabic_regular) }.getOrNull()?.let { typeface = it }
            fontFeatureSettings = "tnum"
        }
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
            with(kaaba) { draw(androidx.compose.ui.geometry.Size(iconSize, iconSize), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(kaabaGlyph(c))) }
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

/**
 * The colour of the Kaaba's sign on its disc (0.25, second review). By night the disc is a light gold or a light green
 * and the sign dark on it; by day the disc is the day's deep gold or deep green, and the sign is white (it was black,
 * and the dark cube all but vanished into the dark green of "Facing the Qibla"). `QiblaKaabaContrastTest` measures it.
 */
internal fun kaabaGlyph(c: com.noor.app.ui.theme.NoorColors): Color = if (c.isDark) c.onGold else Color.White

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

/**
 * The calibration guide, in a sheet (0.25: three steps where there were five, the gesture drawn as under the dial, the
 * compass's level live so one sees it improve, and Done). [onDone] closes the sheet; [f] gives the steps' numbers the
 * digits the user chose.
 */
@Composable
internal fun CalibrationGuide(reading: CompassReading?, onDone: () -> Unit = {}, f: Formatter? = null) {
    val c = NoorTheme.colors
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 6.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.cal_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        // The same drawing as under the dial: a phone in the hand, gliding along a figure 8 (still when animations are off).
        val drawing = Modifier.fillMaxWidth().height(132.dp).padding(vertical = 8.dp)
        if (LocalAmbientMotion.current) {
            val t = rememberInfiniteTransition(label = "fig8")
            val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(NUDGE_LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "phase")
            FigureEightCanvas({ p.value }, rtl, drawing)
        } else FigureEightCanvas({ NUDGE_STILL_PHASE }, rtl, drawing)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(R.string.cal_step1, R.string.cal_step2, R.string.cal_step3).forEachIndexed { i, id ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 1.dp).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.14f)),
                        contentAlignment = Alignment.Center) {
                        Text(f?.number(i + 1) ?: "${i + 1}", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = c.goldText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(id), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
            }
        }
        // Live, while calibrating: the same meter, small, so the user sees it improve as they move the phone.
        if (reading != null) {
            val shown = reading.assessment
            val label = accuracyLabel(shown)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp).semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.cal_live, ""), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary)
                AccuracyMeter(shown, small = true)
                Spacer(Modifier.width(8.dp))
                Text(label, style = com.noor.app.ui.settings.FootnoteStyle, color = accuracyColor(shown))
            }
        }
        NoorButton(stringResource(R.string.done), onDone, Modifier.fillMaxWidth().padding(top = 20.dp))
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
internal fun CalibrateNudge(onOpen: () -> Unit, modifier: Modifier = Modifier, phase: Float? = null, compact: Boolean = false) {
    val c = NoorTheme.colors
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    // (0.25, second review) One card that says it once: its title is what to do ("Calibrate the compass", which the
    // line under the dial said over it until then), the drawing and a line say how, and the chevron opens the guide.
    // The drawing stands beside the words, so the card is about as tall as a row and the accuracy card under the
    // readings stays on the screen.
    GlassCard(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Noor.CardRadius))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {},
        padding = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = if (compact) 2.dp else 6.dp, bottom = if (compact) 2.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // (0.26) On the Qibla page it is smaller, so the dial over it keeps its size on a short screen.
            val drawing = if (compact) Modifier.width(104.dp).height(64.dp) else Modifier.width(132.dp).height(84.dp)
            when {
                phase != null -> FigureEightCanvas({ phase }, rtl, drawing, small = true, tiny = compact)
                LocalAmbientMotion.current -> {
                    val t = rememberInfiniteTransition(label = "nudge")
                    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(NUDGE_LOOP_MS, easing = LinearEasing), RepeatMode.Restart), label = "nudgePhase")
                    // (Read in the draw phase only: each frame draws again, nothing is composed again.)
                    FigureEightCanvas({ p.value }, rtl, drawing, small = true, tiny = compact)
                }
                else -> FigureEightCanvas({ NUDGE_STILL_PHASE }, rtl, drawing, small = true, tiny = compact)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f).padding(vertical = if (compact) 8.dp else 10.dp)) {
                Text(stringResource(R.string.cal_title), style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.SemiBold), color = c.warning)
                Spacer(Modifier.height(2.dp))
                // (0.26, review) With large text the Qibla page's card says only what to do, so the compass keeps its size.
                if (!compact || androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.15f)
                    Text(stringResource(R.string.qibla_figure8), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
    }
}

@Composable
private fun FigureEightCanvas(phase: () -> Float, rtl: Boolean, modifier: Modifier, small: Boolean = false, tiny: Boolean = false) {
    val c = NoorTheme.colors
    Canvas(modifier) { nudgeFigure(phase(), rtl, c.gold, c.textMuted.copy(alpha = 0.30f), c.textPrimary, c.surface, if (tiny) 0.62f else if (small) 0.8f else 1f) }
}

/**
 * A lemniscate of Bernoulli (made a little fuller), drawn as a faint line; a phone, held screen up as in the hand,
 * travels it and tilts as it goes, with a trail that fades behind it. Strokes in plain colours, faded with alpha (a gradient brush on a stroke
 * stops the emulator's graphics: HANDOVER.md section 28).
 */
private fun DrawScope.nudgeFigure(phase: Float, rtl: Boolean, trail: Color, track: Color, body: Color, screen: Color, scale: Float = 1f) {
    val cx = size.width / 2; val cy = size.height / 2
    val phoneH = 26.dp.toPx() * scale; val phoneW = 15.dp.toPx() * scale
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
