package com.noor.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.noor.wear.R
import com.noor.wear.WearText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** A full-width row button (Wear's "chip"): icon, label, and an optional line of live detail. */
@Composable
internal fun NoorChip(
    label: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    enabled: Boolean = true,
    iconTint: Color = Gold,
    labelLines: Int = 1,
    secondaryLines: Int = 2,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Night, contentColor = Ink, secondaryContentColor = Mist, iconColor = iconTint),
        icon = icon?.let { res -> { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) } },
        secondaryLabel = secondary?.let { s -> { Text(s, maxLines = secondaryLines, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = labelLines, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
internal fun Header(text: String, modifier: Modifier = Modifier) {
    ListHeader(modifier = modifier) {
        Text(text, color = Gold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = Mist.copy(alpha = 0.85f), textAlign = TextAlign.Center,
    )
}

/** "Today", "Tomorrow", "in 12 days" (with Arabic's plural forms). */
@Composable
internal fun daysUntilText(days: Long, t: WearText): String = when {
    days <= 0L -> stringResource(R.string.today)
    days == 1L -> stringResource(R.string.tomorrow)
    else -> pluralStringResource(R.plurals.in_days, days.coerceAtMost(9999).toInt(), t.digits(days.toString()))
}
