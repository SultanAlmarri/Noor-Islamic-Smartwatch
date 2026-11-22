package com.noor.app.ui.watch

import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.prayer.Prayer
import com.noor.prayer.WatchFaces
import com.noor.prayer.astro.SunPath
import kotlinx.coroutines.delay

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.noor.app.ui.components.isArabicUi
import com.noor.app.util.Formatter
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchReport
import java.time.Instant
import java.time.ZoneId

@Composable
fun WatchScreen(contentPadding: PaddingValues, vm: WatchViewModel = viewModel(), onOpenAdhan: () -> Unit = {}, onBack: (() -> Unit)? = null, onOpenStudio: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val lastSent by vm.lastSent.collectAsStateWithLifecycle()
    val installs by vm.installs.collectAsStateWithLifecycle()
    val faces by vm.faces.collectAsStateWithLifecycle()
    val faceChoice by vm.faceChoice.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncFailed by vm.syncFailed.collectAsStateWithLifecycle()
    // (Worked out off the main thread once for the settings and the day: eight weeks of times. Until the view model
    // has it, the one already kept for these settings, if any, so a page opened again is whole at once.)
    val worked by vm.config.collectAsStateWithLifecycle()
    val config = worked ?: remember(settings) { settings?.let { com.noor.app.data.DailyConfig.peek(it, null) } }
    val context = LocalContext.current
    val s = settings ?: AppSettings()
    // The page's own clock: for the steps that wait on the watch (minutes) and the light under the watch (the sky's,
    // which changes slowly), a look every quarter of a minute is plenty. (Until 0.24 the page was composed again every
    // second, for a countdown it no longer draws; the face at its top redraws its own seconds without composing.)
    val clock by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }
    val glow = remember(config, clock / 60_000) { config?.let { skyGlow(it, Instant.ofEpochMilli(clock)) } }
    // 0.18: Minhad Studio's design being edited, shown live on this page; re-read when coming back from the editor.
    var studioDesign by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.noor.app.ui.watch.studio.StudioRepository(context).draft) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { studioDesign = com.noor.app.ui.watch.studio.StudioRepository(context).draft; onPauseOrDispose { } }
    val configAvailable = remember(settings) { vm.configAvailable(settings) }
    WatchContent(
        contentPadding, state, configAvailable, lastSent, installs, faces, syncing, syncFailed, vm.setup.installedFromPlay,
        Formatter(isArabicUi(), s.numerals, s.timeFormat),
        WatchActions(
            install = vm::installOnWatch, useFace = vm::useNoorFace, open = vm::openOnWatch, sync = vm::syncNow,
            openGuide = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_GUIDE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            faceChoice = faceChoice, selectFace = vm::selectFace, sendFace = vm::sendFace, openAdhan = onOpenAdhan,
            openStudio = onOpenStudio, studioDesign = studioDesign, studioConfig = config, glow = glow,
        ),
        now = clock, onBack = onBack,
    )
}

/** The colour of the sky at the place at [now], for the light under the watch: phone and watch share the same light. */
internal fun skyGlow(config: com.noor.prayer.WatchConfig, now: Instant): androidx.compose.ui.graphics.Color? = runCatching {
    val dhuhr = config.state(now).today.instant(Prayer.DHUHR)
    androidx.compose.ui.graphics.Color(com.noor.prayer.sky.SkyPalette.glow(SunPath.elevation(now, config.point), evening = dhuhr != null && now.isAfter(dhuhr)))
}.getOrNull()

const val INSTALL_GUIDE = "https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/blob/main/docs/INSTALL.md"

