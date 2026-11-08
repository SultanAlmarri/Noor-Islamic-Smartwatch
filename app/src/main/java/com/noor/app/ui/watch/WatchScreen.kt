package com.noor.app.ui.watch

import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import com.noor.app.ui.components.ScreenTitle
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.theme.Sky
import com.noor.app.data.WearSync
import com.noor.prayer.Prayer
import com.noor.prayer.WatchFaces
import com.noor.prayer.astro.SunPath
import kotlinx.coroutines.delay
import java.time.Duration

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.data.WatchNode
import com.noor.app.data.WatchSetupState
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.Hairline
import com.noor.app.ui.components.Noor
import com.noor.app.ui.components.Pill
import com.noor.app.ui.components.isArabicUi
import com.noor.app.util.Formatter
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchReport
import java.time.Instant
import java.time.ZoneId

@Composable
fun WatchScreen(contentPadding: PaddingValues, vm: WatchViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val lastSent by vm.lastSent.collectAsStateWithLifecycle()
    val installs by vm.installs.collectAsStateWithLifecycle()
    val faces by vm.faces.collectAsStateWithLifecycle()
    val faceChoice by vm.faceChoice.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncFailed by vm.syncFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = settings ?: AppSettings()
    val f = Formatter(isArabicUi(), s.numerals, s.timeFormat)
    val clock by produceState(System.currentTimeMillis()) {
        while (true) { delay(1000 - System.currentTimeMillis() % 1000); value = System.currentTimeMillis() }
    }
    val preview = remember(s, clock / 1000, f.arabic) { watchPreview(s, Instant.ofEpochMilli(clock), f) }
    WatchContent(
        contentPadding, state, vm.configAvailable(settings), lastSent, installs, faces, syncing, syncFailed, vm.setup.installedFromPlay,
        Formatter(isArabicUi(), s.numerals, s.timeFormat),
        WatchActions(
            install = vm::installOnWatch, useFace = vm::useNoorFace, open = vm::openOnWatch, sync = vm::syncNow,
            openGuide = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_GUIDE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            faceChoice = faceChoice, selectFace = vm::selectFace, sendFace = vm::sendFace,
        ),
        now = System.currentTimeMillis(),
        preview = preview,
    )
}

/** What the Noor watch app shows at [now], from the exact configuration the phone sends to the watch. */
fun watchPreview(s: AppSettings, now: Instant, f: Formatter): WatchPreviewState? {
    val config = WearSync.configFor(s, now.toEpochMilli()) ?: return null
    val st = runCatching { config.state(now) }.getOrNull() ?: return null
    val zone = config.zone
    val dhuhr = st.today.instant(Prayer.DHUHR)
    val glow = androidx.compose.ui.graphics.Color(com.noor.prayer.sky.SkyPalette.glow(SunPath.elevation(now, config.point), evening = dhuhr != null && now.isAfter(dhuhr)))
    return WatchPreviewState(
        prayer = f.prayerName(st.next.prayer, st.next.date),
        caption = (if (f.arabic) config.placeAr ?: config.placeEn else config.placeEn),
        countdown = f.countdown(Duration.between(now, st.next.at)), time = f.timeAt(st.next.at, zone), progress = st.progress, glow = glow, arabic = f.arabic,
    )
}

const val INSTALL_GUIDE = "https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/blob/main/docs/INSTALL.md"

class WatchActions(
    val install: (String) -> Unit, val useFace: (String) -> Unit, val open: (String) -> Unit, val sync: () -> Unit, val openGuide: () -> Unit,
    /** The watch-face editor (0.9.8): the face chosen, choosing another, and putting it on a watch. */
    val faceChoice: WatchFaces.Choice = WatchFaces.DEFAULT,
    val selectFace: (WatchFaces.Choice) -> Unit = {},
    val sendFace: (String, WatchReport?) -> Unit = { _, _ -> },
)

