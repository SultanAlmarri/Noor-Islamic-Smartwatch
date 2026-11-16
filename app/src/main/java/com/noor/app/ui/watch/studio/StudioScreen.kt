package com.noor.app.ui.watch.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.ChoiceChips
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.NoorSheet
import com.noor.app.ui.components.Page
import com.noor.app.ui.components.bleed
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.Ambient
import com.noor.prayer.face.Background
import com.noor.prayer.face.ColorRole
import com.noor.prayer.face.Edge
import com.noor.prayer.face.Element
import com.noor.prayer.face.ElementSize
import com.noor.prayer.face.FaceClock
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceDigits
import com.noor.prayer.face.FaceFont
import com.noor.prayer.face.FaceLanguage
import com.noor.prayer.face.FaceTemplates
import com.noor.prayer.face.Hands
import com.noor.prayer.face.Marks
import com.noor.prayer.face.Placement
import com.noor.prayer.face.Slot
import com.noor.prayer.face.TapAction
import java.time.ZoneId

/** What the editor can do; the screen itself holds no state, so every state renders in tests. */
class StudioActions(
    val edit: ((FaceDesign) -> FaceDesign) -> Unit = {},
    val undo: () -> Unit = {},
    val redo: () -> Unit = {},
    val select: (Slot?) -> Unit = {},
    val panel: (StudioPanel) -> Unit = {},
    val ambient: (Boolean) -> Unit = {},
    val use: (FaceDesign) -> Unit = {},
    val save: (String) -> Unit = {},
    val delete: (String) -> Unit = {},
    val send: () -> Unit = {},
)

@Composable
fun StudioScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: StudioViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    StudioContent(
        contentPadding, s, config, studioStatus(s, watches),
        StudioActions(vm::edit, vm::undo, vm::redo, vm::select, vm::panel, vm::ambient, vm::use, vm::save, vm::delete, { vm.send() }),
        onBack,
    )
}

/**
 * Minhad Studio (0.18), the watch-face editor. The face is the editor: a large live preview, drawn by the watch's own
 * renderer, where a tap on a place opens what can go there and a long press lifts an element to move it. Below, the
 * design's look in a few panels, and at the bottom one button that puts it on the watch.
 *
 * Why places rather than free dragging (the owner left the choice open): on a 1.2-inch round screen, free positions
 * make it easy to build a face that is crowded, clipped at the edge or unreadable, and hard to tap. Seven places,
 * tuned to the round screen, keep every design balanced and every element legible and touchable on the wrist, while
 * still letting anything go anywhere; dragging remains, as "move this there", with a tick at each place.
 */
@Composable
fun StudioContent(
    contentPadding: PaddingValues, s: StudioState, config: WatchConfig?, status: StudioStatus, a: StudioActions, onBack: () -> Unit,
) {
    val c = NoorTheme.colors
    val arabic = isArabicUi()
    val showAmbient = s.ambient || s.panel == StudioPanel.AMBIENT
    var picking by remember { mutableStateOf<ColourTarget?>(null) }
    NoorPage(stringResource(R.string.studio_title), contentPadding, onBack = onBack, spacing = 16.dp, floating = { SendBar(s, status, a) }) {
        // Undo, redo, and the always-on preview.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.studio_undo), s.undo.isNotEmpty(), a.undo)
            ToolButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.studio_redo), s.redo.isNotEmpty(), a.redo)
            Spacer(Modifier.weight(1f))
            ModeSwitch(showAmbient, a.ambient)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StudioPreview(
                s.design, config, showAmbient, s.selected, c.gold, onTap = a.select, onMove = { from, to -> a.edit { it.move(from, to) }; a.select(to) },
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.86f), deviceArabic = arabic,
                description = stringResource(R.string.studio_preview_desc),
            )
        }
        Text(stringResource(R.string.studio_hint), style = FootnoteStyle, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // The places as chips too: for TalkBack, and for the small places on a small phone.
        ChoiceChips(Slot.entries.map { it to slotName(it) }, s.selected, { a.select(it) })

        s.selected?.let { slot -> Inspector(slot, s.design, a) { picking = it } }

        ChoiceChips(StudioPanel.entries.map { it to panelName(it) }, s.panel, a.panel)
        when (s.panel) {
            StudioPanel.DESIGNS -> DesignsPanel(s, config, a)
            StudioPanel.STYLE -> StylePanel(s.design, a) { picking = it }
            StudioPanel.DIAL -> DialPanel(s.design, a)
            StudioPanel.AMBIENT -> AmbientPanel(s.design, a)
            StudioPanel.OPTIONS -> OptionsPanel(s.design, a)
        }
        Spacer(Modifier.height(120.dp)) // room for the button held at the bottom
    }
    picking?.let { target ->
        ColourSheet(initial = target.current, onDismiss = { picking = null }) { argb -> a.edit { target.apply(it, argb) }; picking = null }
    }
}

