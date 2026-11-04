package com.noor.app.ui.adhan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
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
import com.noor.app.ui.theme.NoorTheme

/** Screen title row with a back arrow (mirrored in Arabic). */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val c = NoorTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.textPrimary) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
    }
}

/** A problem that stops alerts from working as chosen, with the one action that fixes it. */
@Composable
fun WarningRow(text: String, action: String?, onAction: (() -> Unit)?, icon: ImageVector = Icons.Rounded.WarningAmber) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onAction, border = BorderStroke(1.dp, c.gold.copy(alpha = 0.6f))) {
                Text(action, color = c.goldText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Three-way choice (phone / watch / both), drawn as a segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceRaised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) c.gold else c.surfaceRaised)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onGold else c.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Round play/stop button for previews. */
@Composable
fun PreviewButton(playing: Boolean, label: String, onClick: () -> Unit) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (playing) c.gold else c.gold.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) c.onGold else c.gold, modifier = Modifier.size(22.dp))
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
    onPreview: () -> Unit,
    badge: String? = null,
) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .then(if (onSelect != null) Modifier.clickable(role = Role.RadioButton, onClick = onSelect).semantics { this.selected = selected } else Modifier)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        PreviewButton(playing, previewLabel, onPreview)
    }
}

/** Small explanatory paragraph under a section. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = NoorTheme.colors.textSecondary, modifier = modifier.padding(horizontal = 8.dp))
}
