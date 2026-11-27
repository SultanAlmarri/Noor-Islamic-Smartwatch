package com.noor.app.ui.adhan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.ui.components.glassEdge
import com.noor.app.ui.components.limitedGrowth
import com.noor.app.ui.components.withTick
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.IconTile
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.NoorColors
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter

/** The slim bar of a page pushed from a tab: a back chevron (mirrored in Arabic) and the title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) = com.noor.app.ui.components.NavBar(title, onBack)

/**
 * Something that stops alerts from working as chosen, with the one action that fixes it (0.14: the whole row is the
 * action, named in gold under the explanation, so nothing is squeezed beside a button), or a line of plain status.
 */
@Composable
fun WarningRow(text: String, action: String?, onAction: (() -> Unit)?, icon: ImageVector = Icons.Rounded.WarningAmber, danger: Boolean = false) {
    val c = NoorTheme.colors
    val warn = icon == Icons.Rounded.WarningAmber
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (action != null && onAction != null) Modifier.clickable(role = Role.Button, onClickLabel = action, onClick = onAction) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, Tints.let { if (danger) it.Red else if (warn) it.Orange else it.Gray })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 20.sp), color = if (warn || danger) c.textPrimary else c.textSecondary)
            // (0.25) What fixes it reads as a button, not as a line of coloured text; the whole row is still the touch
            // target. (Second review: the app's one second kind of button, the text's ink on a neutral pill 36 dp high.)
            if (action != null && onAction != null) Box(
                Modifier.padding(top = 8.dp).heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(com.noor.app.ui.components.TonalColors.fill(c)).padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = com.noor.app.ui.components.TonalColors.label(c)) }
        }
    }
}

/**
 * The fills and inks of the segmented control (0.25), in one place so that a test can measure them
 * (`SegmentedContrastTest`: both labels at 4.5:1 or better on their fills wherever the control stands, in both themes,
 * at every height of the Sun). At night the track is white at 7% and the chosen segment white at 15% over it, lit along
 * its top: until 0.24 the two had the same luminance (59,70,90 on 49,70,97), and only the label's weight said where one
 * was. (White at 20% on 8% was tried first: on a sheet under a bright morning sky the chosen label fell to 3.98:1.) By
 * day the track is a quiet grey and the chosen segment white, raised by a shadow; there both labels are in the ink of
 * the text (the grey of the others gave 2.5:1 at the foot of a page at night). Gold is not used: it is kept for what acts.
 */
object SegmentedColors {
    fun track(c: NoorColors): Color = if (c.isDark) Color.White.copy(alpha = 0.07f) else c.textMuted.copy(alpha = 0.13f)
    fun thumb(c: NoorColors): Color = if (c.isDark) Color.White.copy(alpha = 0.15f) else Color.White
    fun selectedLabel(c: NoorColors): Color = c.textPrimary
    fun label(c: NoorColors): Color = if (c.isDark) c.textSecondary else c.textPrimary
}

