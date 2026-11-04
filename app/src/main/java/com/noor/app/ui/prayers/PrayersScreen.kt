package com.noor.app.ui.prayers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.components.CountdownRing
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.PrayerRow
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer

@Composable
fun isArabicUi(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

@Composable
fun PrayersScreen(contentPadding: PaddingValues, onOpenLocation: () -> Unit, onOpenAccuracy: () -> Unit, vm: PrayersViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshIfStale() }
    val u = ui ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NoorTheme.colors.gold) }
    val ar = isArabicUi()
    val f = Formatter(ar, u.settings.numerals, u.settings.timeFormat)
    val c = NoorTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        // Header: place + dates
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenLocation).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (u.place?.fromGps == true) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = c.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        u.place?.let { if (ar) it.nameAr ?: it.nameEn else it.nameEn } ?: stringResource(R.string.location_unknown),
                        style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                    )
                }
                if (u.today != null && u.hijri != null) {
                    Text(f.hijri(u.hijri), style = MaterialTheme.typography.bodyMedium, color = c.gold)
                    Text(f.gregorian(u.today), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (u.place?.fromGps == true) {
                IconButton(onClick = vm::refresh, enabled = !u.refreshing) {
                    if (u.refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = c.gold, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh_location), tint = c.textSecondary)
                }
            }
        }

        if (u.state == null || u.profile == null) {
            NoLocationCard(onOpenLocation)
            return@Column
        }
        val st = u.state
        // Hero: next prayer with live countdown
        GlassCard(Modifier.fillMaxWidth(), highlighted = true, padding = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_prayer).uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(st.next.prayer, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nextName") { p ->
                        Column {
                            Text(f.prayerName(p), style = if (ar) MaterialTheme.typography.displaySmall.copy(fontFamily = Kufi) else MaterialTheme.typography.displaySmall, color = c.textPrimary)
                            Text(if (ar) p.en else p.ar, style = MaterialTheme.typography.titleMedium.copy(fontFamily = if (ar) MaterialTheme.typography.titleMedium.fontFamily else Kufi), color = c.gold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val nextMin = st.next.at.atZone(u.place!!.zone).let { it.hour * 60 + it.minute }
                    val (t, sfx) = f.time(nextMin)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(t, style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, fontWeight = FontWeight.Light)
                        if (sfx != null) Text(" $sfx", style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                CountdownRing(st.progress, Modifier.size(132.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PrayerIcon(st.next.prayer, 26.dp, c.gold)
                        Spacer(Modifier.height(4.dp))
                        Text(f.countdown(st.untilNext), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.remaining), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Today's timetable
        GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            val day = st.today
            val nowLabel = stringResource(R.string.now)
            val nextLabel = stringResource(R.string.next)
            for (p in Prayer.entries) {
                val m = day.minutes[p]
                val (t, sfx) = if (m != null) f.time(m) else "—" to null
                val isCurrent = st.current?.let { it.prayer == p && it.date == day.date } ?: false
                val isNext = st.next.prayer == p && st.next.date == day.date
                val passed = day.instant(p)?.let { it.isBefore(java.time.Instant.now()) } ?: false
                PrayerRow(
                    prayer = p, name = f.prayerName(p), secondaryName = if (ar) p.en else p.ar,
                    time = t, suffix = sfx, isCurrent = isCurrent, isNext = isNext, passed = passed && !isCurrent,
                    nowLabel = nowLabel, nextLabel = nextLabel,
                )
            }
        }

        // Authority and accuracy
        GlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpenAccuracy), padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Verified, null, tint = c.gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (ar) u.profile.nameAr else u.profile.nameEn, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text(if (ar) u.profile.validationAr else u.profile.validationEn, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.offline_ready), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NoLocationCard(onOpenLocation: () -> Unit) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.perm_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenLocation) { Text(stringResource(R.string.set_location), color = c.gold) }
    }
}
