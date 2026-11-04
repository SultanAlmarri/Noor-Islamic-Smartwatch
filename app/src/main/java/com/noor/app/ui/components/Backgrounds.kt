package com.noor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.ui.theme.NoorTheme
import kotlin.math.cos
import kotlin.math.sin

/** Night-sky gradient with a faint eight-pointed-star lattice (khatam), drawn, not bitmapped. */
@Composable
fun NoorBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = NoorTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 132.dp.toPx()
            val r = cell * 0.34f
            val stroke = Stroke(width = 1.dp.toPx())
            var y = -cell / 2
            var row = 0
            while (y < size.height + cell) {
                var x = if (row % 2 == 0) 0f else cell / 2
                while (x < size.width + cell) {
                    drawPath(star(Offset(x, y), r, r * 0.72f), c.pattern, style = stroke)
                    x += cell
                }
                y += cell * 0.86f; row++
            }
        }
        content()
    }
}

/** Two overlapping squares rotated 45 degrees form the eight-pointed star outline. */
private fun star(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 16) {
        val ang = Math.toRadians((-90.0 + i * 22.5))
        val rr = if (i % 2 == 0) outer else inner
        val px = center.x + (rr * cos(ang)).toFloat(); val py = center.y + (rr * sin(ang)).toFloat()
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/** Frosted card with a hairline gold edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: Dp = 20.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .clip(shape)
            .background(if (highlighted) c.surfaceRaised else c.surface)
            .border(1.dp, Brush.linearGradient(listOf(c.outline, Color.Transparent, c.outline.copy(alpha = c.outline.alpha * 0.5f))), shape)
            .padding(padding),
        content = content,
    )
}
