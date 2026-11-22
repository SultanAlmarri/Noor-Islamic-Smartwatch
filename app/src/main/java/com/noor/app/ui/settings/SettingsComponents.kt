package com.noor.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorSheet
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import kotlinx.coroutines.launch

/** The small print of a grouped list: what stands under a row's title and under a group (iOS's "footnote"). */
val FootnoteStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/**
 * A titled group of rows (0.14: an inset grouped list). The title is quiet; a [footer] under the group explains what
 * its rows do, in a sentence. Gold is kept for what the user can act on or has chosen.
 */
@Composable
fun SettingsSection(title: String, footer: String? = null, content: @Composable () -> Unit) {
    val c = NoorTheme.colors
    Column {
        if (title.isNotEmpty()) SectionHeader(title)
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 24.dp) { content() }
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, letterSpacing = 0.9.sp), color = NoorTheme.colors.textMuted,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, style = FootnoteStyle, color = NoorTheme.colors.textMuted, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
}

/**
 * The colours of the signs in the lists (0.25). As in the Settings of an iPhone, each kind of thing has a colour of
 * its own, so a row is found by its colour before its words are read: where one is in blue, the prayer's times in
 * Minhad's gold, what makes itself heard in red and pink, what is seen in indigo and teal, the calendar in red, the
 * watch in graphite, what is checked in green. The sign itself is white on its tile.
 */
object Tints {
    val Gold = Color(0xFFCB9A3C)
    val Blue = Color(0xFF1E88F5)
    val Green = Color(0xFF2FB45A)
    val Orange = Color(0xFFF58B1F)
    val Red = Color(0xFFF0463C)
    val Pink = Color(0xFFEE3E6A)
    val Purple = Color(0xFFA557DA)
    val Indigo = Color(0xFF5E5CE0)
    val Teal = Color(0xFF28A9C4)
    val Mint = Color(0xFF1FB8A6)
    val Brown = Color(0xFFA2835C)
    val Gray = Color(0xFF8A8F9A)
    val Graphite = Color(0xFF4A4F5C)

    private val byIcon: Map<String, Color> = mapOf(
        // where
        "LocationOn" to Blue, "MyLocation" to Blue, "GpsFixed" to Blue, "Update" to Green, "Map" to Green, "Public" to Blue, "Place" to Red,
        // the times
        "Schedule" to Gold, "Explore" to Teal, "Tune" to Gray, "Verified" to Green,
        // what is heard and shown as a notice
        "NotificationsActive" to Red, "Notifications" to Red, "Timer" to Orange, "History" to Gray, "VolumeUp" to Pink, "Vibration" to Pink,
        "MusicNote" to Pink, "LibraryMusic" to Pink, "AudioFile" to Purple, "RecordVoiceOver" to Orange, "GraphicEq" to Purple, "DoNotDisturbOn" to Indigo,
        "Snooze" to Orange, "PlayCircle" to Green, "Label" to Orange, "Add" to Green, "BatteryAlert" to Green, "BatterySaver" to Green,
        // how it looks
        "Brightness6" to Indigo, "Brightness" to Indigo, "Contrast" to Graphite, "Landscape" to Teal, "Park" to Green, "AutoAwesome" to Purple, "Palette" to Pink,
        "Widgets" to Purple, "ViewCarousel" to Purple, "TextFields" to Blue, "CloudOff" to Gray,
        // language and calendar
        "Language" to Blue, "Numbers" to Gray, "AccessTime" to Orange, "CalendarMonth" to Red,
        // the rest
        "Watch" to Graphite, "Info" to Gray,
    )

    /** The colour for a sign, by what it shows; Minhad's gold for a sign with no colour of its own. */
    fun of(icon: ImageVector): Color = byIcon[icon.name.substringAfterLast('.')] ?: Gold
}

/**
 * The sign at the start of a row (0.25): a small tile filled with the sign's colour, lit from above, and the sign in
 * white on it, as in an iPhone's Settings. [tint] is given only where the colour says something (a warning, a danger);
 * otherwise the sign has the colour of its kind ([Tints.of]).
 */
