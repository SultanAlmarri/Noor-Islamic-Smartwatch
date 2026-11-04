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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.WatchStatus
import com.noor.app.data.WearSync
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Pill
import com.noor.app.ui.theme.NoorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** The watch side as it really is: connection, whether Noor is on the watch, and what it receives. */
@Composable
fun WatchScreen(contentPadding: PaddingValues) {
    val c = NoorTheme.colors
    val context = LocalContext.current
    val container = (context.applicationContext as NoorApplication).container
    val settings by container.settings.settings.collectAsState(initial = null)
    val lastSent by container.wear.lastSent.collectAsState()
    var status by remember { mutableStateOf<WatchStatus?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Connection changes on its own (Bluetooth range, watch reboot): poll gently while this screen is open.
    LaunchedEffect(Unit) { while (true) { status = container.wear.status(); delay(15_000) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.watch_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)

        // Connection and sync
        GlassCard(Modifier.fillMaxWidth(), highlighted = status?.withNoor?.isNotEmpty() == true) {
            Header(Icons.Rounded.Sync, stringResource(R.string.watch_sync_title), null)
            Spacer(Modifier.height(12.dp))
            val s = status
            when {
                s == null -> Line(false, stringResource(R.string.watch_checking))
                s.unavailable -> Line(false, stringResource(R.string.watch_no_wear_os))
                else -> {
                    Line(s.connected.isNotEmpty(),
                        if (s.connected.isEmpty()) stringResource(R.string.watch_none_connected)
                        else stringResource(R.string.watch_connected, s.connected.joinToString()))
                    Line(s.withNoor.isNotEmpty(),
                        if (s.withNoor.isEmpty()) stringResource(R.string.watch_app_missing)
                        else stringResource(R.string.watch_app_installed, s.withNoor.joinToString()))
                }
            }
            val sentText = lastSent?.let { stringResource(R.string.watch_last_sent, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) }
            val cfg = settings?.let { WearSync.configFor(it, 0L) }
            Line(cfg != null && lastSent != null, when {
                cfg == null -> stringResource(R.string.watch_needs_location)
                syncFailed -> stringResource(R.string.watch_sync_failed)
                sentText != null -> sentText
                else -> stringResource(R.string.watch_not_sent)
            })
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    scope.launch {
                        syncing = true
                        syncFailed = !container.wear.syncNow()
                        status = container.wear.status()
                        syncing = false
                    }
                },
                enabled = !syncing && cfg != null,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = Color(0xFF1A1206)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(stringResource(if (syncing) R.string.watch_syncing else R.string.watch_sync_now), style = MaterialTheme.typography.titleSmall) }
        }

        // What the watch app provides
        GlassCard(Modifier.fillMaxWidth()) {
            Header(Icons.Rounded.Widgets, stringResource(R.string.watch_on_watch_title), null)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.watch_on_watch_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }

        // Watch face
        GlassCard(Modifier.fillMaxWidth()) {
            Header(Icons.Rounded.Watch, stringResource(R.string.watch_face_installed), null)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.watch_face_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Header(Icons.Rounded.Palette, stringResource(R.string.watch_editor_title), stringResource(R.string.in_development))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.watch_editor_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(icon: ImageVector, title: String, badge: String?) {
    val c = NoorTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (badge != null) Pill(badge, filled = false)
    }
}

@Composable
private fun Line(ok: Boolean, text: String) {
    val c = NoorTheme.colors
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (ok) c.success else c.textMuted, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (ok) c.textPrimary else c.textSecondary)
    }
}
