package com.noor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
    Box(modifier.height(rowHeight * 5).semantics { contentDescription = label; stateDescription = items[centre % n] }, contentAlignment = Alignment.Center) {
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
                    Text(items[i % n], maxLines = 1,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = if (on) size.sp else (size - 4f).sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) c.textPrimary else c.textSecondary)
                }
            }
        }
    }
}
