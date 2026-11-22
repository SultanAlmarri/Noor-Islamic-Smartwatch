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
    const val EDITION = 25

    fun due(onboardingDone: Boolean, seen: Int): Boolean = onboardingDone && seen < EDITION
}

private data class NewsItem(val icon: ImageVector, val tint: Color, val title: Int, val body: Int)

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
    val items = remember {
        listOf(
            NewsItem(Icons.Rounded.AutoAwesome, Tints.Indigo, R.string.news_look_title, R.string.news_look_body),
            NewsItem(Icons.Rounded.CalendarMonth, Tints.Red, R.string.news_month_title, R.string.news_month_body),
            NewsItem(Icons.Rounded.NotificationsActive, Tints.Gold, R.string.news_settings_title, R.string.news_settings_body),
            NewsItem(Icons.Rounded.Tune, Tints.Blue, R.string.news_motion_title, R.string.news_motion_body),
            NewsItem(Icons.Rounded.Watch, Tints.Graphite, R.string.news_watch_title, R.string.news_watch_body),
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
                        val brush = remember(item.tint) { Brush.verticalGradient(listOf(lerp(item.tint, Color.White, 0.20f), item.tint, lerp(item.tint, Color.Black, 0.10f))) }
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(brush), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
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
