package com.noor.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.noor.wear.R
import com.noor.wear.WearText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** 0.25: the corner of every platter: a rounded rectangle, not a pill. */
internal val PlatterShape = RoundedCornerShape(18.dp)

/**
 * 0.25: something that can be pressed: it gives a little under the finger and springs back (no motion when the
 * watch's "Remove animations" is on: Compose follows the system's animation scale).
 */
internal fun Modifier.pressable(
    shape: Shape, color: Color, enabled: Boolean = true, role: Role = Role.Button, dimDisabled: Boolean = true, onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.955f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled || !dimDisabled) 1f else 0.5f }
        .clip(shape).background(color)
        .clickable(source, LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}

/**
 * The time, written by the app itself straight on the page (no capsule behind it), in the digits and the 12/24-hour
 * clock chosen for Minhad. The system's own time text is never used (`NoorScaffold`), so a page shows the time only
 * where it asks for it: lists show it until they leave their top; the compass never does.
 */
@Composable
internal fun WatchClock(visible: Boolean = true, modifier: Modifier = Modifier) {
    val t = LocalWearText.current
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "clock")
    if (alpha > 0.01f) {
        TimeText(modifier.graphicsLayer { this.alpha = alpha }, backgroundColor = Color.Transparent, timeSource = remember(t) { ClockSource(t) })
    }
}

private class ClockSource(private val t: WearText) : TimeSource {
    @Composable
    override fun currentTime(): String {
        val now by produceState(System.currentTimeMillis()) {
            while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); value = System.currentTimeMillis() }
        }
        return t.timeShort(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    }
}

/**
 * Whether a list is where it starts: nothing above to scroll back to, or (a list whose first item starts in the middle
 * of the screen and may fill it) that item still in the middle.
 */
@Composable
internal fun ScalingLazyListState.atTop(): Boolean {
    val slack = with(LocalDensity.current) { 8.dp.toPx() }
    val top by remember(this) { derivedStateOf { !canScrollBackward || (centerItemIndex == 0 && kotlin.math.abs(centerItemScrollOffset) < slack) } }
    return top
}

/** A page with a list: the list, and the time over it until the list leaves its top. */
@Composable
internal fun PageScaffold(list: ScalingLazyListState, content: @Composable BoxScope.() -> Unit) {
    ScreenScaffold(scrollState = list) { _ ->
        Box(Modifier.fillMaxSize()) {
            content()
            WatchClock(visible = list.atTop())
        }
    }
}

/**
 * The list every page is made of. It starts with its title a fifth of the way down the screen, has room below its
 * last item for that item to rise to the middle of the round screen (so a closing line can always be read), and
 * does not shrink or dim rows near the edges. With [centerFirst] the first item starts in the middle (a first
 * screenful composed as one piece).
 */
@Composable
internal fun NoorList(list: ScalingLazyListState, centerFirst: Boolean = false, content: ScalingLazyListScope.() -> Unit) {
    val h = Screen.height()
    ScalingLazyColumn(
        state = list, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = if (centerFirst) 0.dp else (h * 0.20f - 18.dp).coerceAtLeast(24.dp), bottom = if (centerFirst) 0.dp else h * 0.30f),
        autoCentering = if (centerFirst) AutoCenteringParams(itemIndex = 0) else null,
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
        content = content,
    )
}

/** A round sign: the icon on a disc of its colour, as beside every settings row and on the home screen's tools. */
@Composable
internal fun RoundSign(@DrawableRes icon: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(modifier.size(size).background(tint, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(size * 0.58f), tint = if (tint.luminance() > 0.42f) OnGold else Color.White)
    }
}

/**
 * A row on a platter: a round sign (or [leading]), the label, and a line that says its state. 0.25 replaced the stock
 * navy pill with this (the name is kept: every list in the app is made of it).
 */
