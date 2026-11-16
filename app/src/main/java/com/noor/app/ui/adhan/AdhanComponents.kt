package com.noor.app.ui.adhan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.ui.theme.NoorTheme

/** The slim bar of a page pushed from a tab: a back chevron (mirrored in Arabic) and the title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) = com.noor.app.ui.components.NavBar(title, onBack)

/**
 * Something that stops alerts from working as chosen, with the one action that fixes it (0.14: the whole row is the
 * action, named in gold under the explanation, so nothing is squeezed beside a button), or a line of plain status.
 */
@Composable
fun WarningRow(text: String, action: String?, onAction: (() -> Unit)?, icon: ImageVector = Icons.Rounded.WarningAmber, danger: Boolean = false) {
    val c = NoorTheme.colors
    val warn = icon == Icons.Rounded.WarningAmber
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (action != null && onAction != null) Modifier.clickable(role = Role.Button, onClickLabel = action, onClick = onAction) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.noor.app.ui.settings.IconTile(icon, if (danger) c.danger else if (warn) c.warning else c.textSecondary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = com.noor.app.ui.settings.FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 20.sp), color = if (warn || danger) c.textPrimary else c.textSecondary)
            if (action != null && onAction != null) Text(action, style = MaterialTheme.typography.labelLarge, color = c.goldText, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * A choice of two or three, drawn as a segmented control (0.14, as on iOS): a quiet track, and a raised thumb that
 * slides to the chosen segment with a spring and a tick. Gold is not used here: the thumb is the selection.
 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().clip(shape).background(c.textMuted.copy(alpha = if (c.isDark) 0.18f else 0.13f)).padding(3.dp),
    ) {
        val w = maxWidth / options.size
        val x by androidx.compose.animation.core.animateDpAsState(w * index, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(if (c.isDark) 0.dp else 2.dp, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp))
                .background(if (c.isDark) Color(0xFF3B465A) else Color.White))
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(9.dp))
                        .clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                            if (!on) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(value)
                        }
                        .semantics { this.selected = on }.padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 0.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                        color = if (on) c.textPrimary else c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Round play/stop button for previews. */
@Composable
fun PreviewButton(playing: Boolean, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (playing) c.gold else c.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) c.onGold else c.gold, modifier = Modifier.size(22.dp))
        }
    }
}

/** A selectable sound: check mark, title, detail, preview. */
@Composable
fun SoundRow(
    title: String,
    detail: String?,
    selected: Boolean,
    playing: Boolean,
    previewLabel: String,
    onSelect: (() -> Unit)?,
    onPreview: (() -> Unit)?,
    badge: String? = null,
) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onSelect != null) Modifier.clickable(role = Role.RadioButton, onClick = onSelect).semantics { this.selected = selected } else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSelect != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) c.goldText else c.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = c.gold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.gold.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (detail != null) Text(detail, style = com.noor.app.ui.settings.FootnoteStyle, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onPreview != null) {
            Spacer(Modifier.width(8.dp))
            PreviewButton(playing, previewLabel, onPreview)
        }
    }
}

/** Small explanatory paragraph under a section. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = com.noor.app.ui.settings.FootnoteStyle, color = NoorTheme.colors.textMuted, modifier = modifier.padding(horizontal = 16.dp))
}
