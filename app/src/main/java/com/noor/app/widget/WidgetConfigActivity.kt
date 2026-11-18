package com.noor.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.data.AppSettings
import com.noor.app.rememberDaylight
import com.noor.app.rememberSun
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.settings.SectionFooter
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.theme.NoorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A widget's settings (0.23): opened by the home screen when the widget is placed (before Android 12) or from its
 * menu (long press, Settings; Android 12 and later, where it is optional when placing). At the top, the widget itself
 * as it is now, redrawn as each option changes (the same views the home screen shows, built by [Widgets.preview]);
 * under it, the kind's options ([WidgetKind.options]). Each change is kept at once for this widget's id and the home
 * screen updated; Done closes the page.
 */
class WidgetConfigActivity : AppCompatActivity() {
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // The widget is placed whatever the page's end (its defaults are good): leaving it never takes the widget away.
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val manager = AppWidgetManager.getInstance(this)
        val provider = runCatching { manager.getAppWidgetInfo(id)?.provider?.className }.getOrNull()
        val kind = WidgetKind.ofClassName(provider) ?: run { finish(); return }
        val container = (application as NoorApplication).container
        val size = Widgets.sizeDp(manager, id, kind)
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: AppSettings()
            NoorTheme(s.theme, rememberDaylight(s, container.timetables), rememberSun(s)) {
                NoorBackground {
                    if (settings != null) WidgetSettings(kind, WidgetOptionsStore.load(this@WidgetConfigActivity, id, kind), size, onChange = { o ->
                        WidgetOptionsStore.save(this@WidgetConfigActivity, id, o)
                        Widgets.refreshAsync(this@WidgetConfigActivity)
                    }, onDone = { finish() })
                }
            }
        }
    }
}

