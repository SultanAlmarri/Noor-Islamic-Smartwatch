package com.noor.app.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.widget.WidgetConfigActivity
import com.noor.app.widget.WidgetKind
import com.noor.app.widget.WidgetOptionsStore
import com.noor.app.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings → Widgets (0.24): every Minhad widget on the home screen, each with a small picture of itself (tap one to
 * customise it), the defaults new widgets of each kind start with, and how to add one (or, where the home screen
 * allows it, a button that asks it to).
 */
@Composable
fun WidgetsScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var placed by remember { mutableStateOf(placedWidgets(context)) }
    // Back from a widget's page, or from the home screen after adding one: the list again.
    LifecycleResumeEffect(Unit) { placed = placedWidgets(context); onPauseOrDispose { } }
    NoorPage(stringResource(R.string.w_widgets_title), contentPadding, onBack = onBack) {
        SettingsSection(stringResource(R.string.w_widgets_placed)) {
            if (placed.isEmpty()) Text(stringResource(R.string.w_widgets_none), style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary,
                modifier = Modifier.padding(16.dp))
            else Column {
                placed.forEachIndexed { i, (kind, id) ->
                    if (i > 0) SettingsDivider()
                    WidgetRow(kind, id) {
                        context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    }
                }
            }
        }
        SettingsSection(stringResource(R.string.w_widgets_defaults), footer = stringResource(R.string.w_widgets_defaults_note)) {
            Column {
                WidgetKind.entries.forEachIndexed { i, kind ->
                    if (i > 0) SettingsDivider()
                    WidgetRow(kind, null) {
                        context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_DEFAULTS_KIND, kind.name))
                    }
                }
            }
        }
        SettingsSection(stringResource(R.string.w_widgets_how_title)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.w_widgets_how), style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary)
                val manager = AppWidgetManager.getInstance(context)
                if (android.os.Build.VERSION.SDK_INT >= 26 && runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)) {
                    NoorButton(stringResource(R.string.w_widgets_add), {
                        runCatching { manager.requestPinAppWidget(ComponentName(context, WidgetKind.NEXT.provider), null, null) }
                    }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
                }
            }
        }
    }
}

/** The Minhad widgets on the home screen, by kind, each with its id. */
private fun placedWidgets(context: Context): List<Pair<WidgetKind, Int>> {
    val manager = AppWidgetManager.getInstance(context.applicationContext)
    return WidgetKind.entries.flatMap { k ->
        (runCatching { manager.getAppWidgetIds(ComponentName(context.applicationContext, k.provider)) }.getOrNull() ?: IntArray(0)).map { k to it }
    }
}

/** A widget (or, with no id, the defaults of its kind): its small picture, its name, and a chevron. */
@Composable
private fun WidgetRow(kind: WidgetKind, id: Int?, onClick: () -> Unit) {
    val context = LocalContext.current
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val size = if (id != null) Widgets.sizeDp(AppWidgetManager.getInstance(context), id, kind) else kind.previewDp
    LaunchedEffect(kind, id) {
        picture = withContext(Dispatchers.Default) {
            runCatching {
                val o = if (id != null) WidgetOptionsStore.load(context, id, kind) else WidgetOptionsStore.defaults(context, kind)
                Widgets.preview(context, kind, o, size.first, size.second)
            }.getOrNull()
        }?.let { v -> withContext(Dispatchers.Main) { runCatching { com.noor.app.widget.draw(context, v, size) }.getOrNull() } }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = com.noor.app.ui.components.withTick(onClick)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val scale = minOf(96f / size.first, 56f / size.second)
        Box(Modifier.size(96.dp, 56.dp), contentAlignment = Alignment.Center) {
            picture?.let {
                androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.size((size.first * scale).dp, (size.second * scale).dp).clip(RoundedCornerShape((20 * scale).dp)))
            } ?: Icon(Icons.Rounded.Widgets, null, tint = NoorTheme.colors.gold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(kind.nameRes), style = MaterialTheme.typography.bodyLarge, color = NoorTheme.colors.textPrimary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NoorTheme.colors.textMuted.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
    }
}