/**
 * A choice of two to four, drawn as a segmented control (0.14, as on iOS; 0.25: one control everywhere, the home
 * screen's settings included, which had a gold-filled one of their own): a quiet track, and a raised thumb, lit along
 * its top, that slides to the chosen segment with a spring and a tick. The labels keep to their segment: two lines at
 * most (one when there are four), growing with the phone's text size up to a fifth, no further.
 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(12.dp)
    val thumbShape = RoundedCornerShape(9.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().clip(shape).background(SegmentedColors.track(c)).padding(3.dp),
    ) {
        val w = maxWidth / options.size
        val x by androidx.compose.animation.core.animateDpAsState(w * index, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = x).width(w).fillMaxHeight()
                // (A soft shadow round the thumb, drawn outside it only: an elevation shadow shows through a thumb
                // of glass and muddies it.)
                .drawBehind {
                    val r = 9.dp.toPx()
                    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                    clipPath(outline, ClipOp.Difference) {
                        for (k in 1..3) {
                            val grow = k * 1.dp.toPx()
                            drawRoundRect(Color.Black.copy(alpha = if (c.isDark) 0.10f else 0.05f), topLeft = Offset(-grow, -grow + 1.dp.toPx()),
                                size = Size(size.width + 2 * grow, size.height + 2 * grow), cornerRadius = CornerRadius(r + grow))
                        }
                    }
                }
                .clip(thumbShape).background(SegmentedColors.thumb(c))
                .then(if (c.isDark) Modifier.glassEdge(thumbShape, Color.White.copy(alpha = 0.30f), Color.Transparent, 0.5.dp) else Modifier))
        }
        val many = options.size >= 4
        val grow = limitedGrowth(1.2f)
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 42.dp).clip(thumbShape)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                            if (!on) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                        }
                        .semantics { this.selected = on }.padding(horizontal = if (many) 2.dp else 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp * grow, letterSpacing = 0.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) SegmentedColors.selectedLabel(c) else SegmentedColors.label(c), textAlign = TextAlign.Center,
                        maxLines = if (many) 1 else 2, softWrap = !many, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Noor's slider (0.25, in place of Material's thick track with a bar for a handle and a dot at its end): a 4 dp
 * track, gold up to the value, and a round white thumb with a soft shadow, as on iOS. It runs from the right in Arabic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoorSlider(
    value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, label: String,
    modifier: Modifier = Modifier, onValueChangeFinished: (() -> Unit)? = null,
    /**
     * (0.26 review) The prayers it sets differ: no thumb and no gold, only the bare track, so it shows no position it
     * does not have; a touch on the track sets them all there, and the thumb appears.
     */
    mixed: Boolean = false,
) {
    val c = NoorTheme.colors
    val rest = c.textMuted.copy(alpha = if (c.isDark) 0.30f else 0.24f)
    val mixedWord = androidx.compose.ui.res.stringResource(com.noor.app.R.string.voice_mixed)
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.semantics { contentDescription = label; if (mixed) stateDescription = mixedWord },
        thumb = {
            if (mixed) Spacer(Modifier.size(24.dp))
            else Box(Modifier.size(24.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)
                .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape))
        },
        track = { state ->
            val f = if (mixed) 0f else ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(rest, size = size, cornerRadius = r)
                val w = size.width * f
                drawRoundRect(c.goldFill, topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f, 0f), size = Size(w, size.height), cornerRadius = r)
            }
        },
    )
}

/**
 * What depends on a switch that is off (0.25): shown at 38%, and it takes no touch and is not read out, so it looks
 * as off as it is. (The page still scrolls with a finger on it.)
 */
@Composable
fun OffWhen(off: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!off) { Box(modifier) { content() }; return }
    Box(modifier) {
        Box(Modifier.alpha(0.38f).clearAndSetSemantics { }) { content() }
        Box(Modifier.matchParentSize().pointerInput(Unit) { })
    }
}

/**
 * A clock time with its AM or PM small beside it, as the home screen writes its times ("4:55 AM", "4:55 ص"): the
 * marker at half the digits' size (in Arabic "ص" stood as large as the digits).
 */
@Composable
fun ClockText(
    time: Pair<String, String?>, style: TextStyle, color: Color, modifier: Modifier = Modifier,
    markerStyle: TextStyle = style.copy(fontSize = style.fontSize * 0.5f, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    markerColor: Color = NoorTheme.colors.textSecondary,
) {
    Row(modifier.semantics(mergeDescendants = true) { }) {
        Text(time.first, style = style, color = color, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        time.second?.let { Text(" $it", style = markerStyle, color = markerColor, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline()) }
    }
}

/** The clock time of [at] in [zone], and its AM or PM apart (null on a 24-hour clock). */
internal fun Formatter.clockAt(at: java.time.Instant, zone: java.time.ZoneId): Pair<String, String?> =
    at.atZone(zone).let { time(it.hour * 60 + it.minute) }

/** A word in the bar of a page that acts (Cancel, Save), as on iOS: in the accent, the main one heavier; 48 dp to touch. */
@Composable
fun BarAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true) {
    val c = NoorTheme.colors
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(enabled = enabled, role = Role.Button, onClick = withTick(onClick))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp * limitedGrowth(1.2f), fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
            color = c.goldText.copy(alpha = if (enabled) 1f else 0.38f), maxLines = 1, softWrap = false)
    }
}

