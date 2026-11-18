package com.noor.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.unit.sp
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
 * A widget's settings (0.23; 0.24 every part of it can be chosen): opened by the home screen when the widget is
 * placed (before Android 12) or from its menu (long press, Settings; Android 12 and later), and from Minhad's
 * Settings → Widgets. At the top, the widget itself, redrawn as each option changes, at its own size or the kind's
 * small, wide and large ones; under it, Look, Content and Tap. Each change is kept at once for this widget and the
 * home screen updated. With no widget id the page edits the defaults for new widgets of the kind.
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
        val defaultsFor = intent?.getStringExtra(EXTRA_DEFAULTS_KIND)?.let { n -> WidgetKind.entries.firstOrNull { it.name == n } }
        val kind = WidgetKind.ofClassName(provider) ?: defaultsFor ?: run { finish(); return }
        val editingDefaults = provider == null
        val container = (application as NoorApplication).container
        val size = if (editingDefaults) kind.previewDp else Widgets.sizeDp(manager, id, kind)
        val start = if (editingDefaults) WidgetOptionsStore.defaults(this, kind) else WidgetOptionsStore.load(this, id, kind)
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings ?: AppSettings()
            NoorTheme(s.theme, rememberDaylight(s, container.timetables), rememberSun(s)) {
                NoorBackground {
                    if (settings != null) WidgetSettings(kind, start, size,
                        onChange = { o ->
                            if (editingDefaults) WidgetOptionsStore.saveDefaults(this@WidgetConfigActivity, kind, o)
                            else WidgetOptionsStore.save(this@WidgetConfigActivity, id, o)
                            Widgets.refreshAsync(this@WidgetConfigActivity)
                        },
                        onDone = { finish() },
                        onApplyAll = if (editingDefaults) null else { o -> applyToAll(this@WidgetConfigActivity, o) },
                        own = !editingDefaults,
                    )
                }
            }
        }
    }

    companion object {
        /** Opens the page for the defaults of new widgets of a kind (its name), with no widget id. */
        const val EXTRA_DEFAULTS_KIND = "com.noor.app.widget.DEFAULTS_KIND"

        /** These options for every Minhad widget on the home screen, and as the defaults for new ones. */
        fun applyToAll(context: Context, o: WidgetOptions) {
            val manager = AppWidgetManager.getInstance(context)
            for (k in WidgetKind.entries) {
                val ids = runCatching { manager.getAppWidgetIds(android.content.ComponentName(context, k.provider)) }.getOrNull() ?: IntArray(0)
                for (i in ids) WidgetOptionsStore.save(context, i, o)
                WidgetOptionsStore.saveDefaults(context, k, o)
            }
            Widgets.refreshAsync(context)
        }
    }
}

/** The sizes the page shows the widget at: its own (when placed), and the kind's small, wide and large. */
internal fun previewSizes(kind: WidgetKind): List<Pair<Int, Pair<Int, Int>>> = when (kind) {
    WidgetKind.STRIP -> listOf(R.string.w_size_small to (176 to 84), R.string.w_size_wide to (360 to 84), R.string.w_size_large to (360 to 176))
    WidgetKind.RING -> listOf(R.string.w_size_small to (176 to 176), R.string.w_size_large to (250 to 250))
    else -> listOf(R.string.w_size_small to (176 to 176), R.string.w_size_wide to (360 to 176), R.string.w_size_large to (360 to 372))
}

