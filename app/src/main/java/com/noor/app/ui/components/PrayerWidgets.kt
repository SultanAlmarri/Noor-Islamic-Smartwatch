package com.noor.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.Prayer
import kotlin.math.cos
import kotlin.math.sin

fun prayerIcon(p: Prayer): Int = when (p) {
    Prayer.FAJR -> R.drawable.ic_prayer_fajr
    Prayer.SHURUQ -> R.drawable.ic_prayer_sunrise
    Prayer.DHUHR -> R.drawable.ic_prayer_dhuhr
    Prayer.ASR -> R.drawable.ic_prayer_asr
    Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
    Prayer.ISHA -> R.drawable.ic_prayer_isha
}

@Composable
fun PrayerIcon(p: Prayer, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(painterResource(prayerIcon(p)), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Progress ring from the previous event to the next, with a glowing head. */
@Composable
fun CountdownRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 6.dp, content: @Composable () -> Unit) {
    val c = NoorTheme.colors
    val animated by animateFloatAsState(progress, tween(900), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w * 1.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(c.textMuted.copy(alpha = 0.22f), -90f, 360f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            val sweep = 360f * animated.coerceIn(0.002f, 1f)
            drawArc(
                Brush.sweepGradient(listOf(c.goldDeep, c.gold, c.goldSoft, c.goldDeep)),
                -90f, sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round),
            )
            val ang = Math.toRadians((-90f + sweep).toDouble())
            val r = arcSize.width / 2
            val head = Offset(center.x + (r * cos(ang)).toFloat(), center.y + (r * sin(ang)).toFloat())
            drawCircle(c.gold.copy(alpha = 0.25f), w * 2.2f, head)
            drawCircle(c.goldSoft, w * 0.75f, head)
        }
        content()
    }
}

@Composable
fun Pill(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) c.gold else Color.Transparent)
            .border(1.dp, c.gold.copy(alpha = if (filled) 0f else 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelSmall,
            color = if (filled) (if (c.isDark) Color(0xFF1A1206) else Color.White) else c.gold,
        )
    }
}

/** One line of the day's timetable. */
@Composable
fun PrayerRow(
    prayer: Prayer, name: String, secondaryName: String?, time: String, suffix: String?,
    isCurrent: Boolean, isNext: Boolean, passed: Boolean, nowLabel: String, nextLabel: String,
) {
    val c = NoorTheme.colors
    val bg by animateColorAsState(if (isCurrent) c.gold.copy(alpha = if (c.isDark) 0.13f else 0.12f) else Color.Transparent, label = "rowBg")
    val textColor = when {
        isCurrent -> c.textPrimary
        passed -> c.textMuted
        else -> c.textPrimary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (isCurrent) c.gold.copy(alpha = 0.18f) else c.surface),
            contentAlignment = Alignment.Center,
        ) { PrayerIcon(prayer, 22.dp, if (isCurrent || isNext) c.gold else c.textSecondary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = textColor,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                if (isCurrent) Pill(nowLabel, filled = true)
                if (isNext) Pill(nextLabel, filled = false)
            }
            if (secondaryName != null) Text(secondaryName, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = MaterialTheme.typography.titleLarge, color = textColor, fontWeight = FontWeight.Medium)
            if (suffix != null) {
                Spacer(Modifier.width(4.dp))
                Text(suffix, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