/** Round play/stop button for previews. */
@Composable
fun PreviewButton(playing: Boolean, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = withTick(onClick)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (playing) c.goldFill else c.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) c.onGold else c.gold, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * A selectable sound: check mark, title, detail, preview. [add] (0.25): there is nothing to play yet and the row adds
 * a recording: a plus at its end, where the others have their preview.
 */
@Composable
fun SoundRow(
    title: String,
    detail: String?,
    selected: Boolean,
    playing: Boolean,
    previewLabel: String,
    onSelect: (() -> Unit)?,
    onPreview: (() -> Unit)?,
    badge: String? = null,
    add: Boolean = false,
    /** A sign at the end where the others have their button, in the same column (Random's shuffle). */
    sign: ImageVector? = null,
) {
    val c = NoorTheme.colors
    Row(
        // (A row with a button or a sign at its end is 64 dp: the 48 dp column and the padding round it.)
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onSelect != null) Modifier.clickable(role = if (add) Role.Button else Role.RadioButton, onClick = withTick(onSelect)).semantics { if (!add) this.selected = selected } else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSelect != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) c.goldText else c.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = c.gold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.gold.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (detail != null) Text(detail, style = FootnoteStyle, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            add -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = c.gold, modifier = Modifier.size(22.dp))
                }
            }
            onPreview != null -> { Spacer(Modifier.width(8.dp)); PreviewButton(playing, previewLabel, onPreview) }
            sign != null -> { Spacer(Modifier.width(8.dp)); Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(sign, null, tint = c.textSecondary, modifier = Modifier.size(22.dp)) } }
        }
    }
}

/** A no-break space: what is written with it ("Fajr 5:20 AM", "25 min") never breaks across two lines. */
internal const val NBSP = ' '

/** [this] as one piece that a line never breaks inside (its spaces made no-break spaces). */
internal fun String.together(): String = replace(' ', NBSP)

/**
 * A few facts on one line joined by " · " (0.25, second review): when they do not fit, the line breaks before a fact,
 * never inside a short one and never after a dot (a dot left at the end of a line, "…Exact alarms were" / "off", read
 * as a fault). Each fact longer than the whole width still wraps by itself. [center]: each line centred (the alarm
 * editor's line under its time). Read out by TalkBack as one text, without the dots.
 */
@Composable
fun FactsLine(facts: List<String>, style: TextStyle, color: Color, modifier: Modifier = Modifier, center: Boolean = false) {
    val parts = facts.filter { it.isNotBlank() }
    if (parts.isEmpty()) return
    val align = if (center) TextAlign.Center else TextAlign.Start
    androidx.compose.ui.layout.Layout(
        content = {
            parts.forEach { Text(it, style = style, color = color, textAlign = align) }
            repeat(parts.size - 1) { Text(" · ", style = style, color = color, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics { }) }
        },
        modifier = modifier.semantics(mergeDescendants = true) { },
    ) { measurables, cs ->
        val n = parts.size
        val room = if (cs.hasBoundedWidth) cs.maxWidth else Int.MAX_VALUE
        val items = measurables.take(n).map { it.measure(androidx.compose.ui.unit.Constraints(maxWidth = room)) }
        val dots = measurables.drop(n).map { it.measure(androidx.compose.ui.unit.Constraints()) }
        // (Lines filled fact by fact: a fact goes on the line if it fits there with the dot before it.)
        val lines = mutableListOf<MutableList<Int>>()
        var x = 0
        items.forEachIndexed { i, p ->
            val dot = if (i > 0) dots[i - 1].width else 0
            if (lines.isEmpty() || x + dot + p.width > room) { lines.add(mutableListOf(i)); x = p.width } else { lines.last().add(i); x += dot + p.width }
        }
        val widths = lines.map { l -> l.sumOf { items[it].width } + l.drop(1).sumOf { dots[it - 1].width } }
        val heights = lines.map { l -> l.maxOf { items[it].height } }
        val w = maxOf(widths.max(), cs.minWidth).coerceAtMost(room)
        val h = maxOf(heights.sum(), cs.minHeight)
        layout(w, h) {
            var y = 0
            lines.forEachIndexed { li, l ->
                var px = if (center) (w - widths[li]) / 2 else 0
                l.forEachIndexed { k, i ->
                    if (k > 0) { dots[i - 1].placeRelative(px, y); px += dots[i - 1].width }
                    items[i].placeRelative(px, y); px += items[i].width
                }
                y += heights[li]
            }
        }
    }
}