/** What a colour picked in the sheet goes to. */
class ColourTarget(val current: Int, val apply: (FaceDesign, Int) -> FaceDesign)

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }.graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.goldText, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun ModeSwitch(ambient: Boolean, onChange: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.glassRaised).padding(3.dp)) {
        for ((on, label) in listOf(false to stringResource(R.string.studio_active), true to stringResource(R.string.studio_aod))) {
            val sel = on == ambient
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.gold else Color.Transparent)
                    .clickable(role = Role.RadioButton) { if (!sel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(on) }
                    .semantics { selected = sel }.heightIn(min = 40.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) { Icon(Icons.Rounded.DarkMode, null, tint = if (sel) c.onGold else c.textSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) c.onGold else c.textSecondary)
            }
        }
    }
}

// ---- the selected place ----

@Composable
private fun Inspector(slot: Slot, d: FaceDesign, a: StudioActions, pick: (ColourTarget) -> Unit) {
    val c = NoorTheme.colors
    val p = d.at(slot)
    val haptics = LocalHapticFeedback.current
    GlassCard(Modifier.fillMaxWidth(), padding = 16.dp, highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slotName(slot), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            if (p != null) TextButton(onClick = { a.edit { it.with(slot, null) } }) { Text(stringResource(R.string.studio_remove), color = c.danger) }
            ToolButton(Icons.Rounded.Close, stringResource(R.string.studio_close), true) { a.select(null) }
        }
        Label(stringResource(R.string.studio_what_here))
        Row(Modifier.bleed(16.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in Element.offered) {
                val sel = p?.element == e
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) c.gold.copy(alpha = 0.22f) else c.glass)
                        .border(if (sel) 1.5.dp else 0.dp, if (sel) c.gold else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            a.edit { it.with(slot, Placement(e, size = p?.size ?: ElementSize.M)) }
                        }
                        .semantics { selected = sel }.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(elementIcon(e), null, tint = if (sel) c.goldText else c.textSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(elementName(e), style = MaterialTheme.typography.labelSmall, color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (p != null) {
            Label(stringResource(R.string.studio_size))
            ChoiceChips(ElementSize.entries.map { it to sizeName(it) }, p.size, { z -> a.edit { it.with(slot, p.copy(size = z)) } })
            Label(stringResource(R.string.studio_colour))
            ColourRow(d, p, { role, argb -> a.edit { it.with(slot, p.copy(color = role, custom = argb)) } }) {
                pick(ColourTarget(p.custom ?: d.theme.accent) { dd, argb -> dd.with(slot, p.copy(color = ColorRole.CUSTOM, custom = argb)) })
            }
            Label(stringResource(R.string.studio_touch))
            ChoiceChips(TapAction.entries.map { it to tapName(it) }, p.tapAction, { t -> a.edit { it.with(slot, p.copy(tap = t)) } })
            if (p.element == Element.LABEL) {
                Label(stringResource(R.string.studio_label_text))
                LabelField(p.text) { txt -> a.edit { it.with(slot, p.copy(text = txt.take(FaceDesign.MAX_TEXT))) } }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = NoorTheme.colors.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun LabelField(value: String, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    BasicTextField(
        value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary), cursorBrush = SolidColor(c.gold),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text(stringResource(R.string.studio_label_hint), color = c.textMuted)
                inner()
            }
        },
    )
}

/** The theme's three colours, a few more, and one of one's own. */
@Composable
private fun ColourRow(d: FaceDesign, p: Placement, onPick: (ColorRole, Int?) -> Unit, onCustom: () -> Unit) {
    Row(Modifier.bleed(16.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Swatch(Color(d.theme.accent), stringResource(R.string.studio_colour_accent), p.color == ColorRole.ACCENT) { onPick(ColorRole.ACCENT, null) }
        Swatch(Color(d.theme.text), stringResource(R.string.studio_colour_text), p.color == ColorRole.TEXT) { onPick(ColorRole.TEXT, null) }
        Swatch(Color(d.theme.soft), stringResource(R.string.studio_colour_soft), p.color == ColorRole.SOFT) { onPick(ColorRole.SOFT, null) }
        val other = stringResource(R.string.studio_colour)
        FaceTemplates.swatches.forEachIndexed { i, argb ->
            Swatch(Color(argb), "$other ${i + 1}", p.color == ColorRole.CUSTOM && p.custom == argb) { onPick(ColorRole.CUSTOM, argb) }
        }
        RainbowSwatch(stringResource(R.string.studio_colour_custom), onCustom)
    }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(if (selected) 2.5.dp else 1.dp, if (selected) c.textPrimary else c.glassEdge, CircleShape)
            .padding(4.dp).clip(CircleShape).background(color)
            .clickable(role = Role.RadioButton) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { contentDescription = label; this.selected = selected },
    )
}

@Composable
private fun RainbowSwatch(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
    )
}


// ---- panels ----

@Composable
private fun DesignsPanel(s: StudioState, config: WatchConfig?, a: StudioActions) {
    val c = NoorTheme.colors
    var naming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<FaceDesign?>(null) }
    Label(stringResource(R.string.studio_templates))
    DesignRow(FaceTemplates.all, config, s.design.id, { templateName(it.id) }, a.use, null)
    Label(stringResource(R.string.studio_mine))
    if (s.saved.isEmpty()) Text(stringResource(R.string.studio_mine_empty), style = FootnoteStyle, color = c.textMuted)
    else DesignRow(s.saved, config, s.design.id, { it.name }, a.use) { confirmDelete = it }
    NoorButton(stringResource(R.string.studio_save), { naming = true }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Tonal)

    if (naming) {
        val default = stringResource(R.string.studio_saved_default, s.saved.size + 1)
        var name by remember { mutableStateOf(s.design.name.ifBlank { default }) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.studio_save_title)) },
            text = { LabelField(name) { name = it.take(40) } },
            confirmButton = { TextButton({ a.save(name.ifBlank { default }); naming = false }) { Text(stringResource(R.string.studio_save)) } },
            dismissButton = { TextButton({ naming = false }) { Text(stringResource(R.string.studio_cancel)) } },
            containerColor = c.glassRaised.copy(alpha = 1f),
        )
    }
    confirmDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.studio_delete_named, d.name)) },
            confirmButton = { TextButton({ a.delete(d.id); confirmDelete = null }) { Text(stringResource(R.string.studio_delete), color = c.danger) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(R.string.studio_cancel)) } },
        )
    }
}