class WatchActions(
    val install: (String) -> Unit, val useFace: (String) -> Unit, val open: (String) -> Unit, val sync: () -> Unit, val openGuide: () -> Unit,
    /** The watch-face editor (0.9.8): the face chosen, choosing another, and putting it on a watch. */
    val faceChoice: WatchFaces.Choice = WatchFaces.DEFAULT,
    val selectFace: (WatchFaces.Choice) -> Unit = {},
    val sendFace: (String, WatchReport?) -> Unit = { _, _ -> },
    /** 0.15: the watch's alerts are chosen on the Adhan tab: its row here opens it. */
    val openAdhan: () -> Unit = {},
    /**
     * 0.18: Minhad Studio: the editor, and the design shown at the top of the page (none: no watch there), drawn from
     * the configuration the phone sends the watch; 0.25: the sky's colour of the moment, for the light under it.
     */
    val openStudio: () -> Unit = {},
    val studioDesign: com.noor.prayer.face.FaceDesign? = null,
    val studioConfig: com.noor.prayer.WatchConfig? = null,
    val glow: androidx.compose.ui.graphics.Color? = null,
)

/**
 * Stateless Watch page, so every state (no watch, installing, ready…) can be rendered and tested. The page opens from
 * Settings (0.15.2): [onBack] gives it the slim bar and the back chevron of every pushed page.
 */
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
    onBack: (() -> Unit)? = null,
) {
    val c = NoorTheme.colors
    com.noor.app.ui.components.NoorPage(stringResource(R.string.watch_title), contentPadding, onBack = onBack) {
        // 0.25: one watch at the top, and it shows the face: the user's own design, drawn by the face's renderer (this
        // moment's time, today's date, the app's language). It says "on your watch" only when the watch reports it.
        actions.studioDesign?.let { d ->
            val studioOn = state?.reports?.values?.any { it.studioActive == true } == true
            com.noor.app.ui.watch.studio.StudioCard(d, actions.studioConfig, studioOn, actions.glow, actions.openStudio)
        }

        when {
            state == null -> GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.watch_checking), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
            }
            state.wearUnavailable -> EmptyCard(stringResource(R.string.watch_no_wear_os_title), stringResource(R.string.watch_no_wear_os), actions.openGuide)
            else -> {
                // Connected watches first, then watches that have Noor but are out of reach right now.
                val known = state.connected + state.withNoor.filter { id -> state.connected.none { it.id == id } }.map { WatchNode(it, "") }
                if (known.isEmpty()) EmptyCard(stringResource(R.string.watch_none_title), stringResource(R.string.watch_none_body), actions.openGuide)
                for (node in known) WatchCard(node, node.id in state.connected.map { it.id }, node.id in state.withNoor, state.reports[node.id],
                    configAvailable, lastSent, installs[node.id], faces[node.id], syncing, syncFailed, fromPlay, f, actions, now)
            }
        }

        // 0.25: the one row here that leads somewhere stands in a group of its own (it sat among rows that say what
        // the watch does and go nowhere, the only one with a chevron).
        SettingsSection("") {
            SettingsItem(Icons.Rounded.NotificationsActive, stringResource(R.string.watch_feature_alerts), stringResource(R.string.watch_feature_alerts_desc), actions.openAdhan)
        }

        // What Minhad puts on the watch: a plain list of what is there; everything calculates on the watch itself.
        SettingsSection(stringResource(R.string.watch_on_watch_title), footer = stringResource(R.string.offline_watch_footer)) {
            SettingsItem(Icons.Rounded.Watch, stringResource(R.string.watch_feature_app), stringResource(R.string.watch_feature_app_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.ViewCarousel, stringResource(R.string.watch_feature_tile), stringResource(R.string.watch_feature_tile_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.Widgets, stringResource(R.string.watch_feature_complication), stringResource(R.string.watch_feature_complication_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.Palette, stringResource(R.string.watch_feature_editor), stringResource(R.string.watch_feature_editor_desc), null)
            SettingsDivider()
            SettingsItem(Icons.Rounded.CloudOff, stringResource(R.string.watch_feature_standalone), stringResource(R.string.watch_feature_standalone_desc), null)
        }
    }
}

@Composable
private fun WatchCard(
    node: WatchNode, connected: Boolean, hasNoor: Boolean, report: WatchReport?, configAvailable: Boolean, lastSent: Long?,
    install: Pending?, face: Pending?, syncing: Boolean, syncFailed: Boolean, fromPlay: Boolean, f: Formatter, actions: WatchActions, now: Long,
) {
    val c = NoorTheme.colors
    fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { f.timeText(it.hour * 60 + it.minute) }
    GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 24.dp) {
        // Header: watch and connection (0.25: the watch's sign as in every list, white on its graphite tile; gold is
        // for what can be acted on).
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.noor.app.ui.settings.IconTile(Icons.Rounded.Watch)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name.ifBlank { stringResource(R.string.watch_generic_name) }, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) c.success else c.textMuted))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (connected) R.string.watch_connected_short else R.string.watch_not_connected_short),
                        style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary)
                    report?.let {
                        Text("  ·  Wear OS " + WatchViewModel.wearOsName(it.sdkInt), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textMuted)
                    }
                }
            }
        }
        Hairline()

        // Step 1: Noor on the watch
        val installTimedOut = install != null && now - install.startedAtMs > WatchViewModel.PENDING_TIMEOUT_MS
        Step(
            n = f.number(1), done = hasNoor, title = stringResource(R.string.step_app_title),
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
        Hairline(inset = 56.dp)

        // Step 2: settings reached the watch
        val received = report?.configReceivedAtMs
        Step(
            n = f.number(2), done = hasNoor && received != null, title = stringResource(R.string.step_sync_title),
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
        Hairline(inset = 56.dp)

        // Step 3: the Noor watch face, with the editor (0.9.8).
        val faceTimedOut = face != null && now - face.startedAtMs > WatchViewModel.PENDING_TIMEOUT_MS
        val chosen = actions.faceChoice
        val onWatch = report?.faceVariant?.let(WatchFaces::parse)
        val showing = report?.faceActive == true && report.faceVariant == chosen.id
        val sending = face != null && face.reached && !faceTimedOut && report?.faceVariant != chosen.id
        // (0.25: the face a user designed in Minhad Studio is a Minhad face too. When the watch itself reports that it
        // is the one in use, the step is done and says so; until 0.24 it then still read as a step to take.)
        val studioShowing = report?.studioActive == true && !report.faceActive
        Step(
            n = f.number(3), done = report?.faceActive == true || studioShowing, title = stringResource(R.string.step_face_title),
            body = when {
                !hasNoor -> stringResource(R.string.step_after_install)
                report == null -> stringResource(R.string.step_face_open_first)
                studioShowing -> stringResource(R.string.face_on_watch, stringResource(R.string.studio_title))
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
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FaceEditor(chosen, actions.selectFace)
                Spacer(Modifier.height(6.dp))
                if (connected && !showing) PrimaryButton(stringResource(R.string.face_send)) { actions.sendFace(node.id, report) }
                Text(stringResource(R.string.face_changes_hint), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (hasNoor && connected) {
            Hairline()
            com.noor.app.ui.components.NoorButton(stringResource(R.string.watch_open_on_watch), { actions.open(node.id) }, Modifier.fillMaxWidth(),
                kind = com.noor.app.ui.components.ButtonKind.Plain)
        }
    }
}

@Composable
private fun Step(n: String, done: Boolean, title: String, body: String, working: Boolean, action: @Composable () -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done) c.success.copy(alpha = 0.18f) else c.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(16.dp))
                working -> CircularProgressIndicator(Modifier.size(14.dp), color = c.gold, strokeWidth = 2.dp)
                else -> Text(n, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary)
            Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    com.noor.app.ui.components.NoorButton(text, onClick, Modifier.padding(top = 8.dp))
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    com.noor.app.ui.components.PillButton(text, Modifier.padding(top = 2.dp), onClick)
}

@Composable
private fun EmptyCard(title: String, body: String, openGuide: () -> Unit = {}) {
    val c = NoorTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.noor.app.ui.settings.IconTile(Icons.Rounded.Watch)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        // 0.15: what to do next, as buttons, not only said: the watch's own app from the Play Store, and Noor's guide.
        Spacer(Modifier.height(16.dp))
        com.noor.app.ui.components.NoorButton(stringResource(R.string.watch_get_wearable), {
            val id = "com.samsung.android.app.watchmanager"
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }, Modifier.fillMaxWidth())
        com.noor.app.ui.components.NoorButton(stringResource(R.string.watch_how_to), openGuide, Modifier.fillMaxWidth(), kind = com.noor.app.ui.components.ButtonKind.Plain)
    }
}