@Composable
internal fun NoorChip(
    label: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    enabled: Boolean = true,
    iconTint: Color = Tint.Gold,
    labelLines: Int = 2,
    secondaryLines: Int = 2,
    secondaryColor: Color = Quiet,
    container: Color = Platter,
    /** The page's main action: the row in gold. */
    prominent: Boolean = false,
    /** Not pressable now, but not greyed either (a main action that is busy). */
    busy: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val signed = icon != null || leading != null
    Row(
        modifier.fillMaxWidth().pressable(PlatterShape, if (prominent) Gold else container, enabled && !busy, dimDisabled = !busy, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp).padding(start = if (signed) 10.dp else 16.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) leading() else if (icon != null) RoundSign(icon, if (prominent) OnGold else iconTint)
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.row, color = if (prominent) OnGold else Ink, maxLines = labelLines, overflow = TextOverflow.Ellipsis)
            if (secondary != null) Text(secondary, style = Type.detail, color = if (prominent) OnGold.copy(alpha = 0.82f) else secondaryColor, maxLines = secondaryLines, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/**
 * Large figures (a countdown, a count, a bearing, a date), always left to right, in a box as tall as the figures
 * themselves: Plex Arabic's own line is 1.6 times its size (room for Arabic's marks), which would push everything
 * around a large number far from it. With [softColons] the colons are quieter and closer: in Arabic-Indic digits a
 * zero is a dot, and "٢٣:٠٠" otherwise reads as two figures and a row of points.
 */
@Composable
internal fun Numeral(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Ink, weight: FontWeight = FontWeight.SemiBold, softColons: Boolean = false) {
    val box = with(LocalDensity.current) { (size * 0.94f).toDp() }
    val shown = if (softColons && ':' in text) buildAnnotatedString {
        for (ch in text) {
            if (ch == ':') withStyle(SpanStyle(color = color.copy(alpha = 0.55f), letterSpacing = (-0.10).em, fontWeight = FontWeight.Medium)) { append(ch) } else append(ch)
        }
    } else AnnotatedString(text)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(shown, modifier.height(box).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
            style = Type.numeral(size, weight), color = color, maxLines = 1, softWrap = false)
    }
}

/** The one action a page is for: gold, full width. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().pressable(RoundedCornerShape(50), Gold, enabled, onClick = onClick).defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.padding(end = 8.dp).size(18.dp), tint = OnGold)
        Text(text, style = Type.row, color = OnGold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A switch on a platter; green when on. */
@Composable
internal fun NoorSwitch(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null, iconTint: Color = Tint.Gold, secondary: String? = null,
) {
    SwitchButton(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier.fillMaxWidth(), shape = PlatterShape,
        colors = SwitchButtonDefaults.switchButtonColors(
            checkedContainerColor = Platter, uncheckedContainerColor = Platter,
            checkedContentColor = Ink, uncheckedContentColor = Ink,
            checkedSecondaryContentColor = Quiet, uncheckedSecondaryContentColor = Quiet,
            checkedThumbColor = Color.White, uncheckedThumbColor = Color(0xFFD0D0D6),
            checkedTrackColor = Success, uncheckedTrackColor = Color(0xFF3A3A40),
            checkedTrackBorderColor = Success, uncheckedTrackBorderColor = Color(0xFF3A3A40),
            checkedThumbIconColor = Success,
        ),
        icon = icon?.let { res -> { RoundSign(res, iconTint, size = 28.dp) } },
        secondaryLabel = secondary?.let { s -> { Text(s, style = Type.detail, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, style = Type.row, maxLines = 3, overflow = TextOverflow.Ellipsis) },
    )
}

/** The page's title: white and confident, a fifth of the way down the screen, 8 dp over the first row. */
@Composable
internal fun Header(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, bottom = 4.dp),
        style = Type.title, color = Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A quiet label over a group of rows. */
@Composable
internal fun Caption(text: String, t: WearText, modifier: Modifier = Modifier, color: Color = Quiet) {
    Text(t.caps(text), modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        style = t.captionStyle(), color = color, textAlign = TextAlign.Center, maxLines = 2)
}

/**
 * A closing line of a page. Never wider than three fifths of the screen: at the end of a list it stands where the
 * round screen narrows.
 */
@Composable
internal fun Footnote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, Modifier.widthIn(max = Screen.width() * 0.6f), style = Type.note, color = Quiet, textAlign = TextAlign.Center)
    }
}

/**
 * The ring round the edge of the screen: how far through something we are (the interval to the next prayer, a
 * phrase of the tasbih), with a gap at the top for the clock and a bright head where it has got to.
 * Plain colours only (a gradient brush on a stroke has killed an emulator's graphics before).
 */
@Composable
internal fun Ring(
    progress: Float, modifier: Modifier = Modifier, color: Color = Gold, track: Color = Color.White.copy(alpha = 0.14f),
    stroke: Dp = 5.dp, gapDeg: Float = 60f, animate: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "ring")
    val p = if (animate) shown else target
    Canvas(modifier) {
        val w = stroke.toPx()
        val d = size.minDimension - w
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val start = -90f + gapDeg / 2f
        val sweep = 360f - gapDeg
        drawArc(track, start, sweep, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        if (p > 0.001f) {
            drawArc(color, start, sweep * p, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            val a = Math.toRadians((start + sweep * p).toDouble())
            val head = Offset(size.width / 2f + (d / 2f * cos(a)).toFloat(), size.height / 2f + (d / 2f * sin(a)).toFloat())
            drawCircle(Color.White, w * 0.27f, head)
        }
    }
}

/** "Today", "Tomorrow", "in 12 days" (with Arabic's plural forms). */
@Composable
internal fun daysUntilText(days: Long, t: WearText): String = when {
    days <= 0L -> stringResource(R.string.today)
    days == 1L -> stringResource(R.string.tomorrow)
    else -> pluralStringResource(R.plurals.in_days, days.coerceAtMost(9999).toInt(), t.digits(days.toString()))
}
