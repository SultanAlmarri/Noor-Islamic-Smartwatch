package com.noor.app.ui.adhan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSound
import com.noor.alerts.CustomSounds
import com.noor.alerts.SoundEntry
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.NoorSearchField
import com.noor.app.ui.components.ChoiceChips
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter

/** The regions the library can be browsed by, in the order shown. */
private val REGIONS = listOf(
    "haramain" to R.string.lib_region_haramain, "middle_east" to R.string.lib_region_middle_east, "africa" to R.string.lib_region_africa,
    "asia" to R.string.lib_region_asia, "europe" to R.string.lib_region_europe,
)

/**
 * The Adhan library (0.9.5): find recordings by place, country, mosque or reciter; filter by region, Fajr or favourites;
 * preview any of them at once (tapping another switches, so voices can be compared); mark favourites for "Random from
 * favourites"; open a recording for its notes, both versions and its full credit and licence, as the licences require.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SoundLibraryScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: AdhanViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val preview by vm.preview.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    val lib = vm.library
    var openId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val open = openId?.let { lib.byId(it) }

    // 0.18: the library is the owner's voices and five sound effects: a plain list of each, to hear and to see its
    // credit and licence (the search, filters and favourites of the 18-recording library had nothing left to sort).
    StopPreviewOnLeave(vm)
    NoorPage(stringResource(R.string.adhan_library), contentPadding, onBack = onBack, spacing = 16.dp) {
        SettingsSection(stringResource(R.string.alarm_sounds_adhan)) {
            lib.adhans.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                LibraryRow(e, f, ar, playing = preview?.id == e.id, onPlay = { vm.previewLibrary(e.id, short = !e.hasFullRecording) }, onOpen = { openId = e.id })
            }
        }
        if (lib.sounds.isNotEmpty()) SettingsSection(stringResource(R.string.alarm_sounds_effects)) {
            lib.sounds.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                LibraryRow(e, f, ar, playing = preview?.id == e.id, onPlay = { vm.previewLibrary(e.id, false) }, onOpen = { openId = e.id })
            }
        }
    }

    open?.let { e ->
        com.noor.app.ui.components.NoorSheet({ openId = null }, androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                AdhanCard(e, f, ar, preview, vm)
            }
        }
    }
}

/** One recording in the list: play at once, what and where, favourite, and more. */
@Composable
private fun LibraryRow(e: SoundEntry, f: Formatter, ar: Boolean, playing: Boolean, onPlay: () -> Unit, onOpen: () -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        PreviewButton(playing, stringResource(R.string.adhan_preview), onPlay)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.title(ar), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.fajr) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.adhan_badge_fajr), style = MaterialTheme.typography.labelSmall, color = c.goldText,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(c.gold.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            val len = if (e.hasFullRecording) f.duration(e.seconds) else stringResource(R.string.adhan_opening_only) + " · " + f.duration(e.shortSeconds ?: 0.0)
            Text(listOfNotNull(e.place(ar)?.let(f::digits), len).joinToString(" · "), style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted.copy(alpha = 0.7f), modifier = Modifier.padding(end = 8.dp).size(22.dp))
    }
}