/** The settings page: the live preview and the options. Also drawn by the tests. */
@Composable
internal fun WidgetSettings(kind: WidgetKind, initial: WidgetOptions, sizeDp: Pair<Int, Int>, onChange: (WidgetOptions) -> Unit, onDone: () -> Unit,
                            preview: (suspend (Context, WidgetOptions) -> RemoteViews?)? = null,
                            onApplyAll: ((WidgetOptions) -> Unit)? = null, own: Boolean = true) {
    var o by remember { mutableStateOf(initial) }
    fun set(n: WidgetOptions) { o = n; onChange(n) }
    val bars = WindowInsets.safeDrawing.asPaddingValues()
    val prayers = kind.prayers
    var applied by remember { mutableStateOf(false) }
    val sizes = (if (own) listOf(R.string.w_size_yours to sizeDp) else emptyList()) + previewSizes(kind)
    var size by remember { mutableStateOf(sizes.first().second) }
    var looks by remember { mutableStateOf<WidgetLooks?>(null) }
    NoorPage(stringResource(kind.nameRes), PaddingValues(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding() + 16.dp), onBack = onDone) {
        WidgetPreview(kind, o, size, if (size == sizeDp) preview else null) { looks = it }
        Chips(sizes.map { it.second to stringResource(it.first) }.distinctBy { it.first }, size) { size = it }

        // ---- Look ----
        SettingsSection(stringResource(R.string.widget_section_look)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(stringResource(R.string.widget_opt_background))
                val bgs = listOf(WidgetBackground.SKY to R.string.widget_bg_sky, WidgetBackground.SOLID to R.string.w_bg_solid, WidgetBackground.GRADIENT to R.string.w_bg_gradient,
                    WidgetBackground.DARK to R.string.widget_bg_dark, WidgetBackground.LIGHT to R.string.widget_bg_light, WidgetBackground.AUTO to R.string.w_bg_auto,
                    WidgetBackground.GLASS to R.string.widget_bg_glass, WidgetBackground.YOU to R.string.widget_bg_you, WidgetBackground.CLEAR to R.string.w_bg_clear)
                    .filter { it.first != WidgetBackground.YOU || android.os.Build.VERSION.SDK_INT >= 31 }
                Chips(bgs.map { it.first to stringResource(it.second) }, o.background) { set(o.copy(background = it)) }
                when (o.background) {
                    WidgetBackground.SKY -> Chips(listOf(SkyLight.LIVE to R.string.w_sky_live, SkyLight.DAWN to R.string.w_sky_dawn, SkyLight.DAY to R.string.w_sky_day,
                        SkyLight.GOLDEN to R.string.w_sky_golden, SkyLight.NIGHT to R.string.w_sky_night).map { it.first to stringResource(it.second) }, o.skyLight) { set(o.copy(skyLight = it)) }
                    WidgetBackground.SOLID -> ColorChoice(o.color1) { set(o.copy(color1 = it)) }
                    WidgetBackground.GRADIENT -> {
                        Label(stringResource(R.string.w_gradient_top)); ColorChoice(o.color1) { set(o.copy(color1 = it)) }
                        Label(stringResource(R.string.w_gradient_bottom)); ColorChoice(o.color2) { set(o.copy(color2 = it)) }
                    }
                    else -> Unit
                }
                if (o.background != WidgetBackground.CLEAR) {
                    Label(stringResource(R.string.widget_opt_opacity))
                    GoldSlider(o.opacity / 100f, 0.2f..1f) { set(o.copy(opacity = (it * 100).toInt())) }
                }
                Label(stringResource(R.string.w_text_color))
                Chips(listOf(TextColor.AUTO to R.string.w_auto, TextColor.WHITE to R.string.w_white, TextColor.BLACK to R.string.w_black, TextColor.CUSTOM to R.string.w_custom)
                    .map { it.first to stringResource(it.second) }, o.textColor) { set(o.copy(textColor = it)) }
                if (o.textColor == TextColor.CUSTOM) Hsv(o.textCustom) { set(o.copy(textCustom = it)) }
                // Beside the colour: it would not read here, and what the widget shows instead.
                looks?.takeIf { it.textFallback }?.let { l ->
                    val shown = when (l.ink) { 0xFFFFFFFF.toInt() -> R.string.w_shown_white; 0xFF1C1C1E.toInt() -> R.string.w_shown_black; else -> R.string.w_shown_readable }
                    Warning(stringResource(R.string.w_warn_text_shown, stringResource(shown)))
                }
                if (prayers || kind == WidgetKind.DATE) {
                    Label(stringResource(R.string.widget_opt_accent))
                    val accents = WidgetAccent.entries.filter { it != WidgetAccent.YOU || android.os.Build.VERSION.SDK_INT >= 31 }
                    Swatches(accents, o.accent) { set(o.copy(accent = it)) }
                    if (o.accent == WidgetAccent.CUSTOM) Hsv(o.accentCustom) { set(o.copy(accentCustom = it)) }
                    if (looks?.accentFallback == true) Warning(stringResource(R.string.w_warn_accent))
                }
                if (prayers) {
                    Label(stringResource(R.string.w_icons))
                    Chips(listOf(IconStyle.COLOR to R.string.w_icons_color, IconStyle.MONO to R.string.w_icons_mono, IconStyle.OFF to R.string.w_off)
                        .map { it.first to stringResource(it.second) }, o.icons) { set(o.copy(icons = it)) }
                }
                Label(stringResource(R.string.widget_opt_text) + "  " + "${(o.textScaleFactor * 100).toInt()} %")
                GoldSlider(o.textScaleFactor, 0.8f..1.3f, steps = 9) { set(o.copy(textScale = ((it * 100 / 5).toInt() * 5).coerceIn(80, 130))) }
                if (kind == WidgetKind.NEXT || kind == WidgetKind.DATE) {
                    Label(stringResource(R.string.w_align))
                    Chips(listOf(WidgetAlign.START to R.string.w_align_start, WidgetAlign.CENTER to R.string.w_align_center).map { it.first to stringResource(it.second) }, o.align) { set(o.copy(align = it)) }
                }
            }
        }

        // ---- Content ----
        if (prayers) SettingsSection(stringResource(R.string.w_section_content)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(stringResource(R.string.w_main))
                Chips(listOf(MainNumber.COUNTDOWN to R.string.w_main_count, MainNumber.ADHAN to R.string.w_main_adhan).map { it.first to stringResource(it.second) }, o.main) { set(o.copy(main = it)) }
                Label(stringResource(R.string.w_count_style))
                Chips(listOf(CountStyle.TIMER to R.string.w_count_timer, CountStyle.WORDS to R.string.w_count_words).map { it.first to stringResource(it.second) }, o.countStyle) { set(o.copy(countStyle = it)) }
                Label(stringResource(R.string.widget_opt_place))
                Chips(listOf(PlaceMode.FULL to R.string.w_place_full, PlaceMode.AREA to R.string.w_place_area, PlaceMode.OFF to R.string.w_off).map { it.first to stringResource(it.second) },
                    o.placeShown) { set(o.copy(place = it != PlaceMode.OFF, placeMode = it)) }
                Label(stringResource(R.string.w_prayers))
                Chips(listOf(ShuruqChoice.AUTO to R.string.w_prayers_auto, ShuruqChoice.SHOW to R.string.w_prayers_all, ShuruqChoice.HIDE to R.string.w_prayers_five).map { it.first to stringResource(it.second) },
                    o.shuruqShown) { set(o.copy(shuruq = it != ShuruqChoice.HIDE, shuruqChoice = it)) }
                if (kind == WidgetKind.TODAY || kind == WidgetKind.STRIP || kind == WidgetKind.SKY) {
                    Label(stringResource(R.string.w_order))
                    Chips(listOf(RowOrder.LANGUAGE to R.string.w_order_lang, RowOrder.FAJR_LEFT to R.string.w_order_ltr).map { it.first to stringResource(it.second) }, o.rowOrder) { set(o.copy(rowOrder = it)) }
                }
                Label(stringResource(R.string.w_past))
                Chips(listOf(PastStyle.DIM to R.string.w_past_dim, PastStyle.NORMAL to R.string.w_past_normal, PastStyle.HIDE to R.string.w_past_hide).map { it.first to stringResource(it.second) }, o.past) { set(o.copy(past = it)) }
                Label(stringResource(R.string.w_highlight))
                Chips(listOf(HighlightStyle.FILLED to R.string.w_hl_filled, HighlightStyle.OUTLINE to R.string.w_hl_outline, HighlightStyle.COLOR to R.string.w_hl_color).map { it.first to stringResource(it.second) }, o.highlight) { set(o.copy(highlight = it)) }
                Label(stringResource(R.string.w_after))
                Chips(listOf(AfterAdhan.SINCE to R.string.w_after_since, AfterAdhan.NEXT to R.string.w_after_next).map { it.first to stringResource(it.second) }, o.after) { set(o.copy(after = it)) }
            }
        }
        if (prayers) SettingsSection(stringResource(R.string.widget_section_show)) {
            Column {
                data class Sw(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val on: Boolean, val apply: (Boolean) -> WidgetOptions)
                val rows = listOf(
                    Sw(Icons.Rounded.Image, R.string.w_show_icon, o.showIcon) { o.copy(showIcon = it) },
                    Sw(Icons.Rounded.TextFields, R.string.w_show_name, o.showName) { o.copy(showName = it) },
                    Sw(Icons.Rounded.Schedule, R.string.w_show_time, o.showTime) { o.copy(showTime = it) },
                    Sw(Icons.Rounded.Timer, R.string.widget_opt_countdown, o.countdown) { o.copy(countdown = it) },
                    Sw(Icons.Rounded.AccessTime, R.string.widget_opt_seconds, o.seconds) { o.copy(seconds = it) },
                    Sw(Icons.Rounded.LinearScale, R.string.w_show_bar, o.showBar) { o.copy(showBar = it) },
                    Sw(Icons.Rounded.CalendarMonth, R.string.widget_opt_hijri, o.hijri) { o.copy(hijri = it) },
                    Sw(Icons.Rounded.Event, R.string.w_show_greg, o.gregorian) { o.copy(gregorian = it) },
                    Sw(Icons.Rounded.FormatListBulleted, R.string.w_show_later, o.showLater) { o.copy(showLater = it) },
                    Sw(Icons.Rounded.LightMode, R.string.widget_opt_jumuah, o.jumuah) { o.copy(jumuah = it) },
                    Sw(Icons.Rounded.Schedule, R.string.w_ampm, o.ampm) { o.copy(ampm = it) },
                )
                rows.forEachIndexed { i, r ->
                    if (i > 0) SettingsDivider()
                    SettingsSwitch(r.icon, stringResource(r.title), null, r.on) { v -> set(r.apply(v)) }
                }
            }
        }
        // ---- As in the app, or this widget's own ----
        SettingsSection(stringResource(R.string.w_language)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Chips(listOf(LanguageChoice.APP to R.string.w_as_app, LanguageChoice.ENGLISH to R.string.w_lang_en, LanguageChoice.ARABIC to R.string.w_lang_ar).map { it.first to stringResource(it.second) }, o.language) { set(o.copy(language = it)) }
                Label(stringResource(R.string.w_digits))
                Chips(listOf(DigitChoice.APP to R.string.w_as_app, DigitChoice.WESTERN to R.string.w_digits_western, DigitChoice.ARABIC_INDIC to R.string.w_digits_indic).map { it.first to stringResource(it.second) }, o.digits) { set(o.copy(digits = it)) }
                if (prayers) {
                    Label(stringResource(R.string.w_time_format))
                    Chips(listOf(TimeChoice.APP to R.string.w_as_app, TimeChoice.H12 to R.string.w_12h, TimeChoice.H24 to R.string.w_24h).map { it.first to stringResource(it.second) }, o.timeFormat) { set(o.copy(timeFormat = it)) }
                }
            }
        }
        SettingsSection(stringResource(R.string.widget_opt_tap)) {
            Box(Modifier.padding(vertical = 10.dp)) {
                Chips(listOf(WidgetTap.HOME to stringResource(R.string.widget_tap_home), WidgetTap.QIBLA to stringResource(R.string.widget_tap_qibla),
                    WidgetTap.ADHAN to stringResource(R.string.widget_tap_adhan)), o.tap) { set(o.copy(tap = it)) }
            }
        }
        NoorButton(stringResource(R.string.w_reset), { set(kind.defaults) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
        if (onApplyAll != null) {
            NoorButton(stringResource(R.string.w_apply_all), { onApplyAll(o); applied = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
            if (applied) SectionFooter(stringResource(R.string.w_applied))
        }
        NoorButton(stringResource(R.string.widget_done), onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Filled)
    }
}

@Composable
private fun Warning(text: String) {
    val c = NoorTheme.colors
    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FFB14A)).padding(12.dp))
}