/** The settings page: the live preview and the options. Also drawn by the tests. */
@Composable
internal fun WidgetSettings(kind: WidgetKind, initial: WidgetOptions, sizeDp: Pair<Int, Int>, onChange: (WidgetOptions) -> Unit, onDone: () -> Unit,
                            preview: (suspend (Context, WidgetOptions) -> RemoteViews?)? = null) {
    var o by remember { mutableStateOf(initial) }
    fun set(n: WidgetOptions) { o = n; onChange(n) }
    val bars = WindowInsets.safeDrawing.asPaddingValues()
    val has = kind.options.toSet()
    NoorPage(stringResource(kind.nameRes), PaddingValues(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding() + 16.dp), onBack = onDone) {
        WidgetPreview(kind, o, sizeDp, preview)
        if (WidgetOption.BACKGROUND in has || WidgetOption.ACCENT in has || WidgetOption.TEXT_SIZE in has) {
            SettingsSection(stringResource(R.string.widget_section_look)) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (WidgetOption.BACKGROUND in has) {
                        Label(stringResource(R.string.widget_opt_background))
                        val bgs = WidgetBackground.entries.filter { it != WidgetBackground.YOU || android.os.Build.VERSION.SDK_INT >= 31 }
                        Chips(bgs.map { it to stringResource(backgroundName(it)) }, o.background) { set(o.copy(background = it)) }
                        if (o.background == WidgetBackground.GLASS || o.background == WidgetBackground.DARK || o.background == WidgetBackground.LIGHT) {
                            Label(stringResource(R.string.widget_opt_opacity))
                            Slider(o.opacity / 100f, { set(o.copy(opacity = (it * 100).toInt())) }, valueRange = 0.2f..1f,
                                colors = SliderDefaults.colors(thumbColor = NoorTheme.colors.gold, activeTrackColor = NoorTheme.colors.gold),
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (WidgetOption.ACCENT in has) {
                        Label(stringResource(R.string.widget_opt_accent))
                        val accents = WidgetAccent.entries.filter { it != WidgetAccent.YOU || android.os.Build.VERSION.SDK_INT >= 31 }
                        Swatches(accents, o.accent) { set(o.copy(accent = it)) }
                    }
                    if (WidgetOption.TEXT_SIZE in has) {
                        Label(stringResource(R.string.widget_opt_text))
                        Chips(listOf(WidgetTextSize.SMALL to stringResource(R.string.widget_ts_small), WidgetTextSize.NORMAL to stringResource(R.string.widget_ts_normal),
                            WidgetTextSize.LARGE to stringResource(R.string.widget_ts_large)), o.textSize) { set(o.copy(textSize = it)) }
                    }
                }
            }
        }
        val switches = listOfNotNull(
            (WidgetOption.COUNTDOWN in has).takeIf { it }?.let { Triple(Icons.Rounded.Timer, R.string.widget_opt_countdown, WidgetOption.COUNTDOWN) },
            (WidgetOption.SECONDS in has && o.countdown).takeIf { it }?.let { Triple(Icons.Rounded.AccessTime, R.string.widget_opt_seconds, WidgetOption.SECONDS) },
            (WidgetOption.SHURUQ in has).takeIf { it }?.let { Triple(Icons.Rounded.WbSunny, R.string.widget_opt_shuruq, WidgetOption.SHURUQ) },
            (WidgetOption.PLACE in has).takeIf { it }?.let { Triple(Icons.Rounded.Place, R.string.widget_opt_place, WidgetOption.PLACE) },
            (WidgetOption.HIJRI in has).takeIf { it }?.let { Triple(Icons.Rounded.CalendarMonth, R.string.widget_opt_hijri, WidgetOption.HIJRI) },
            (WidgetOption.JUMUAH in has).takeIf { it }?.let { Triple(Icons.Rounded.LightMode, R.string.widget_opt_jumuah, WidgetOption.JUMUAH) },
        )
        if (switches.isNotEmpty()) SettingsSection(stringResource(R.string.widget_section_show)) {
            Column {
                switches.forEachIndexed { i, (icon, title, opt) ->
                    if (i > 0) SettingsDivider()
                    val on = when (opt) {
                        WidgetOption.COUNTDOWN -> o.countdown; WidgetOption.SECONDS -> o.seconds; WidgetOption.SHURUQ -> o.shuruq
                        WidgetOption.PLACE -> o.place; WidgetOption.HIJRI -> o.hijri; else -> o.jumuah
                    }
                    SettingsSwitch(icon, stringResource(title), if (opt == WidgetOption.COUNTDOWN && !on) stringResource(R.string.widget_opt_countdown_off) else null, on) { v ->
                        set(when (opt) {
                            WidgetOption.COUNTDOWN -> o.copy(countdown = v); WidgetOption.SECONDS -> o.copy(seconds = v); WidgetOption.SHURUQ -> o.copy(shuruq = v)
                            WidgetOption.PLACE -> o.copy(place = v); WidgetOption.HIJRI -> o.copy(hijri = v); else -> o.copy(jumuah = v)
                        })
                    }
                }
            }
        }
        if (WidgetOption.TAP in has) SettingsSection(stringResource(R.string.widget_opt_tap)) {
            Box(Modifier.padding(vertical = 10.dp)) {
                Chips(listOf(WidgetTap.HOME to stringResource(R.string.widget_tap_home), WidgetTap.QIBLA to stringResource(R.string.widget_tap_qibla),
                    WidgetTap.ADHAN to stringResource(R.string.widget_tap_adhan)), o.tap) { set(o.copy(tap = it)) }
            }
        }
        NoorButton(stringResource(R.string.widget_done), onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Filled)
    }
}

/** A single choice as chips that wrap onto a second line inside their card (never cut at its edge). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = NoorTheme.colors
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), maxLines = 1,
                color = if (on) c.onGold else c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.gold else c.glassRaised)
                    .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = com.noor.app.ui.components.withTick { onSelect(value) })
                    .heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

/** The accent colours as round swatches, the chosen one ringed; each is named for TalkBack. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Swatches(accents: List<WidgetAccent>, selected: WidgetAccent, onSelect: (WidgetAccent) -> Unit) {
    val c = NoorTheme.colors
    val context = LocalContext.current
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        accents.forEach { a ->
            val on = a == selected
            val name = stringResource(accentName(a))
            val fill = if (a == WidgetAccent.YOU) Brush.sweepGradient(listOf(Color(WidgetPalette.accentOn(a, false, context)), Color(0xFF7FA7E0), Color(0xFFE0A0B8), Color(WidgetPalette.accentOn(a, false, context))))
                else androidx.compose.ui.graphics.SolidColor(Color(WidgetPalette.accentOn(a, false, null)))
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .then(if (on) Modifier.border(2.dp, c.textPrimary, androidx.compose.foundation.shape.CircleShape) else Modifier)
                .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = com.noor.app.ui.components.withTick { onSelect(a) })
                .semantics { contentDescription = name }
                .padding(if (on) 6.dp else 3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(fill))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * The widget as the home screen will show it, at its size (made smaller to fit the page), over a quiet wallpaper so
 * glass and no background can be judged. Rebuilt off the main thread a moment after each change.
 */
@Composable
private fun WidgetPreview(kind: WidgetKind, o: WidgetOptions, sizeDp: Pair<Int, Int>, preview: (suspend (Context, WidgetOptions) -> RemoteViews?)?) {
    val context = LocalContext.current
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(o) {
        delay(60)
        val views = withContext(Dispatchers.Default) {
            try { preview?.invoke(context, o) ?: Widgets.preview(context, kind, o, sizeDp.first, sizeDp.second) }
            catch (e: Exception) { android.util.Log.w("MinhadWidget", "preview", e); null }
        }
        // The home screen's views, laid out at the widget's size and drawn, here on the main thread, as a picture (the
        // countdown's seconds stand still in it; on the home screen they turn).
        picture = views?.let { v -> runCatching { draw(context, v, sizeDp) }.onFailure { android.util.Log.w("MinhadWidget", "preview draw", it) }.getOrNull() }
    }
    val wall = Brush.linearGradient(listOf(Color(0xFF3B4C6B), Color(0xFF6E5A7E), Color(0xFFB98A6B)))
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(wall).padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
            val room = maxWidth - 32.dp
            val scale = minOf(1f, room.value / sizeDp.first)
            Box(Modifier.width(room).height((sizeDp.second * scale).dp), contentAlignment = Alignment.Center) {
                picture?.let { bmp ->
                    androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size((sizeDp.first * scale).dp, (sizeDp.second * scale).dp))
                }
            }
        }
        SectionFooter(stringResource(R.string.widget_preview_note))
    }
}