/** Stateless Watch tab, so every state (no watch, installing, ready…) can be rendered and tested. */
@Composable
fun WatchContent(
    contentPadding: PaddingValues,
    state: WatchSetupState?,
    configAvailable: Boolean,
    lastSent: Long?,
    installs: Map<String, Pending>,
    faces: Map<String, Pending>,
    syncing: Boolean,
    syncFailed: Boolean,
    fromPlay: Boolean,
    f: Formatter,
    actions: WatchActions,
    now: Long,
    preview: WatchPreviewState? = null,
) {
    val c = NoorTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = Noor.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Noor.Gap),
    ) {
        ScreenTitle(stringResource(R.string.watch_title))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WatchPreview(preview, description = stringResource(R.string.watch_preview_desc))
        }

        when {
            state == null -> GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.watch_checking), color = c.textSecondary)
                }
            }
            state.wearUnavailable -> EmptyCard(stringResource(R.string.watch_no_wear_os_title), stringResource(R.string.watch_no_wear_os))
            else -> {
                // Connected watches first, then watches that have Noor but are out of reach right now.
                val known = state.connected + state.withNoor.filter { id -> state.connected.none { it.id == id } }.map { WatchNode(it, "") }
                if (known.isEmpty()) EmptyCard(stringResource(R.string.watch_none_title), stringResource(R.string.watch_none_body))
                for (node in known) WatchCard(node, node.id in state.connected.map { it.id }, node.id in state.withNoor, state.reports[node.id],
                    configAvailable, lastSent, installs[node.id], faces[node.id], syncing, syncFailed, fromPlay, f, actions, now)
            }
        }

        // What Noor puts on the watch; everything there calculates on the watch itself, offline.
        SettingsSection(stringResource(R.string.watch_on_watch_title), footer = stringResource(R.string.offline_watch_footer)) {
            SettingsItem(Icons.Rounded.Watch, stringResource(R.string.watch_feature_app), stringResource(R.string.watch_feature_app_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.ViewCarousel, stringResource(R.string.watch_feature_tile), stringResource(R.string.watch_feature_tile_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.Widgets, stringResource(R.string.watch_feature_complication), stringResource(R.string.watch_feature_complication_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.NotificationsActive, stringResource(R.string.watch_feature_alerts), stringResource(R.string.watch_feature_alerts_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.CloudOff, stringResource(R.string.watch_feature_standalone), stringResource(R.string.watch_feature_standalone_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.Palette, stringResource(R.string.watch_feature_editor), stringResource(R.string.watch_feature_editor_desc), null)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WatchCard(
    node: WatchNode, connected: Boolean, hasNoor: Boolean, report: WatchReport?, configAvailable: Boolean, lastSent: Long?,
    install: Pending?, face: Pending?, syncing: Boolean, syncFailed: Boolean, fromPlay: Boolean, f: Formatter, actions: WatchActions, now: Long,
) {
    val c = NoorTheme.colors
    fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { f.timeText(it.hour * 60 + it.minute) }
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        // Header: watch and connection
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Watch, null, tint = c.gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name.ifBlank { stringResource(R.string.watch_generic_name) }, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) c.success else c.textMuted))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (connected) R.string.watch_connected_short else R.string.watch_not_connected_short),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    report?.let {
                        Text("  ·  Wear OS " + WatchViewModel.wearOsName(it.sdkInt), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
        }
        Hairline()

        // Step 1: Noor on the watch
        val installTimedOut = install != null && now - install.startedAtMs > WatchViewModel.PENDING_TIMEOUT_MS
        Step(
            n = 1, done = hasNoor, title = stringResource(R.string.step_app_title),
            body = when {
                hasNoor -> stringResource(R.string.step_app_done, report?.appVersionName ?: "")
                install != null && !install.reached -> stringResource(R.string.step_app_unreached)
                install != null && !installTimedOut -> stringResource(R.string.step_app_waiting)
                installTimedOut -> stringResource(R.string.step_app_timeout)
                !fromPlay -> stringResource(R.string.step_app_dev)
                else -> stringResource(R.string.step_app_todo)
            },
            working = install != null && !hasNoor && install.reached && !installTimedOut,
        ) {
            if (!hasNoor && connected) {
                if (fromPlay) PrimaryButton(stringResource(if (install == null) R.string.step_app_button else R.string.step_app_again)) { actions.install(node.id) }
                else SecondaryButton(stringResource(R.string.step_app_guide), actions.openGuide)
            }
        }
        Hairline(inset = 58.dp)

        // Step 2: settings reached the watch
        val received = report?.configReceivedAtMs
        Step(
            n = 2, done = hasNoor && received != null, title = stringResource(R.string.step_sync_title),
            body = when {
                !configAvailable -> stringResource(R.string.watch_needs_location)
                !hasNoor -> stringResource(R.string.step_after_install)
                syncFailed -> stringResource(R.string.watch_sync_failed)
                report?.usingWatchSettings == true -> stringResource(R.string.step_sync_own)
                received != null -> stringResource(R.string.step_sync_done, clock(received))
                lastSent != null -> stringResource(R.string.step_sync_sent, clock(lastSent))
                else -> stringResource(R.string.watch_not_sent)
            },
            working = syncing,
        ) {
            if (hasNoor && configAvailable) SecondaryButton(stringResource(if (syncing) R.string.watch_syncing else R.string.watch_sync_now), actions.sync)
        }
        Hairline(inset = 58.dp)

        // Step 3: the Noor watch face, with the editor (0.9.8).
        val faceTimedOut = face != null && now - face.startedAtMs > WatchViewModel.PENDING_TIMEOUT_MS
        val chosen = actions.faceChoice
        val onWatch = report?.faceVariant?.let(WatchFaces::parse)
        val showing = report?.faceActive == true && report.faceVariant == chosen.id
        val sending = face != null && face.reached && !faceTimedOut && report?.faceVariant != chosen.id
        Step(
            n = 3, done = report?.faceActive == true, title = stringResource(R.string.step_face_title),
            body = when {
                !hasNoor -> stringResource(R.string.step_after_install)
                report == null -> stringResource(R.string.step_face_open_first)
                !report.facePushSupported -> stringResource(R.string.step_face_needs6, WatchViewModel.wearOsName(report.sdkInt))
                face != null && !face.reached -> stringResource(R.string.step_app_unreached)
                sending -> stringResource(R.string.face_sending)
                report.faceActive && onWatch != null -> stringResource(R.string.face_on_watch, faceLabel(onWatch))
                report.faceActive -> stringResource(R.string.step_face_done)
                // Wear OS lets the watch app make its face active once; after that the user picks it, and that is fine.
                report.faceNeedsPick == true -> stringResource(R.string.face_pick_on_watch)
                face != null && !faceTimedOut -> stringResource(R.string.step_face_waiting)
                report.faceInstalledVersion != null -> stringResource(R.string.step_face_installed)
                else -> stringResource(R.string.step_face_todo)
            },
            working = sending,
        ) {}
        if (hasNoor && report?.facePushSupported == true) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FaceEditor(chosen, actions.selectFace)
                Spacer(Modifier.height(6.dp))
                if (connected && !showing) PrimaryButton(stringResource(R.string.face_send)) { actions.sendFace(node.id, report) }
                Text(stringResource(R.string.face_changes_hint), style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (hasNoor && connected) {
            Hairline()
            TextButton(onClick = { actions.open(node.id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.watch_open_on_watch), color = c.goldText)
            }
        }
    }
}

@Composable
private fun Step(n: Int, done: Boolean, title: String, body: String, working: Boolean, action: @Composable () -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done) c.success.copy(alpha = 0.18f) else c.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(16.dp))
                working -> CircularProgressIndicator(Modifier.size(14.dp), color = c.gold, strokeWidth = 2.dp)
                else -> Text("$n", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Button(onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(top = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onGold)) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    com.noor.app.ui.components.PillButton(text, Modifier.padding(top = 2.dp), onClick)
}

@Composable
private fun EmptyCard(title: String, body: String) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Header(Icons.Rounded.Watch, title, null)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
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
