package com.noor.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.theme.NoorTheme

/** A titled group of rows. Section titles are quiet; gold is kept for what the user can act on or has chosen. */
@Composable
fun SettingsSection(title: String, footer: String? = null, content: @Composable () -> Unit) {
    val c = NoorTheme.colors
    Column {
        if (title.isNotEmpty()) Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp), color = c.textMuted,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() })
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 20.dp) { content() }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
    }
}

/** The icon tile at the start of a row: a soft gold square with the glyph in gold. */
@Composable
fun IconTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = NoorTheme.colors.gold) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = if (NoorTheme.colors.isDark) 0.15f else 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, value: String?, onClick: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    val c = NoorTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted)
        }
    }
}

/** Divider aligned with the row text (after the icon tile), as in a grouped list. */
@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = NoorTheme.colors.separator)

@Composable
fun NoorSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = c.gold, checkedBorderColor = c.gold,
            uncheckedThumbColor = c.textMuted, uncheckedTrackColor = c.surfaceRaised, uncheckedBorderColor = c.outline,
        ),
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    val c = NoorTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(value); onDismiss() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() },
                            colors = RadioButtonDefaults.colors(selectedColor = c.gold))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.gold) } },
    )
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = NoorTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, null, tint = c.gold) }
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, null, tint = c.gold) }
    }
}
