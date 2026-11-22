package com.noor.app.ui.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The light under the watch when the sky of the moment is not known. */
private val RestingGlow = Color(0xFF3B4A7A)

/**
 * A drawn Galaxy Watch–style case: brushed graphite lit from the upper left, two crowns, a black bezel, and a soft
 * light under it in the colour of the sky of the moment ([glow]), so phone and watch share the same light. What the
 * watch shows is [screen], laid out in the round glass ([diameter] less the case and bezel).
 *
 * 0.25: the case alone. Until 0.24 it held a hand-drawn copy of the watch app's home screen (a ring and a countdown),
 * while the face stood further down the page as a second watch that said something else (the review: "two different
 * watches on one page"). The page's one watch now shows the face itself, drawn by the face's own renderer
 * ([com.noor.app.ui.watch.studio.StudioCard]), and a copy of a screen can no longer fall behind the watch app.
 */
@Composable
fun WatchCase(modifier: Modifier = Modifier, diameter: Dp = 196.dp, glow: Color? = null, screen: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(diameter + 28.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter + 28.dp)) {
            val c = center
            val outer = size.minDimension / 2
            // Soft light under the watch, tinted by the sky of the moment.
            drawCircle(Brush.radialGradient(listOf((glow ?: RestingGlow).copy(alpha = 0.35f), Color.Transparent), c, outer * 1.0f), outer, c)
            // Case: brushed graphite with light from the upper left, and the crowns on the side.
            val case = outer - CaseInset.toPx()
            val crownW = 7.dp.toPx(); val crownH = 22.dp.toPx()
            for (dy in listOf(-0.42f, 0.42f)) drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6C7380), Color(0xFF2A2E36))),
                topLeft = Offset(c.x + case - 2.dp.toPx(), c.y + dy * case - crownH / 2), size = Size(crownW, crownH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawCircle(Brush.linearGradient(listOf(Color(0xFF8A919D), Color(0xFF3B404A), Color(0xFF15171B), Color(0xFF4A505B)),
                Offset(c.x - case, c.y - case), Offset(c.x + case, c.y + case)), case, c)
            drawCircle(Brush.linearGradient(listOf(Color(0x66FFFFFF), Color.Transparent, Color(0x22FFFFFF)), Offset(c.x - case, c.y - case), Offset(c.x + case, c.y + case)),
                case, c, style = Stroke(1.dp.toPx()))
            // Bezel and glass.
            drawCircle(Color(0xFF07080B), case - CaseRim.toPx(), c)
        }
        Box(Modifier.size(diameter + 28.dp - (CaseInset + CaseRim + BezelRim) * 2).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center, content = screen)
    }
}

/** From the edge of the drawing to the case (the room the glow and the crowns take), the case's rim, the bezel's. */
private val CaseInset = 10.dp
private val CaseRim = 6.dp
private val BezelRim = 5.dp