@Composable
private fun GoldSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(thumbColor = NoorTheme.colors.gold, activeTrackColor = NoorTheme.colors.gold),
        modifier = Modifier.padding(horizontal = 16.dp))
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

/** Twelve colours to choose from, and one's own (hue, saturation, brightness). */
internal val SWATCHES = listOf(0xFF2F7FD8, 0xFF1C4E8C, 0xFF0B1430, 0xFF3A4F9A, 0xFF6A4C9C, 0xFF8A6AA0, 0xFFE0896A, 0xFFB5523B,
    0xFF2E7D5B, 0xFF14524A, 0xFF1C1C1E, 0xFFF2F2F7).map { it.toInt() }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorChoice(value: Int, onChange: (Int) -> Unit) {
    val c = NoorTheme.colors
    var custom by remember { mutableStateOf(value !in SWATCHES) }
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SWATCHES.forEach { sw ->
            val on = sw == value && !custom
            Box(Modifier.size(40.dp).clip(CircleShape).then(if (on) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier)
                .selectable(on, onClick = com.noor.app.ui.components.withTick { custom = false; onChange(sw) })
                .semantics { contentDescription = "#%06X".format(sw and 0xFFFFFF) }
                .padding(if (on) 5.dp else 2.dp).clip(CircleShape).background(Color(sw)))
        }
        Text(stringResource(R.string.w_custom), style = MaterialTheme.typography.labelLarge, color = if (custom) c.onGold else c.textSecondary,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (custom) c.gold else c.glassRaised)
                .selectable(custom, onClick = com.noor.app.ui.components.withTick { custom = true }).heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp))
    }
    if (custom) Hsv(value, onChange)
}

