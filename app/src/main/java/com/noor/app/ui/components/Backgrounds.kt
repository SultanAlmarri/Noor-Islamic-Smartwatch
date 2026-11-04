package com.noor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.ui.theme.NoorTheme
import kotlin.math.cos
import kotlin.math.sin

/** Design tokens shared by every screen: one corner radius family, one spacing scale. */
object Noor {
    val CardRadius = 22.dp
    val RowRadius = 14.dp
    val ScreenPadding = 20.dp
    val Gap = 14.dp
}

/** Plain, calm screen background. Decoration lives in a few deliberate places, not behind everything. */
@Composable
fun NoorBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(NoorTheme.colors.background), content = content)
}

/** Solid card. In the dark theme a hairline edge separates it from the background. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = Noor.CardRadius,
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
            .then(if (c.isDark) Modifier.border(0.5.dp, c.separator, shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Thin separator between rows of a grouped list. */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(NoorTheme.colors.separator))
}

/** The eight-pointed star (khatam): two squares rotated 45 degrees. Used sparingly as Noor's mark. */
fun DrawScope.khatam(center: Offset, outer: Float, color: Color, strokeWidth: Float) {
    val inner = outer * 0.72f
    val path = Path().apply {
        for (i in 0 until 16) {
            val ang = Math.toRadians(-90.0 + i * 22.5)
            val rr = if (i % 2 == 0) outer else inner
            val px = center.x + (rr * cos(ang)).toFloat(); val py = center.y + (rr * sin(ang)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color, style = Stroke(strokeWidth))
}

/** A faint khatam watermark for hero cards. */
@Composable
fun KhatamWatermark(modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    Canvas(modifier) {
        val r = size.minDimension / 2f
        khatam(center, r, c.gold.copy(alpha = if (c.isDark) 0.07f else 0.09f), 1.2.dp.toPx())
        khatam(center, r * 0.62f, c.gold.copy(alpha = if (c.isDark) 0.05f else 0.07f), 1.dp.toPx())
    }
}