@Composable
fun IconTile(icon: ImageVector, tint: Color = Color.Unspecified) {
    val base = if (tint == Color.Unspecified) Tints.of(icon) else tint
    val brush = remember(base) { Brush.verticalGradient(listOf(lerp(base, Color.White, 0.20f), base, lerp(base, Color.Black, 0.10f))) }
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/**
 * A row of a grouped list. [value] is a line under the title (an explanation, or a long choice); [detail] is the
 * current choice in a word or two, shown at the end of the row before the chevron, as on iOS ("Numerals   Western ›").
 * With large text the detail moves under the title, so neither is squeezed.
 */
@Composable
fun SettingsItem(
    icon: ImageVector, title: String, value: String?, onClick: (() -> Unit)?,
    detail: String? = null, tint: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = NoorTheme.colors
    val roomy = LocalDensity.current.fontScale <= 1.15f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = com.noor.app.ui.components.withTick(onClick)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        val head: @Composable () -> Unit = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                val under = listOfNotNull(value, detail.takeIf { !roomy }).joinToString("\n").ifEmpty { null }
                if (under != null) Text(under, style = FootnoteStyle, color = c.textSecondary)
            }
        }
        if (detail != null && roomy) {
            // (0.21) The choice takes the room the title leaves it (the title keeps at least half the row): at a fixed
            // 150 dp, "Phone's alarm sound" was cut beside the short "Sound" on a 360 dp phone.
            androidx.compose.ui.layout.Layout(
                content = {
                    head()
                    Text(detail, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                },
                modifier = Modifier.weight(1f),
            ) { parts, cs ->
                val gap = 12.dp.roundToPx()
                val room = cs.maxWidth
                val titleWants = parts[0].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
                val detailWants = parts[1].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
                val detailW = minOf(detailWants, (room - gap - minOf(titleWants, room / 2)).coerceAtLeast(0))
                val t = parts[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = (room - gap - detailW).coerceAtLeast(0)))
                val d = parts[1].measure(androidx.compose.ui.unit.Constraints(maxWidth = detailW))
                val h = maxOf(t.height, d.height, cs.minHeight)
                layout(room, h) {
                    t.placeRelative(0, (h - t.height) / 2)
                    d.placeRelative(room - d.width, (h - d.height) / 2)
                }
            }
        } else Box(Modifier.weight(1f)) { head() }
        when {
            trailing != null -> { Spacer(Modifier.width(8.dp)); trailing() }
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp).size(22.dp))
        }
    }
}

/**
 * A row that turns something on or off. The whole row is the switch (one thing for TalkBack: "title, switch, on"),
 * and a touch anywhere on it answers with a tick.
 */
@Composable
fun SettingsSwitch(icon: ImageVector, title: String, value: String?, checked: Boolean, tint: Color = Color.Unspecified, onChange: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(it) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (value != null) Text(value, style = FootnoteStyle, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        NoorSwitch(checked, null)
    }
}

/** Divider aligned with the row text (after the icon tile), as in a grouped list. */
@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = NoorTheme.colors.separator)

/** Noor's switch: gold when on, a quiet grey track when off, the same white thumb either way. */
@Composable
fun NoorSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    val c = NoorTheme.colors
    val off = c.textMuted.copy(alpha = if (c.isDark) 0.38f else 0.30f)
    Switch(
        // (0.18: a tick when it is flipped by itself; rows that hold a switch tick for it.)
        checked = checked, onCheckedChange = onChange?.let { com.noor.app.ui.components.withTick(it) },
        // (With content, Material keeps the thumb one size on and off, as on iOS.)
        thumbContent = { Spacer(Modifier.size(SwitchDefaults.IconSize)) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.gold, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = off, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * A single choice from a short list (0.14: a sheet from the bottom, where the thumb is, instead of a dialog in the
 * middle of the screen). The chosen row carries a gold check; a label's second line, if it has one, is its small
 * print; picking answers with a tick and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit, note: String? = null) {
    val c = NoorTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    NoorSheet(onDismiss, sheet) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = if (note == null) 10.dp else 4.dp).semantics { heading() })
        if (note != null) Text(note, style = FootnoteStyle, color = c.textSecondary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        LazyColumn(Modifier.padding(horizontal = 12.dp).weight(1f, fill = false)) {
            items(options) { (value, label) ->
                val on = value == selected
                val lines = label.split('\n', limit = 2)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.background(c.gold.copy(alpha = if (c.isDark) 0.12f else 0.10f)) else Modifier)
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                            scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                        }
                        .semantics { this.selected = on }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(lines[0], style = MaterialTheme.typography.bodyLarge, color = if (on) c.goldText else c.textPrimary)
                        if (lines.size > 1) Text(lines[1], style = FootnoteStyle, color = c.textSecondary)
                    }
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (on) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A value with − and + beside it, as one capsule; each half is a full target and is named for TalkBack. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    val less = stringResource(R.string.decrease)
    val more = stringResource(R.string.increase)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Tabular).copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
            modifier = Modifier.widthIn(min = 36.dp).padding(end = 8.dp), textAlign = TextAlign.Center, maxLines = 1)
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.textMuted.copy(alpha = if (c.isDark) 0.20f else 0.14f)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 48.dp, height = 44.dp).clickable(role = Role.Button, onClickLabel = less) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMinus() }
                .semantics { contentDescription = less }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) }
            Box(Modifier.size(width = 0.5.dp, height = 20.dp).background(c.textMuted.copy(alpha = 0.4f)))
            Box(Modifier.size(width = 48.dp, height = 44.dp).clickable(role = Role.Button, onClickLabel = more) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlus() }
                .semantics { contentDescription = more }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) }
        }
    }
}