/** A colour by hue, saturation and brightness, with its swatch. */
@Composable
private fun Hsv(value: Int, onChange: (Int) -> Unit) {
    val hsv = remember(value) { FloatArray(3).also { android.graphics.Color.colorToHSV(value, it) } }
    fun emit(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(value)))
            Spacer(Modifier.width(10.dp))
            Text("#%06X".format(value and 0xFFFFFF), style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary)
        }
        Label(stringResource(R.string.w_hue)); GoldSlider(hsv[0], 0f..360f) { emit(h = it) }
        Label(stringResource(R.string.w_saturation)); GoldSlider(hsv[1], 0f..1f) { emit(s = it) }
        Label(stringResource(R.string.w_brightness)); GoldSlider(hsv[2], 0f..1f) { emit(v = it) }
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
            val fill = when (a) {
                WidgetAccent.YOU -> Brush.sweepGradient(listOf(Color(WidgetPalette.accentOn(a, false, context)), Color(0xFF7FA7E0), Color(0xFFE0A0B8), Color(WidgetPalette.accentOn(a, false, context))))
                WidgetAccent.CUSTOM -> Brush.sweepGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFD36B), Color(0xFF6BFFB0), Color(0xFF6BB0FF), Color(0xFFD06BFF), Color(0xFFFF6B6B)))
                WidgetAccent.GOLD -> androidx.compose.ui.graphics.SolidColor(Color(0xFFFFD36B))
                else -> androidx.compose.ui.graphics.SolidColor(Color(WidgetPalette.accentOn(a, false, null)))
            }
            Box(Modifier.size(44.dp).clip(CircleShape)
                .then(if (on) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier)
                .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = com.noor.app.ui.components.withTick { onSelect(a) })
                .semantics { contentDescription = name }
                .padding(if (on) 6.dp else 3.dp).clip(CircleShape).background(fill))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = NoorTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * The widget as the home screen will show it, at [sizeDp] (made smaller to fit the page), on a home screen's backdrop
 * so glass and no background can be judged. Rebuilt off the main thread a moment after each change; [onLooks] hears
 * whether a colour of one's own gave way to a legible one (text, accent).
 */
