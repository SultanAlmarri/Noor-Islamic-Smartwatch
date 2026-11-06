package com.noor.app.ui.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.Sky
import com.noor.app.ui.theme.Tabular

/** What the watch is showing right now (computed from the same configuration the watch receives). */
@Immutable
data class WatchPreviewState(
    val prayer: String,
    /** The place, as the watch shows it under the countdown, in the user's language. */
    val caption: String,
    val countdown: String,
    val time: String,
    /** 0..1 from the previous prayer to the next. */
    val progress: Float,
    /** Sky colour of the moment, used for the ring's glow so phone and watch share the same light. */
    val glow: Color,
    val arabic: Boolean,
)

/**
 * A drawn Galaxy Watch–style case showing Noor's watch screen: a ring with the interval to the next prayer (gap at the
 * top, as on the real watch app), the prayer's name, the countdown and the time. Not a screenshot: it is redrawn live.
 */
@Composable
fun WatchPreview(state: WatchPreviewState?, modifier: Modifier = Modifier, diameter: Dp = 196.dp, description: String) {
    // An illustration inside a fixed circle: its text keeps its size whatever the system font scale.
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1f)) {
        WatchPreviewBody(state, modifier, diameter, description)
    }
}

@Composable
private fun WatchPreviewBody(state: WatchPreviewState?, modifier: Modifier, diameter: Dp, description: String) {
    Box(modifier.size(diameter + 28.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter + 28.dp)) {
            val c = center
            val outer = size.minDimension / 2
            // Soft light under the watch, tinted by the sky of the moment.
            drawCircle(Brush.radialGradient(listOf((state?.glow ?: Color(0xFF3B4A7A)).copy(alpha = 0.35f), Color.Transparent), c, outer * 1.0f), outer, c)
            // Case: brushed graphite with light from the upper left, and the crown on the side.
            val case = outer - 10.dp.toPx()
            val crownW = 7.dp.toPx(); val crownH = 22.dp.toPx()
            for (dy in listOf(-0.42f, 0.42f)) drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6C7380), Color(0xFF2A2E36))),
                topLeft = Offset(c.x + case - 2.dp.toPx(), c.y + dy * case - crownH / 2), size = Size(crownW, crownH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawCircle(Brush.linearGradient(listOf(Color(0xFF8A919D), Color(0xFF3B404A), Color(0xFF15171B), Color(0xFF4A505B)),
                Offset(c.x - case, c.y - case), Offset(c.x + case, c.y + case)), case, c)
            drawCircle(Brush.linearGradient(listOf(Color(0x66FFFFFF), Color.Transparent, Color(0x22FFFFFF)), Offset(c.x - case, c.y - case), Offset(c.x + case, c.y + case)),
                case, c, style = Stroke(1.dp.toPx()))
            // Bezel and glass.
            val bezel = case - 6.dp.toPx()
            drawCircle(Color(0xFF07080B), bezel, c)
            val screen = bezel - 5.dp.toPx()
            drawCircle(Color.Black, screen, c)
            // Interval ring, gap at the top for the time (300° → 240°, clockwise), as on the watch.
            val ring = screen - 7.dp.toPx()
            val tl = Offset(c.x - ring, c.y - ring); val sz = Size(ring * 2, ring * 2)
            val sweep = 300f
            drawArc(Color(0xFFE3C27E).copy(alpha = 0.18f), startAngle = -60f, sweepAngle = sweep, useCenter = false, topLeft = tl, size = sz, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            if (state != null && state.progress > 0f) drawArc(Color(0xFFE3C27E), startAngle = -60f, sweepAngle = sweep * state.progress, useCenter = false, topLeft = tl, size = sz,
                style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            // Glass reflection.
            drawArc(Brush.linearGradient(listOf(Color(0x14FFFFFF), Color.Transparent), Offset(c.x - screen, c.y - screen), Offset(c.x, c.y)),
                startAngle = 180f, sweepAngle = 90f, useCenter = true, topLeft = Offset(c.x - screen, c.y - screen), size = Size(screen * 2, screen * 2))
        }
        if (state != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.prayer, style = MaterialTheme.typography.titleMedium.copy(fontFamily = if (state.arabic) Kufi else MaterialTheme.typography.titleMedium.fontFamily, fontSize = 17.sp),
                color = Color(0xFFF7F2E7))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(state.countdown, style = MaterialTheme.typography.displaySmall.merge(Tabular).copy(fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = (-0.5).sp),
                    color = Color.White, textAlign = TextAlign.Center)
            }
            Text(state.time, style = MaterialTheme.typography.labelMedium, color = Sky.GoldOnSky)
            Text(state.caption, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = Color(0xFFB7C0D2), maxLines = 1)
        }
    }
}