/**
 * What a page shows when it has nothing in it yet (0.25, second review: one style for every such page): a card with
 * the gold seal, what the page is for in a title and a sentence, and the one thing to do.
 */
@Composable
fun EmptyCard(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit, actionEnabled: Boolean = true) {
    val c = NoorTheme.colors
    com.noor.app.ui.components.GlassCard(Modifier.fillMaxWidth(), corner = 30.dp, highlighted = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            com.noor.app.ui.components.Medallion(androidx.compose.ui.graphics.vector.rememberVectorPainter(icon))
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            com.noor.app.ui.components.NoorButton(action, onAction, Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Rounded.Add, enabled = actionEnabled)
        }
    }
}

/** Small explanatory paragraph under a section. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = FootnoteStyle, color = NoorTheme.colors.textMuted, modifier = modifier.padding(horizontal = 16.dp))
}

// ---- 0.26 review: the foot bar, the mixed state, the snackbar, the days ----

/**
 * A bar at the foot of a page (0.26 review: the alarm editor's Cancel and Save, the alarms' Delete while choosing),
 * as the page's top bar is made ([com.noor.app.ui.components.NoorPage]): opaque, in the page's own sky (the sky drawn
 * as the page draws it where the bar stands, its horizon's glow included, so the bar is the page and not a wash over
 * it), a hairline above it; nothing that scrolls under it is seen, and it takes the touches that land on it. [bottom]:
 * the room under its row (the navigation bar, or the tab bar that floats over its lower part).
 */
@Composable
internal fun FootBar(bottom: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val backdrop = com.noor.app.ui.theme.LocalBackdrop.current
    // (Where the bar stands on the page, and the page's size, for the sky to be drawn as the page draws it.)
    val place = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    val placed = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(
        modifier.fillMaxWidth()
            .onPlaced { co ->
                val at = co.positionInRoot(); val root = co.findRootCoordinates().size
                if (place[0] != at.x || place[1] != at.y || place[2] != root.width.toFloat() || place[3] != root.height.toFloat()) {
                    place[0] = at.x; place[1] = at.y; place[2] = root.width.toFloat(); place[3] = root.height.toFloat(); placed.intValue++
                }
            }
            .drawBehind {
                placed.intValue
                val x = place[0]; val y = place[1]
                val w = if (place[2] > 0f) place[2] else size.width
                val h = if (place[3] > 0f) place[3] else size.height
                drawRect(Brush.verticalGradient(0f to backdrop.top, 0.55f to backdrop.middle, 1f to backdrop.bottom, startY = -y, endY = h - y))
                if (backdrop.glow.alpha > 0.01f) drawRect(Brush.radialGradient(listOf(backdrop.glow, Color.Transparent),
                    center = Offset(w * 0.5f - x, h * 1.02f - y), radius = kotlin.math.max(w, h) * 0.62f))
            }
            .pointerInput(Unit) { },
    ) {
        com.noor.app.ui.components.Hairline()
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = bottom).heightIn(min = FootBarHeight), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** How tall a [FootBar]'s row is, over the room under it. */
internal val FootBarHeight = 56.dp

/**
 * A switch for several prayers that differ (0.26 review): the track of a switch that is off, its thumb in the middle
 * with a dash, as a checkbox shows "some". Drawn as Minhad's switch is ([com.noor.app.ui.settings.NoorSwitch], 52 by
 * 32 dp), so a row keeps its shape; a touch turns it on for every prayer.
 */
@Composable
internal fun MixedSwitch() {
    val c = NoorTheme.colors
    val off = c.textMuted.copy(alpha = if (c.isDark) 0.38f else 0.30f)
    Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CircleShape).background(off), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 10.dp, height = 2.5.dp).clip(RoundedCornerShape(50)).background(MixedDash))
        }
    }
}

/** The dash on a mixed switch's white thumb (4.6:1 on white). */
internal val MixedDash = Color(0xFF6A7180)

