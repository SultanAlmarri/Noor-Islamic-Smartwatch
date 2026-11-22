package com.noor.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.R
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * The welcome's pictures (0.25), all drawn here: Minhad's mark, and one symbol for each thing the welcome asks for.
 * A symbol is a disc of glass with a gold sign on it and its own signal round it (a place found, a way travelled,
 * a call heard). Strokes are plain colours only (a gradient on a stroke has killed an emulator's graphics, 0.18).
 */

/**
 * Where the crescent and its star sit in the mark's drawing (`ic_splash_mark`, written by tools/brand/logo.py: the
 * crescent's circle is centred on 52.5, 46 with radius 22 in a field of 108). The welcome shows the mark without the
 * launcher's margins, so it needs to know.
 */
internal object Mark {
    const val FIELD = 108f
    const val CX = 52.5f
    const val CY = 46f
    const val R = 22f
    /** The side of the square that holds the crescent and its star with a little air, as a part of the field. */
    const val BOX = 2f * (R + 3f) / FIELD
}

/** The mark alone, its crescent [crescent] across, in a box that just holds it. */
@Composable
internal fun MinhadMark(crescent: Dp, modifier: Modifier = Modifier) {
    val mark = painterResource(R.drawable.ic_splash_mark)
    val field = crescent * (Mark.FIELD / (2f * Mark.R))
    Canvas(modifier.size(field * Mark.BOX).clearAndSetSemantics { }) { drawMark(mark, field.toPx(), center) }
}

/** Draws the mark's whole field, [field] px across, with the crescent's centre at [at]. */
internal fun DrawScope.drawMark(mark: Painter, field: Float, at: Offset, alpha: Float = 1f) {
    translate(at.x - field * Mark.CX / Mark.FIELD, at.y - field * Mark.CY / Mark.FIELD) { with(mark) { draw(Size(field, field), alpha = alpha) } }
}

internal enum class Symbol { PLACE, TRAVEL, ADHAN, MINUTE }

private val GoldLight = Color(0xFFFFF6DE)
private val Gold = Color(0xFFF3D08C)
private val GoldDeep = Color(0xFFDDA450)

/**
 * One of the welcome's symbols. [phase] 0..1 turns endlessly while ambient motion is allowed (the signal moves);
 * below 0 the symbol is still.
 */
@Composable
internal fun SymbolArt(symbol: Symbol, modifier: Modifier = Modifier, phase: () -> Float = { -1f }) {
    val mark = painterResource(R.drawable.ic_splash_mark)
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        val c = center
        val disc = s * 0.33f
        val hair = 1.25.dp.toPx()
        val t = phase()
        // The glass: a soft light round it, the pane, its lit edge.
        drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.16f), 0.62f to Color.White.copy(alpha = 0.07f), 1f to Color.Transparent, center = c, radius = s * 0.5f), s * 0.5f, c)
        drawCircle(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 1f to Color.White.copy(alpha = 0.07f), startY = c.y - disc, endY = c.y + disc), disc, c)
        // (Its edge is lit from above: a plain line all round, a brighter one over its top.)
        drawCircle(Color.White.copy(alpha = 0.26f), disc, c, style = Stroke(hair))
        drawArc(Color.White.copy(alpha = 0.42f), 205f, 130f, false, Offset(c.x - disc, c.y - disc), Size(disc * 2f, disc * 2f), style = Stroke(hair, cap = StrokeCap.Round))
        // The sign's own light on the glass.
        drawCircle(Brush.radialGradient(0f to Gold.copy(alpha = 0.30f), 1f to Color.Transparent, center = c, radius = disc * 0.86f), disc * 0.86f, c)
        val gold = Brush.linearGradient(0f to GoldLight, 0.5f to Gold, 1f to GoldDeep, start = Offset(c.x - disc * 0.6f, c.y - disc * 0.7f), end = Offset(c.x + disc * 0.5f, c.y + disc * 0.7f))
        val r1 = s * 0.405f; val r2 = s * 0.475f
        when (symbol) {
            Symbol.PLACE -> {
                // A place found: rings going out from it.
                ring(c, r1, 0.34f, hair); ring(c, r2, 0.16f, hair)
                if (t >= 0f) ring(c, disc + (s * 0.5f - disc) * t, 0.5f * sin(t * PI.toFloat()), hair * 1.4f)
                pin(Offset(c.x, c.y - s * 0.05f), s * 0.112f, c.y + s * 0.185f, gold)
            }
            Symbol.TRAVEL -> {
                ring(c, r1, 0.34f, hair); ring(c, r2, 0.16f, hair)
                // A way travelled over the Earth's round: from where one was, in steps, to where one is.
                val ground = Offset(c.x, c.y + disc * 1.45f); val gr = disc * 1.2f
                fun on(deg: Float) = Offset(ground.x + gr * cos(Math.toRadians(deg.toDouble())).toFloat(), ground.y + gr * sin(Math.toRadians(deg.toDouble())).toFloat())
                drawArc(Color.White.copy(alpha = 0.42f), 234f, 72f, false, Offset(ground.x - gr, ground.y - gr), Size(gr * 2f, gr * 2f), style = Stroke(hair * 1.4f, cap = StrokeCap.Round))
                val from = on(249f); val to = on(291f)
                val over = Offset(c.x - disc * 0.03f, c.y - disc * 0.42f)
                drawCircle(Color.White, disc * 0.07f, from)
                val steps = 5
                for (i in 1..steps) {
                    val f = i / (steps + 1f)
                    val p = Offset((1 - f) * (1 - f) * from.x + 2 * f * (1 - f) * over.x + f * f * to.x, (1 - f) * (1 - f) * from.y + 2 * f * (1 - f) * over.y + f * f * to.y)
                    // (The step the signal is at shines.)
                    val lit = if (t >= 0f) (1f - kotlin.math.abs(t * (steps + 3f) - 1f - i).coerceAtMost(1.5f) / 1.5f) else 0f
                    drawCircle(Color.White, disc * (0.036f + 0.016f * lit), p, alpha = 0.6f + 0.4f * lit)
                }
                pin(Offset(to.x, to.y - disc * 0.47f), disc * 0.225f, to.y, gold)
            }
            Symbol.ADHAN -> {
                // A call heard: its sound going out on both sides.
                listOf(r1 to 0.62f, r2 to 0.32f).forEachIndexed { i, (r, a) ->
                    val wave = if (t >= 0f) 0.55f + 0.45f * sin((t - i * 0.22f) * 2f * PI.toFloat()) else 1f
                    val box = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
                    for (start in listOf(-30f, 150f)) drawArc(Color.White.copy(alpha = a * wave), start, 60f, false, box.topLeft, box.size, style = Stroke(hair * 2f, cap = StrokeCap.Round))
                }
                drawMark(mark, disc * 0.92f * Mark.FIELD / (2f * Mark.R), Offset(c.x - disc * 0.04f, c.y + disc * 0.02f))
            }
            Symbol.MINUTE -> {
                // The very minute: a clock, its twelve marks round the glass, its hands on the hour.
                fun at(deg: Float, r: Float) = Offset(c.x + r * cos(Math.toRadians(deg.toDouble())).toFloat(), c.y + r * sin(Math.toRadians(deg.toDouble())).toFloat())
                for (i in 0 until 12) {
                    val quarter = i % 3 == 0
                    // (The mark the signal passes shines.)
                    val round = ((t * 12f - i) % 12f + 12f) % 12f
                    val lit = if (t >= 0f) (1f - minOf(round, 12f - round)).coerceIn(0f, 1f) else 0f
                    drawCircle(Color.White, hair * (if (quarter) 2.1f else 1.35f) * (1f + 0.35f * lit), at(i * 30f - 90f, r1), alpha = ((if (quarter) 0.9f else 0.55f) + 0.45f * lit).coerceAtMost(1f))
                }
                ring(c, r2, 0.16f, hair)
                drawLine(Gold, c, at(-150f, disc * 0.42f), strokeWidth = disc * 0.14f, cap = StrokeCap.Round)
                drawLine(GoldLight, c, at(-90f, disc * 0.66f), strokeWidth = disc * 0.105f, cap = StrokeCap.Round)
                drawCircle(gold, disc * 0.13f, c)
                drawCircle(Color(0xFF3A3560), disc * 0.048f, c)
            }
        }
    }
}

private fun DrawScope.ring(c: Offset, r: Float, alpha: Float, width: Float) {
    if (alpha > 0f) drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), r, c, style = Stroke(width))
}

/** A map pin: a head of radius [r] round [head], coming down to a point at [tipY], with the sky showing through its eye. */
private fun DrawScope.pin(head: Offset, r: Float, tipY: Float, brush: Brush) {
    val d = tipY - head.y
    val half = Math.toDegrees(acos((r / d).toDouble())).toFloat()
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        arcTo(Rect(head.x - r, head.y - r, head.x + r, head.y + r), 90f + half, 360f - 2f * half, true)
        lineTo(head.x, tipY)
        close()
        addOval(Rect(head.x - r * 0.4f, head.y - r * 0.4f, head.x + r * 0.4f, head.y + r * 0.4f))
    }
    drawPath(path, brush)
}

/** A small tile of colour with a white sign on it, as the rows of an iPhone's Settings have: what Minhad gives. */
@Composable
internal fun SignTile(sign: Painter, top: Color, low: Color, side: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(side).clip(RoundedCornerShape(side * 0.285f)).background(Brush.verticalGradient(listOf(top, low))).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) { Icon(sign, null, tint = Color.White, modifier = Modifier.size(side * 0.59f)) }
}
