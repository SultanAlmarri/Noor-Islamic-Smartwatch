package com.noor.app.ui.watch

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchFaces
import com.noor.prayer.WatchFaces.Colour
import com.noor.prayer.WatchFaces.Design

/**
 * The phone's watch-face editor (0.9.8): the design and the colour of the Noor watch face, with a large preview of
 * each, and a button that puts it on the watch (the watch replaces Noor's face in place, so it changes at once).
 */
@Composable
fun FaceEditor(choice: WatchFaces.Choice, onChoose: (WatchFaces.Choice) -> Unit, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val name = faceLabel(choice)
        Crossfade(choice.id, label = "face") { id ->
            Image(
                painterResource(facePreview(id)), contentDescription = stringResource(R.string.face_preview_desc, name),
                modifier = Modifier.size(196.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Text(stringResource(designBlurb(choice.design)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        // Designs: each shown in the chosen colour.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (d in Design.entries) {
                val option = WatchFaces.Choice(d, choice.colour)
                val selected = d == choice.design
                val label = stringResource(designName(d))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) c.gold.copy(alpha = 0.14f) else Color.Transparent)
                        .border(1.dp, if (selected) c.gold else c.outline, RoundedCornerShape(14.dp))
                        .clickable { onChoose(option) }
                        .semantics { this.selected = selected; role = Role.RadioButton; contentDescription = label }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(facePreview(option.id)), null, Modifier.size(52.dp).clip(CircleShape))
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) c.goldText else c.textSecondary, maxLines = 1)
                }
            }
        }
        if (choice.design != Design.ORIGINAL) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                for (col in Colour.entries) {
                    val selected = col == choice.colour
                    val label = stringResource(colourName(col))
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).border(2.dp, if (selected) c.textPrimary else Color.Transparent, CircleShape)
                            .clickable { onChoose(WatchFaces.Choice(choice.design, col)) }
                            .semantics { this.selected = selected; role = Role.RadioButton; contentDescription = label }
                            .padding(4.dp).clip(CircleShape).background(Color(col.argb)),
                    )
                }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
fun faceLabel(choice: WatchFaces.Choice?): String {
    choice ?: return ""
    val d = stringResource(designName(choice.design))
    return if (choice.design == Design.ORIGINAL) d else d + " · " + stringResource(colourName(choice.colour))
}

@StringRes fun designName(d: Design) = when (d) {
    Design.HORIZON -> R.string.face_design_horizon
    Design.DIAL -> R.string.face_design_dial
    Design.MINIMAL -> R.string.face_design_minimal
    Design.ORIGINAL -> R.string.face_design_original
}

@StringRes private fun designBlurb(d: Design) = when (d) {
    Design.HORIZON -> R.string.face_design_horizon_desc
    Design.DIAL -> R.string.face_design_dial_desc
    Design.MINIMAL -> R.string.face_design_minimal_desc
    Design.ORIGINAL -> R.string.face_design_original_desc
}

@StringRes fun colourName(c: Colour) = when (c) {
    Colour.GOLD -> R.string.face_colour_gold
    Colour.EMERALD -> R.string.face_colour_emerald
    Colour.TURQUOISE -> R.string.face_colour_turquoise
    Colour.ROSE -> R.string.face_colour_rose
    Colour.SILVER -> R.string.face_colour_silver
}

/** The picture of a face (drawn by tools/watchface/noor_face.py with the face's own layout). */
@DrawableRes fun facePreview(id: String): Int = when (id) {
    "original" -> R.drawable.face_original
    "horizon_gold" -> R.drawable.face_horizon_gold
    "horizon_emerald" -> R.drawable.face_horizon_emerald
    "horizon_turquoise" -> R.drawable.face_horizon_turquoise
    "horizon_rose" -> R.drawable.face_horizon_rose
    "horizon_silver" -> R.drawable.face_horizon_silver
    "dial_gold" -> R.drawable.face_dial_gold
    "dial_emerald" -> R.drawable.face_dial_emerald
    "dial_turquoise" -> R.drawable.face_dial_turquoise
    "dial_rose" -> R.drawable.face_dial_rose
    "dial_silver" -> R.drawable.face_dial_silver
    "minimal_gold" -> R.drawable.face_minimal_gold
    "minimal_emerald" -> R.drawable.face_minimal_emerald
    "minimal_turquoise" -> R.drawable.face_minimal_turquoise
    "minimal_rose" -> R.drawable.face_minimal_rose
    "minimal_silver" -> R.drawable.face_minimal_silver
    else -> R.drawable.face_horizon_gold
}