@Composable
internal fun WidgetPreview(kind: WidgetKind, o: WidgetOptions, sizeDp: Pair<Int, Int>, preview: (suspend (Context, WidgetOptions) -> RemoteViews?)?,
                           onLooks: (WidgetLooks?) -> Unit = {}) {
    val context = LocalContext.current
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(o, sizeDp) {
        delay(60)
        var looks: WidgetLooks? = null
        val views = withContext(Dispatchers.Default) {
            try {
                // (Built by the page's own route whatever the preview, to know what it is drawn in.)
                val own = Widgets.previewBuilt(context, kind, o, sizeDp.first, sizeDp.second) { looks = it.design.looks }
                preview?.invoke(context, o) ?: own
            }
            catch (e: Exception) { android.util.Log.w("MinhadWidget", "preview", e); null }
        }
        onLooks(looks)
        // The home screen's views, laid out at the widget's size and drawn, here on the main thread, as a picture (the
        // countdown's seconds stand still in it; on the home screen they turn).
        picture = views?.let { v -> runCatching { draw(context, v, sizeDp) }.onFailure { android.util.Log.w("MinhadWidget", "preview draw", it) }.getOrNull() }
    }
    val wall = Brush.linearGradient(listOf(Color(0xFF2B3550), Color(0xFF6B5A6E), Color(0xFFB08670)))
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(wall).padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
            val room = maxWidth - 32.dp
            val scale = minOf(1.2f, room.value / sizeDp.first, 420f / sizeDp.second)
            Box(Modifier.width(room).height((sizeDp.second * scale).dp), contentAlignment = Alignment.Center) {
                picture?.let { bmp ->
                    androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size((sizeDp.first * scale).dp, (sizeDp.second * scale).dp).clip(RoundedCornerShape((24 * scale).dp)))
                }
            }
        }
        SectionFooter(stringResource(R.string.widget_preview_note))
    }
}

/** [views] applied, laid out at [sizeDp] and drawn into a picture. */
internal fun draw(context: Context, views: RemoteViews, sizeDp: Pair<Int, Int>): android.graphics.Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (sizeDp.first * d).toInt(); val h = (sizeDp.second * d).toInt()
    val frame = FrameLayout(context)
    frame.addView(views.apply(context, frame), FrameLayout.LayoutParams(w, h))
    frame.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
    frame.layout(0, 0, w, h)
    return android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also { frame.draw(android.graphics.Canvas(it)) }
}

private fun accentName(a: WidgetAccent) = when (a) {
    WidgetAccent.GOLD -> R.string.widget_ac_gold; WidgetAccent.TEAL -> R.string.widget_ac_teal; WidgetAccent.ROSE -> R.string.widget_ac_rose
    WidgetAccent.BLUE -> R.string.widget_ac_blue; WidgetAccent.WHITE -> R.string.widget_ac_white; WidgetAccent.YOU -> R.string.widget_ac_you
    WidgetAccent.CUSTOM -> R.string.w_custom
}
