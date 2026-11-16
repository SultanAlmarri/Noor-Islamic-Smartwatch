package com.noor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.ui.theme.NoorTheme
import kotlin.math.max

/**
 * The first thing on a tab's page (0.15): a large pane of glass lit from one corner by Noor's gold, a medallion with
 * the page's sign in it, and in big type what the page is about now (the next Adhan, the place the times are for).
 * [overline] is a quiet line over the [title]; [trailing] holds a control (a switch); [below] runs under it all,
 * the width of the card. The whole card is [onClick] when given.
 */
@Composable
fun HeroCard(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(30.dp)
    val glow = c.gold.copy(alpha = if (c.isDark) 0.26f else 0.20f)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.glassRaised)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), center = Offset(size.width * 0.12f, 0f), radius = max(size.width, size.height) * 0.85f))
            }
            .glassEdge(shape, c.glassEdge, c.glassEdgeLow)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = withTick(onClick)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medallion(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (overline != null) Text(overline.uppercase(), color = c.goldText, maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 1.0.sp, fontWeight = FontWeight.SemiBold))
                Text(title, color = c.textPrimary,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp))
                if (subtitle != null) Text(subtitle, color = subtitleColor ?: c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        }
        below?.invoke(this)
    }
}

/** A round seal of gold with a sign in it, for hero cards. */
@Composable
fun Medallion(icon: Painter, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF6E7C4), Color(0xFFE3C27E), Color(0xFFB98F45)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color(0xFF1C1407), modifier = Modifier.size(size * 0.5f))
    }
}
