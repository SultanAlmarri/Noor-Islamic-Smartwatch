package com.noor.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.DailyConfig
import com.noor.app.notify.StatusNotification
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import com.noor.app.util.Formatter
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * (0.25, second review) What the notification bar's page is about, shown: the notification as it stands in the shade
 * now, worked out by the same code that builds the real one (`StatusNotification.content`), its countdown counting.
 * The page held a single switch before, and the reviewer asked that it earn its place. A drawing, not the system's
 * own: the shade's look differs from phone to phone. Dims while the notification is off.
 */
@Composable
internal fun StatusPreview(s: AppSettings, on: Boolean, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    val app = LocalContext.current.applicationContext as? NoorApplication
    val ar = isArabicUi()
    val f = androidx.compose.runtime.remember(ar, s.numerals, s.timeFormat) { Formatter(ar, s.numerals, s.timeFormat) }
    val config by produceState<com.noor.prayer.WatchConfig?>(null, s) { value = runCatching { DailyConfig.get(s, app?.container?.timetables) }.getOrNull() }
    val now by produceState(Instant.now()) { while (true) { delay(1000L - System.currentTimeMillis() % 1000L); value = Instant.now() } }
    val cfg = config ?: return
    val content = runCatching { StatusNotification.content(cfg, s, now, ar) }.getOrNull() ?: return
    val countdown = content.countdown(now, f)
    Column(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (on) 1f else 0.38f }
            .clip(RoundedCornerShape(24.dp)).background(c.glassRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(content.prayer, content.time, countdown, content.hijri, content.place).joinToString(", ") },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(c.gold.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_notification), null, tint = c.gold, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name) + " · " + stringResource(R.string.now), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(content.prayer + " · " + content.time, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(content.hijri, content.place).joinToString(" · "), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = c.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(countdown, style = MaterialTheme.typography.headlineMedium.merge(Tabular).copy(fontWeight = FontWeight.Light, fontSize = 30.sp), color = if (on) c.textPrimary else c.textSecondary, maxLines = 1)
            }
        }
    }
}