/** [views] applied, laid out at [sizeDp] and drawn into a picture. */
private fun draw(context: Context, views: RemoteViews, sizeDp: Pair<Int, Int>): android.graphics.Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (sizeDp.first * d).toInt(); val h = (sizeDp.second * d).toInt()
    val frame = FrameLayout(context)
    frame.addView(views.apply(context, frame), FrameLayout.LayoutParams(w, h))
    frame.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
    frame.layout(0, 0, w, h)
    return android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { frame.draw(android.graphics.Canvas(it)) }
}

private fun backgroundName(b: WidgetBackground) = when (b) {
    WidgetBackground.SKY -> R.string.widget_bg_sky; WidgetBackground.GLASS -> R.string.widget_bg_glass; WidgetBackground.DARK -> R.string.widget_bg_dark
    WidgetBackground.LIGHT -> R.string.widget_bg_light; WidgetBackground.YOU -> R.string.widget_bg_you; WidgetBackground.CLEAR -> R.string.widget_bg_clear
}

private fun accentName(a: WidgetAccent) = when (a) {
    WidgetAccent.GOLD -> R.string.widget_ac_gold; WidgetAccent.TEAL -> R.string.widget_ac_teal; WidgetAccent.ROSE -> R.string.widget_ac_rose
    WidgetAccent.BLUE -> R.string.widget_ac_blue; WidgetAccent.WHITE -> R.string.widget_ac_white; WidgetAccent.YOU -> R.string.widget_ac_you
}
