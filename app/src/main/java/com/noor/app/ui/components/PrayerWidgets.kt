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
