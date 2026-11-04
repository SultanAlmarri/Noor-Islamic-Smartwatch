package com.noor.app.ui.watch

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Pill
import com.noor.app.ui.theme.NoorTheme

/** Honest status of the watch side: the 1.3.2 face works on its own; sync and the editor are not built yet. */
@Composable
fun WatchScreen(contentPadding: PaddingValues) {
    val c = NoorTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.watch_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        StatusCard(Icons.Rounded.Watch, stringResource(R.string.watch_face_installed), stringResource(R.string.watch_face_version, "1.3.2"), null)
        StatusCard(Icons.Rounded.Sync, stringResource(R.string.watch_sync_title), stringResource(R.string.watch_sync_body), stringResource(R.string.in_development))
        StatusCard(Icons.Rounded.Palette, stringResource(R.string.watch_editor_title), stringResource(R.string.watch_editor_body), stringResource(R.string.in_development))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusCard(icon: ImageVector, title: String, body: String, badge: String?) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.gold, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (badge != null) Pill(badge, filled = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}