@Composable
private fun AdhanCard(e: SoundEntry, f: Formatter, ar: Boolean, preview: PreviewKey?, vm: AdhanViewModel) {
    val c = NoorTheme.colors
    val context = LocalContext.current
    val label = stringResource(R.string.adhan_preview)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title(ar), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                e.place(ar)?.let { Text(f.digits(it), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.hasFullRecording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewButton(preview == PreviewKey(e.id, false), label) { vm.previewLibrary(e.id, false) }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adhan_full_label, f.duration(e.seconds)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (e.shortFile != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewButton(preview == PreviewKey(e.id, true), label) { vm.previewLibrary(e.id, true) }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adhan_short_label, f.duration(e.shortSeconds ?: 0.0)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }
        e.note(ar)?.let { Spacer(Modifier.height(12.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
        e.credit?.let { cr ->
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.lib_credit_title).uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = c.textMuted)
            Spacer(Modifier.height(4.dp))
            // The recordist's name stays as credited (the licence requires it); "Public domain" is translated.
            val licence = if (cr.licence == "Public domain") stringResource(R.string.adhan_public_domain) else cr.licence
            Text("${cr.author} · $licence", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                cr.sourceUrl?.let { url -> Link(stringResource(R.string.adhan_source)) { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                cr.licenceUrl?.let { url -> Link(stringResource(R.string.adhan_licence)) { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            }
            Text(stringResource(R.string.adhan_changes_generic) + if ("SA" in cr.licence) " " + stringResource(R.string.adhan_sa_note) else "",
                style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = NoorTheme.colors.goldText, textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp))
}

/** Imported sounds: import, preview, rename, replace, delete. */
@Composable
fun MySoundsScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: AdhanViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val custom by vm.custom.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var replaceTarget by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<CustomSound?>(null) }
    var deleting by remember { mutableStateOf<CustomSound?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSound(uri, replaceTarget)
        replaceTarget = null
    }

    StopPreviewOnLeave(vm)
    NoorPage(stringResource(R.string.adhan_my_sounds), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        if (custom.isEmpty()) {
            // Empty state: what this is for, and the one thing to do (second review: in the card the alarms' page has
            // when it has none; it was a grey disc on the bare page, a second style for the same moment).
            EmptyCard(Icons.Rounded.GraphicEq, stringResource(R.string.adhan_custom_empty_title), stringResource(R.string.adhan_custom_empty),
                stringResource(R.string.adhan_import), { replaceTarget = null; picker.launch(CustomSounds.PICKER_TYPES) })
            Footnote(stringResource(R.string.adhan_custom_privacy))
        }
        if (custom.isNotEmpty()) SettingsSection("", footer = stringResource(R.string.adhan_custom_privacy)) {
            custom.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SoundRow(cs.name, f.duration(cs.seconds) + " · " + stringResource(R.string.size_megabytes, f.digits("%.1f".format(java.util.Locale.ROOT, cs.bytes / 1_048_576.0))),
                            false, preview == PreviewKey(cs.id, false), stringResource(R.string.adhan_preview), null, { vm.previewCustom(cs.id) })
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.adhan_more), tint = c.textSecondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceRaised) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.adhan_rename), color = c.textPrimary) }, onClick = { menu = false; renaming = cs })
                            DropdownMenuItem(text = { Text(stringResource(R.string.adhan_replace), color = c.textPrimary) }, onClick = {
                                menu = false; replaceTarget = cs.id; picker.launch(CustomSounds.PICKER_TYPES)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.adhan_delete), color = c.danger) }, onClick = { menu = false; deleting = cs })
                        }
                    }
                }
            }
        }
        if (custom.isNotEmpty()) com.noor.app.ui.components.NoorButton(stringResource(R.string.adhan_import), { replaceTarget = null; picker.launch(CustomSounds.PICKER_TYPES) },
            Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
    }

    renaming?.let { cs ->
        var name by remember(cs.id) { mutableStateOf(cs.name) }
        AlertDialog(
            onDismissRequest = { renaming = null }, containerColor = c.surface, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
            title = { Text(stringResource(R.string.adhan_rename)) },
            text = { OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.adhan_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameSound(cs.id, name); renaming = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.adhan_save), color = c.goldText) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel), color = c.textSecondary) } },
        )
    }
    deleting?.let { cs ->
        val users = vm.usersOf(cs.id)
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = c.surface, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
            title = { Text(stringResource(R.string.adhan_delete_confirm, cs.name)) },
            text = { if (users.isNotEmpty()) Text(stringResource(R.string.adhan_delete_used, users.joinToString("، ".takeIf { ar } ?: ", ") { f.prayerName(it) })) },
            confirmButton = { TextButton(onClick = { vm.deleteSound(cs.id); deleting = null }) { Text(stringResource(R.string.adhan_delete), color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel), color = c.textSecondary) } },
        )
    }
}