/** A row of designs as small faces (still, at 10:09, so a row of them costs nothing to draw). */
@Composable
private fun DesignRow(list: List<FaceDesign>, config: WatchConfig?, current: String, name: @Composable (FaceDesign) -> String, onUse: (FaceDesign) -> Unit, onDelete: ((FaceDesign) -> Unit)?) {
    val c = NoorTheme.colors
    val arabic = isArabicUi()
    val zone = config?.zone ?: ZoneId.systemDefault()
    val moment = remember(zone) { fixedMoment(zone) }
    Row(Modifier.bleed().fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Page.Margin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (d in list) {
            val sel = d.id == current
            val label = name(d)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                Box {
                    FaceCanvas(d, config, false, Modifier.size(88.dp).border(if (sel) 2.5.dp else 0.dp, if (sel) c.gold else Color.Transparent, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = label) { onUse(d) }, clock = moment, deviceArabic = arabic, description = label)
                    if (onDelete != null) {
                        val del = stringResource(R.string.studio_delete_named, label)
                        Box(Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(c.glassRaised)
                            .clickable(role = Role.Button, onClickLabel = del) { onDelete(d) }.semantics { contentDescription = del }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) c.goldText else c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StylePanel(d: FaceDesign, a: StudioActions, pick: (ColourTarget) -> Unit) {
    val c = NoorTheme.colors
    val t = d.theme
    Label(stringResource(R.string.studio_palette))
    Row(Modifier.bleed().fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Page.Margin), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (p in FaceTemplates.Palette.entries) {
            val sel = FaceTemplates.Palette.of(t) == p
            val label = paletteName(p)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).border(if (sel) 2.5.dp else 1.dp, if (sel) c.textPrimary else c.glassEdge, CircleShape)
                        .background(Brush.linearGradient(listOf(Color(p.bg1), Color(p.bg2))))
                        .clickable(role = Role.RadioButton) { a.edit { it.copy(theme = p.apply(it.theme)) } }.semantics { contentDescription = label; selected = sel },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(p.accent))) }
                Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    SettingsSection(stringResource(R.string.studio_colours)) {
        ColourItem(stringResource(R.string.studio_colour_accent), t.accent) { pick(ColourTarget(t.accent) { dd, v -> dd.copy(theme = dd.theme.copy(accent = v)) }) }
        SettingsDivider()
        ColourItem(stringResource(R.string.studio_colour_text), t.text) { pick(ColourTarget(t.text) { dd, v -> dd.copy(theme = dd.theme.copy(text = v)) }) }
        SettingsDivider()
        ColourItem(stringResource(R.string.studio_colour_soft), t.soft) { pick(ColourTarget(t.soft) { dd, v -> dd.copy(theme = dd.theme.copy(soft = v)) }) }
    }
    Label(stringResource(R.string.studio_background))
    ChoiceChips(Background.entries.map { it to backgroundName(it) }, t.background, { b -> a.edit { it.copy(theme = it.theme.copy(background = b)) } })
    if (t.background == Background.SOLID || t.background == Background.GRADIENT) {
        SettingsSection("") {
            ColourItem(stringResource(R.string.studio_bg_colour), t.bg1) { pick(ColourTarget(t.bg1) { dd, v -> dd.copy(theme = dd.theme.copy(bg1 = v)) }) }
        }
    }
    Label(stringResource(R.string.studio_font))
    ChoiceChips(FaceFont.entries.map { it to fontName(it) }, t.font, { f -> a.edit { it.copy(theme = it.theme.copy(font = f)) } })
}

@Composable
private fun ColourItem(title: String, argb: Int, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(argb)).border(1.dp, c.glassEdge, CircleShape))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DialPanel(d: FaceDesign, a: StudioActions) {
    val c = NoorTheme.colors
    Label(stringResource(R.string.studio_hands))
    ChoiceChips(Hands.entries.map { it to handsName(it) }, d.hands, { h -> a.edit { it.copy(hands = h) } })
    Label(stringResource(R.string.studio_marks))
    ChoiceChips(Marks.entries.map { it to marksName(it) }, d.marks, { m -> a.edit { it.copy(marks = m) } })
    Label(stringResource(R.string.studio_edge))
    ChoiceChips(Edge.entries.map { it to edgeName(it) }, d.edge, { e -> a.edit { it.copy(edge = e) } })
    Text(stringResource(R.string.studio_edge_desc), style = FootnoteStyle, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(8.dp))
    SettingsSection("") {
        SettingsSwitch(Icons.Rounded.Timer, stringResource(R.string.studio_seconds), stringResource(R.string.studio_seconds_desc), d.seconds) { on -> a.edit { it.copy(seconds = on) } }
    }
}

@Composable
private fun AmbientPanel(d: FaceDesign, a: StudioActions) {
    Label(stringResource(R.string.studio_aod_style))
    ChoiceChips(Ambient.entries.map { it to ambientName(it) }, d.ambient, { m -> a.edit { it.copy(ambient = m) } })
    if (d.ambient == Ambient.CUSTOM) {
        SettingsSection(stringResource(R.string.studio_aod_keep), footer = stringResource(R.string.studio_aod_keep_desc)) {
            val used = Slot.entries.filter { d.at(it) != null && d.at(it)?.element != Element.TIME }
            used.forEachIndexed { i, slot ->
                if (i > 0) SettingsDivider()
                SettingsSwitch(elementIcon(d.at(slot)!!.element), elementName(d.at(slot)!!.element), slotName(slot), slot in d.ambientSlots) { on ->
                    a.edit { it.copy(ambientSlots = if (on) it.ambientSlots + slot else it.ambientSlots - slot) }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    SettingsSection("") {
        SettingsSwitch(Icons.Rounded.Contrast, stringResource(R.string.studio_shift), stringResource(R.string.studio_shift_desc), d.pixelShift) { on -> a.edit { it.copy(pixelShift = on) } }
        SettingsDivider()
        SettingsSwitch(Icons.Rounded.TextFields, stringResource(R.string.studio_outline), stringResource(R.string.studio_outline_desc), d.ambientOutline) { on -> a.edit { it.copy(ambientOutline = on) } }
    }
}

@Composable
private fun OptionsPanel(d: FaceDesign, a: StudioActions) {
    val follow = stringResource(R.string.studio_follow_app)
    Label(stringResource(R.string.studio_language))
    ChoiceChips(listOf(FaceLanguage.FOLLOW to follow, FaceLanguage.EN to stringResource(R.string.studio_lang_en), FaceLanguage.AR to stringResource(R.string.studio_lang_ar)),
        d.language, { l -> a.edit { it.copy(language = l) } })
    Label(stringResource(R.string.studio_digits))
    ChoiceChips(listOf(FaceDigits.FOLLOW to follow, FaceDigits.WESTERN to stringResource(R.string.studio_digits_western), FaceDigits.ARABIC to stringResource(R.string.studio_digits_arabic)),
        d.digits, { v -> a.edit { it.copy(digits = v) } })
    Label(stringResource(R.string.studio_clock))
    ChoiceChips(listOf(FaceClock.FOLLOW to follow, FaceClock.H12 to stringResource(R.string.studio_clock_12), FaceClock.H24 to stringResource(R.string.studio_clock_24)),
        d.clock, { v -> a.edit { it.copy(clock = v) } })
}

// ---- sending ----

@Composable
private fun BoxScope.SendBar(s: StudioState, status: StudioStatus, a: StudioActions) {
    val c = NoorTheme.colors
    val sky = com.noor.app.ui.theme.LocalBackdrop.current.at(1f)
    val changed = s.sentDesign != null && s.sentDesign != s.design
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(sky.copy(alpha = 0f), sky.copy(alpha = 0.92f), sky)))
            .navigationBarsPadding().padding(start = Page.Margin, end = Page.Margin, top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val line = when {
            s.sending -> null
            changed -> stringResource(R.string.studio_status_changed)
            else -> when (status) {
                StudioStatus.NONE -> if (s.sentAt != null) stringResource(R.string.studio_status_none) else null
                StudioStatus.SENT -> stringResource(R.string.studio_status_sent)
                StudioStatus.SHOWING -> stringResource(R.string.studio_status_showing)
                StudioStatus.PICK -> stringResource(R.string.studio_status_pick)
                StudioStatus.UNSUPPORTED -> stringResource(R.string.studio_status_unsupported)
                StudioStatus.UPDATE -> stringResource(R.string.studio_status_update)
                StudioStatus.FAILED -> stringResource(R.string.studio_status_failed)
            }
        }
        if (line != null) Text(line, style = FootnoteStyle, color = if (status == StudioStatus.SHOWING && !changed) c.success else c.textSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp).widthIn(max = Page.MaxWidth))
        NoorButton(stringResource(if (s.sending) R.string.studio_sending else R.string.studio_send), a.send,
            Modifier.fillMaxWidth().widthIn(max = Page.MaxWidth), enabled = !s.sending && status != StudioStatus.UNSUPPORTED)
    }
}

// ---- colour sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourSheet(initial: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val c = NoorTheme.colors
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1].coerceIn(0f, 1f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.2f, 1f)) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    NoorSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.studio_pick_colour), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(argb)).border(1.dp, c.glassEdge, CircleShape))
            }
            Label(stringResource(R.string.studio_hue))
            Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 1f) }))
            }
            Slider(hue, { hue = it }, valueRange = 0f..359f, colors = SliderDefaults.colors(thumbColor = Color(argb), activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
            Label(stringResource(R.string.studio_colour))
            Slider(sat, { sat = it }, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = Color(argb), activeTrackColor = c.gold))
            Label(stringResource(R.string.studio_lightness))
            Slider(value, { value = it }, valueRange = 0.2f..1f, colors = SliderDefaults.colors(thumbColor = Color(argb), activeTrackColor = c.gold))
            NoorButton(stringResource(R.string.studio_done), { onDone(argb or 0xFF000000.toInt()) }, Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

// ---- names ----

@Composable fun slotName(s: Slot) = stringResource(when (s) {
    Slot.UPPER -> R.string.studio_slot_upper; Slot.TOP -> R.string.studio_slot_top; Slot.CENTER -> R.string.studio_slot_center
    Slot.BOTTOM -> R.string.studio_slot_bottom; Slot.LOWER -> R.string.studio_slot_lower; Slot.LEFT -> R.string.studio_slot_left; Slot.RIGHT -> R.string.studio_slot_right
})

@Composable fun elementName(e: Element) = stringResource(when (e) {
    Element.NONE, Element.LABEL -> R.string.studio_el_label
    Element.TIME -> R.string.studio_el_time; Element.DATE -> R.string.studio_el_date; Element.WEEKDAY -> R.string.studio_el_weekday
    Element.HIJRI -> R.string.studio_el_hijri; Element.NEXT_PRAYER -> R.string.studio_el_next; Element.COUNTDOWN -> R.string.studio_el_countdown
    Element.PRAYER_TIMES -> R.string.studio_el_times; Element.PRAYER_GAUGE -> R.string.studio_el_gauge; Element.SUN_ARC -> R.string.studio_el_sun
    Element.QIBLA -> R.string.studio_el_qibla; Element.BATTERY -> R.string.studio_el_battery
})

fun elementIcon(e: Element): ImageVector = when (e) {
    Element.NONE, Element.LABEL -> Icons.Rounded.TextFields
    Element.TIME -> Icons.Rounded.Schedule; Element.DATE -> Icons.Rounded.CalendarToday; Element.WEEKDAY -> Icons.Rounded.Today
    Element.HIJRI -> Icons.Rounded.NightsStay; Element.NEXT_PRAYER -> Icons.Rounded.Mosque; Element.COUNTDOWN -> Icons.Rounded.HourglassBottom
    Element.PRAYER_TIMES -> Icons.AutoMirrored.Rounded.FormatListBulleted; Element.PRAYER_GAUGE -> Icons.Rounded.DonutLarge
    Element.SUN_ARC -> Icons.Rounded.WbTwilight; Element.QIBLA -> Icons.Rounded.Explore; Element.BATTERY -> Icons.Rounded.BatteryFull
}

@Composable fun templateName(id: String) = when (id) {
    FaceTemplates.HORIZON.id -> stringResource(R.string.studio_tpl_horizon); FaceTemplates.DIAL.id -> stringResource(R.string.studio_tpl_dial)
    FaceTemplates.MINIMAL.id -> stringResource(R.string.studio_tpl_minimal); FaceTemplates.DAY.id -> stringResource(R.string.studio_tpl_day)
    FaceTemplates.TIMETABLE.id -> stringResource(R.string.studio_tpl_timetable); FaceTemplates.KUFI.id -> stringResource(R.string.studio_tpl_kufi)
    FaceTemplates.SUN.id -> stringResource(R.string.studio_tpl_sun)
    else -> stringResource(R.string.studio_title)
}

/** A design's name: the user's, or its template's. */
@Composable fun designName(d: FaceDesign) = d.name.ifBlank { templateName(d.id) }

@Composable private fun panelName(p: StudioPanel) = stringResource(when (p) {
    StudioPanel.DESIGNS -> R.string.studio_tab_designs; StudioPanel.STYLE -> R.string.studio_tab_style; StudioPanel.DIAL -> R.string.studio_tab_dial
    StudioPanel.AMBIENT -> R.string.studio_tab_aod; StudioPanel.OPTIONS -> R.string.studio_tab_options
})
@Composable private fun sizeName(z: ElementSize) = stringResource(when (z) { ElementSize.S -> R.string.studio_size_s; ElementSize.M -> R.string.studio_size_m; ElementSize.L -> R.string.studio_size_l })
@Composable private fun tapName(t: TapAction) = stringResource(when (t) {
    TapAction.NONE -> R.string.studio_tap_none; TapAction.APP -> R.string.studio_tap_app; TapAction.QIBLA -> R.string.studio_tap_qibla
    TapAction.TASBIH -> R.string.studio_tap_tasbih; TapAction.CALENDAR -> R.string.studio_tap_calendar
})
@Composable private fun paletteName(p: FaceTemplates.Palette) = stringResource(when (p) {
    FaceTemplates.Palette.GOLD -> R.string.studio_pal_gold; FaceTemplates.Palette.EMERALD -> R.string.studio_pal_emerald
    FaceTemplates.Palette.TURQUOISE -> R.string.studio_pal_turquoise; FaceTemplates.Palette.ROSE -> R.string.studio_pal_rose
    FaceTemplates.Palette.SILVER -> R.string.studio_pal_silver; FaceTemplates.Palette.SAND -> R.string.studio_pal_sand; FaceTemplates.Palette.DUSK -> R.string.studio_pal_dusk
})
@Composable private fun backgroundName(b: Background) = stringResource(when (b) {
    Background.BLACK -> R.string.studio_bg_black; Background.SOLID -> R.string.studio_bg_solid; Background.GRADIENT -> R.string.studio_bg_gradient; Background.DUNES -> R.string.studio_bg_dunes
})
@Composable private fun fontName(f: FaceFont) = stringResource(when (f) {
    FaceFont.LIGHT -> R.string.studio_font_light; FaceFont.REGULAR -> R.string.studio_font_regular; FaceFont.MEDIUM -> R.string.studio_font_medium; FaceFont.KUFI -> R.string.studio_font_kufi
})
@Composable private fun handsName(h: Hands) = stringResource(when (h) {
    Hands.NONE -> R.string.studio_hands_none; Hands.CLASSIC -> R.string.studio_hands_classic; Hands.SLIM -> R.string.studio_hands_slim; Hands.BOLD -> R.string.studio_hands_bold
})
@Composable private fun marksName(m: Marks) = stringResource(when (m) {
    Marks.NONE -> R.string.studio_marks_none; Marks.DOTS -> R.string.studio_marks_dots; Marks.MINUTES -> R.string.studio_marks_minutes; Marks.NUMERALS -> R.string.studio_marks_numerals
})
@Composable private fun edgeName(e: Edge) = stringResource(when (e) { Edge.NONE -> R.string.studio_edge_none; Edge.PRAYER_RING -> R.string.studio_edge_ring; Edge.DAY_DIAL -> R.string.studio_edge_day })
@Composable private fun ambientName(m: Ambient) = stringResource(when (m) {
    Ambient.DIMMED -> R.string.studio_aod_dimmed; Ambient.MINIMAL -> R.string.studio_aod_minimal; Ambient.TIME_ONLY -> R.string.studio_aod_time; Ambient.CUSTOM -> R.string.studio_aod_custom
})
