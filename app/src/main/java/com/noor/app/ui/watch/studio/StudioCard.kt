package com.noor.app.ui.watch.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.noor.app.ui.components.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.watch.WatchCase
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceDesign

/**
 * Minhad Studio on the Watch page (0.18), and since 0.25 the page's one watch: the user's own face in a watch's case,
 * drawn by the very renderer the watch draws it with, so it shows this moment's time, today's date and the next
 * prayer, in the language of the app. Under it, whether the watch is showing it now ([showing]: the watch says so
 * itself) or what the Studio is, and the way into the editor. The watch itself opens the editor too.
 */
@Composable
fun StudioCard(design: FaceDesign, config: WatchConfig?, showing: Boolean, glow: Color? = null, compact: Boolean = false, onOpen: () -> Unit) {
    val c = NoorTheme.colors
    val action = stringResource(if (showing) R.string.studio_card_edit else R.string.studio_card_open)
    val source = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // (The watch gives under the finger, as the app's buttons do; no ripple: its square would show round the case.)
        WatchCase(Modifier.pressScale(source, 0.98f).clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = action, onClick = onOpen), glow = glow) {
            FaceCanvas(design, config, false, Modifier.fillMaxSize(), deviceArabic = isArabicUi(), description = stringResource(R.string.studio_preview_desc))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        if (showing) Row(verticalAlignment = Alignment.CenterVertically) {
            // (As a connected watch is marked: a green dot, the words in the page's own quiet colour.)
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.success))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.studio_status_showing), style = FootnoteStyle, color = c.textSecondary)
        } else Text(stringResource(R.string.studio_card_body), style = FootnoteStyle, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp).padding(horizontal = 16.dp))
        // (Tonal: the page's one filled button belongs to the set-up step that waits, when one does. [compact], while the
        // watch is set up: the picture and the name; the watch itself opens the editor.)
        if (!compact) NoorButton(action, onOpen, Modifier.padding(top = 14.dp), kind = ButtonKind.Tonal, icon = Icons.Rounded.Palette)
    }
}
