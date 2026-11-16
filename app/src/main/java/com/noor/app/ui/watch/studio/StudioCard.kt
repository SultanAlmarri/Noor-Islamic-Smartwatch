package com.noor.app.ui.watch.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceDesign

/** Minhad Studio on the Watch page (0.18): the design, live, and the way into the editor. */
@Composable
fun StudioCard(design: FaceDesign, config: WatchConfig?, showing: Boolean, onOpen: () -> Unit) {
    val c = NoorTheme.colors
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), corner = 24.dp, highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FaceCanvas(design, config, false, Modifier.size(104.dp), deviceArabic = isArabicUi(), description = stringResource(R.string.studio_preview_desc))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (showing) stringResource(R.string.studio_status_showing) else stringResource(R.string.studio_card_body),
                    style = FootnoteStyle, color = if (showing) c.success else c.textSecondary,
                )
            }
        }
        NoorButton(stringResource(if (showing) R.string.studio_card_edit else R.string.studio_card_open), onOpen, Modifier.fillMaxWidth().padding(top = 16.dp),
            icon = Icons.Rounded.Palette)
    }
}
