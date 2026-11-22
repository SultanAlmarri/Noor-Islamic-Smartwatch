package com.noor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.ui.theme.NoorTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A scroll wheel (0.17.1), as the Clock app sets the time with: five rows in sight, the one in the middle chosen, a
 * soft band behind it, a tick as each row passes the middle. [loop]: it goes round (the minutes, 0 after 59). A touch
 * on a row brings it to the middle. [label] names the wheel for TalkBack ("Minutes").
 *
 * (0.25) [unit]: what the wheel's numbers count ("h", "min"), written once, small, in the band beside the chosen
 * number, as Apple's pickers do; the numbers turn past it. The band then reads as a sentence: "0 h  25 min  before  Fajr".
 */
@Composable
fun Wheel(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    rowHeight: Dp = 44.dp,
    /** False when the wheels of a row share one band, drawn behind them all (0.18: one clean line across). */
    band: Boolean = true,
    /** The chosen row's type size; the others are a size smaller. */
    size: Float = 24f,
    unit: String? = null,
) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    val n = items.size
    // (Going round: the list is long and starts in its middle, at the chosen row.)
    val count = if (loop) n * 400 else n
    val first = if (loop) n * 200 + selected.coerceIn(0, n - 1) else selected.coerceIn(0, n - 1)
    val state = rememberLazyListState(first)
    val fling = rememberSnapFlingBehavior(remember(state) { SnapLayoutInfoProvider(state, SnapPosition.Center) })
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onSelect)
    // The row nearest the middle.
    val centre by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: first
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centre }.distinctUntilChanged().collect { i ->
            if (state.isScrollInProgress) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pick(i % n)
        }
    }
    // (The words keep to their narrow wheel: they follow the phone's text size up to a tenth larger, no further.)
    val grow = limitedGrowth(1.1f)
    val unitSize = 13f
    Box(modifier.height(rowHeight * 5).semantics { contentDescription = label; stateDescription = items[centre % n] + (unit?.let { " $it" } ?: "") }, contentAlignment = Alignment.Center) {
        if (band) Box(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(12.dp)).background(c.textMuted.copy(alpha = if (c.isDark) 0.16f else 0.11f)))
        LazyColumn(Modifier.fillMaxWidth().height(rowHeight * 5), state = state, flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            items(count) { i ->
                val on = i == centre
                val away = abs(i - centre)
                Box(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { scope.launch { state.animateScrollToItem(i) } }
                        .graphicsLayer { alpha = when (away) { 0 -> 1f; 1 -> 0.55f; else -> 0.25f } },
                    contentAlignment = Alignment.Center,
                ) {
                    val style = MaterialTheme.typography.titleLarge.copy(fontSize = (if (on) size else size - 4f).sp * grow, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    if (unit == null) Text(items[i % n], maxLines = 1, softWrap = false, style = style, color = if (on) c.textPrimary else c.textSecondary)
                    // (With a unit: the numbers end at the wheel's middle, and the unit begins there.)
                    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(items[i % n], maxLines = 1, softWrap = false, style = style.merge(com.noor.app.ui.theme.Tabular), color = if (on) c.textPrimary else c.textSecondary,
                            textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (unit != null) Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            // (Set down a little, onto the line the larger number stands on.)
            val drop = with(LocalDensity.current) { ((size - unitSize) * 0.36f * grow).sp.toDp() }
            Text(unit, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge.copy(fontSize = unitSize.sp * grow, letterSpacing = 0.sp),
                color = c.textSecondary, modifier = Modifier.weight(1f).padding(start = 3.dp, top = drop))
        }
    }
}
