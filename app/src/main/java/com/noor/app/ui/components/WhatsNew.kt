package com.noor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.noor.app.R
import com.noor.app.ui.settings.Tints
import com.noor.app.ui.theme.NoorTheme
import kotlinx.coroutines.launch

/**
 * "What's new" (0.25), as the apps of an iPhone say it after an update: once, in a sheet over the home screen, the
 * few things that changed, each a coloured sign, a few bold words and one quiet line, and one button. It is shown to
 * someone who had Minhad before this edition, the first time they open it after the update; never after the welcome
 * (a new user is told nothing is "new"), never twice ([com.noor.app.data.AppSettings.newsSeen]).
 */
object WhatsNew {
    /** The number of the page below. Raise it when the page is written anew for a later version. */
    const val EDITION = 26

    fun due(onboardingDone: Boolean, seen: Int): Boolean = onboardingDone && seen < EDITION
}

private data class NewsItem(val icon: @Composable () -> androidx.compose.ui.graphics.painter.Painter, val tint: Color, val title: Int, val body: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    NoorSheet(onDismiss, sheet) { WhatsNewContent { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } } }
}

/** What the sheet holds (apart, so that it can be drawn in a test: a sheet opens in a window of its own). */
@Composable
fun WhatsNewContent(onContinue: () -> Unit) {
    val c = NoorTheme.colors
    // (0.26) The page written anew for 0.26, its signs the app's own discs of sky (IconTile's look), not tiles.
    val items = remember {
        listOf(
            NewsItem({ rememberVectorPainter(Icons.Rounded.Mosque) }, Tints.Purple, R.string.news26_adhan_title, R.string.news26_adhan_body),
            NewsItem({ rememberVectorPainter(Icons.Rounded.Alarm) }, Tints.Orange, R.string.news26_alarms_title, R.string.news26_alarms_body),
            NewsItem({ painterResource(R.drawable.ic_kaaba) }, Tints.Indigo, R.string.news26_qibla_title, R.string.news26_qibla_body),
            NewsItem({ rememberVectorPainter(Icons.Rounded.MusicNote) }, Tints.Pink, R.string.news26_sounds_title, R.string.news26_sounds_body),
            NewsItem({ rememberVectorPainter(Icons.Rounded.AutoAwesome) }, Tints.Gold, R.string.news26_signs_title, R.string.news26_signs_body),
        )
    }
    run {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.news_title), color = c.textPrimary, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 18.dp, bottom = 26.dp).semantics { heading() })
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.noor.app.ui.onboarding.SignTile(item.icon(), item.tint, 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(item.title), color = c.textPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(stringResource(item.body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            NoorButton(stringResource(R.string.news_continue), onContinue,
                Modifier.padding(top = 30.dp, bottom = 16.dp).widthIn(max = 420.dp).fillMaxWidth())
        }
    }
}