/**
 * A row that turns something on or off for several prayers at once (0.26 review), in three states: on, off, or on for
 * some ([Shared.MIXED], the mixed switch). The whole row is the switch; from "some" a touch turns it on for all.
 */
@Composable
internal fun TriSwitchRow(icon: ImageVector, title: String, value: String?, state: Shared, tint: Color, onChange: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .triStateToggleable(
                when (state) { Shared.ON -> ToggleableState.On; Shared.OFF -> ToggleableState.Off; Shared.MIXED -> ToggleableState.Indeterminate },
                role = Role.Switch,
            ) { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onChange(state != Shared.ON) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (value != null) Text(value, style = FootnoteStyle, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        if (state == Shared.MIXED) MixedSwitch() else com.noor.app.ui.settings.NoorSwitch(state == Shared.ON, null)
    }
}

/**
 * The line that rises at a page's foot (0.26 review): opaque, in the colour of what rises over the page (the sheets'
 * surface), softly rounded, its action in the accent. Calmer than Material's dark bar.
 */
@Composable
internal fun NoorSnackbarHost(state: androidx.compose.material3.SnackbarHostState, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    androidx.compose.material3.SnackbarHost(state, modifier) { data ->
        androidx.compose.material3.Snackbar(data, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp),
            containerColor = c.surfaceRaised, contentColor = c.textPrimary, actionColor = c.goldText, dismissActionContentColor = c.textSecondary)
    }
}

/**
 * The colours of the days (0.26 review: one design wherever days are chosen, the alarm's, as Samsung's): a day that is
 * on is a disc of solid gold with its letter dark, a day that is off a bare letter. In the light theme the gold disc has
 * a deeper gold rim, so that it stands out from the white glass at 3:1 (`DayLettersContrastTest`).
 */
internal object WeekColors {
    fun fill(c: NoorColors): Color = c.goldFill
    fun onFill(c: NoorColors): Color = c.onGold
    /** The rim of a day that is on (null: none needed). */
    fun rim(c: NoorColors): Color? = if (c.isDark) null else c.goldDeep
    fun letter(c: NoorColors): Color = c.textSecondary
}

/** The week in the language's own order (Sunday first in English, Saturday in Arabic). */
internal fun week(f: Formatter): List<java.time.DayOfWeek> {
    val first = java.time.temporal.WeekFields.of(f.locale).firstDayOfWeek
    return (0 until 7).map { first.plus(it.toLong()) }
}

/** A day's one letter, as Samsung writes the week ("S M T W T F S"; in Arabic ح ن ث ر خ ج س). */
internal fun letter(d: java.time.DayOfWeek, f: Formatter): String = d.getDisplayName(java.time.format.TextStyle.NARROW, f.locale)

/**
 * The days of the week something rings or alerts on (0.26 review: the one selector of the app, in the alarm editor
 * and on a prayer's page): a line that says them ("Every day", "Mon–Fri") over the week's seven letters, in the
 * language's week order, each a round toggle ([WeekColors]). At least one stays on: [onChange] is given 0 when the last
 * would go, and the caller keeps it. Goes inside a card.
 */
@Composable
internal fun WeekDays(days: Int, f: Formatter, onChange: (Int) -> Unit) {
    val c = NoorTheme.colors
    val grow = limitedGrowth(1.15f)
    Text(daysLabel(days, f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = c.textPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        for (d in week(f)) {
            val bit = 1 shl (d.value - 1)
            val on = days and bit != 0
            val rim = WeekColors.rim(c)
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .then(if (on) Modifier.background(WeekColors.fill(c)).then(if (rim != null) Modifier.border(1.dp, rim, CircleShape) else Modifier) else Modifier)
                    .toggleable(on, role = Role.Checkbox, onValueChange = withTick<Boolean> { onChange(if (it) days or bit else days and bit.inv()) })
                    .semantics { contentDescription = d.getDisplayName(java.time.format.TextStyle.FULL, f.locale) },
                contentAlignment = Alignment.Center,
            ) {
                Text(letter(d, f), style = MaterialTheme.typography.labelLarge.copy(fontSize = (if (f.arabic) 16f else 15f).sp * grow, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) WeekColors.onFill(c) else WeekColors.letter(c), maxLines = 1)
            }
        }
    }
}
