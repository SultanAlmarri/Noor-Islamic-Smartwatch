package com.noor.app.widget

import android.content.Context

/** What is behind a widget's content (0.23; 0.24 more). */
enum class WidgetBackground {
    /** The sky ([WidgetOptions.skyLight]: of the moment, or at a light chosen), a smooth gradient. */
    SKY,
    /** Translucent ink with a soft light at its top edge, as the app's glass cards. */
    GLASS,
    DARK,
    LIGHT,
    /** The wallpaper's colours (Android 12 and later; elsewhere [DARK]). */
    YOU,
    /** Nothing: the wallpaper itself. */
    CLEAR,
    /** (0.24) One colour: [WidgetOptions.color1]. */
    SOLID,
    /** (0.24) A gradient from [WidgetOptions.color1] at the top to [WidgetOptions.color2]. */
    GRADIENT,
    /** (0.24) Dark or light as the phone is. */
    AUTO,
}

/** (0.24) The sky behind a widget: of the moment (it follows the time of day), or always at one light. */
enum class SkyLight { LIVE, DAWN, DAY, GOLDEN, NIGHT }

/** The colour of the prayer counted to, the marks and the rings (0.23; 0.24 a colour of one's own). */
enum class WidgetAccent { GOLD, TEAL, ROSE, BLUE, WHITE, YOU, CUSTOM }

/** The text's size, over the system's font size (0.23; 0.24 [WidgetOptions.textScale] when set). */
enum class WidgetTextSize(val scale: Float) { SMALL(0.88f), NORMAL(1f), LARGE(1.12f) }

/** Where a tap on the widget leads (0.23). */
enum class WidgetTap { HOME, QIBLA, ADHAN }

/** (0.24) The text's colour: chosen for the background, white, black, or one's own (kept legible: [WidgetLooks]). */
enum class TextColor { AUTO, WHITE, BLACK, CUSTOM }

/** (0.24) The prayers' signs: in their own colours, in the text's, or none. */
enum class IconStyle { COLOR, MONO, OFF }

/** (0.24) Where the text stands, where a widget can choose. */
enum class WidgetAlign { START, CENTER }

/** (0.24) The large number: the countdown, or the Adhan's time (the countdown then small). */
enum class MainNumber { COUNTDOWN, ADHAN }

/** (0.24) The countdown as a timer ("−1:04:59") or in words ("in 1 h 4 min"). */
enum class CountStyle { TIMER, WORDS }

/** (0.24) The place: the area and its city, the area alone, or none. */
enum class PlaceMode { FULL, AREA, OFF }

/** (0.24) As in the app, or this widget's own. */
enum class TimeChoice { APP, H12, H24 }
enum class DigitChoice { APP, WESTERN, ARABIC_INDIC }
enum class LanguageChoice { APP, ENGLISH, ARABIC }

/** (0.24) The day's prayers already past: quieter, as the others, or not shown. */
enum class PastStyle { DIM, NORMAL, HIDE }

/** (0.24) The prayer counted to in lists: on a capsule, in an outline, or in the accent's colour alone. */
enum class HighlightStyle { FILLED, OUTLINE, COLOR }

/** (0.24) The order of a row of prayers: as the language reads (Arabic: Fajr on the right), or Fajr on the left always. */
enum class RowOrder { LANGUAGE, FAJR_LEFT }

/** (0.24) Shuruq among the prayers: where there is room, always, or never. */
enum class ShuruqChoice { AUTO, SHOW, HIDE }

/** (0.24) Just after the Adhan: the time since it (as the app), or straight to the next prayer. */
enum class AfterAdhan { SINCE, NEXT }

/**
 * One widget's options (0.23; 0.24 everything the owner asked to customise), chosen in [WidgetConfigActivity] and kept
 * per widget id ([WidgetOptionsStore]). Every widget has every option; a kind shows in its settings only those that
 * change it ([WidgetKind.options]). Colours are ARGB.
 */
data class WidgetOptions(
    val background: WidgetBackground = WidgetBackground.SKY,
    /** 0..100, the background's opacity. */
    val opacity: Int = 100,
    val accent: WidgetAccent = WidgetAccent.GOLD,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    /** The countdown's seconds (otherwise hours and minutes, changing each minute). */
    val seconds: Boolean = true,
    /** The countdown (otherwise only the prayer and its time). */
    val countdown: Boolean = true,
    val shuruq: Boolean = true,
    /** (0.23) The place line; 0.24 [placeMode] says how much of it. */
    val place: Boolean = true,
    /** The Hijri date, where the widget has an information line. */
    val hijri: Boolean = false,
    /** Friday's Dhuhr named Jumu'ah, as in the app. */
    val jumuah: Boolean = true,
    val tap: WidgetTap = WidgetTap.HOME,
    // ---- 0.24 ----
    val skyLight: SkyLight = SkyLight.LIVE,
    val color1: Int = 0xFF2F7FD8.toInt(),
    val color2: Int = 0xFF8A6AA0.toInt(),
    val textColor: TextColor = TextColor.AUTO,
    val textCustom: Int = 0xFFFFFFFF.toInt(),
    val accentCustom: Int = 0xFFFFD36B.toInt(),
    val icons: IconStyle = IconStyle.COLOR,
    /** The text's size in percent (80..130), or 0 for [textSize]'s. */
    val textScale: Int = 0,
    val align: WidgetAlign = WidgetAlign.START,
    val main: MainNumber = MainNumber.COUNTDOWN,
    val showIcon: Boolean = true,
    val showName: Boolean = true,
    /** The Adhan's time beside the name. */
    val showTime: Boolean = true,
    val showBar: Boolean = true,
    val placeMode: PlaceMode = PlaceMode.FULL,
    val gregorian: Boolean = false,
    /** The prayers after the one counted to, where the widget lists them. */
    val showLater: Boolean = true,
    val countStyle: CountStyle = CountStyle.TIMER,
    val timeFormat: TimeChoice = TimeChoice.APP,
    val ampm: Boolean = true,
    val digits: DigitChoice = DigitChoice.APP,
    val language: LanguageChoice = LanguageChoice.APP,
    val past: PastStyle = PastStyle.DIM,
    val highlight: HighlightStyle = HighlightStyle.FILLED,
    val after: AfterAdhan = AfterAdhan.SINCE,
    val rowOrder: RowOrder = RowOrder.LANGUAGE,
    /** Shuruq: [shuruq] false (0.23) reads as [ShuruqChoice.HIDE]. */
    val shuruqChoice: ShuruqChoice = ShuruqChoice.AUTO,
) {
    /** Shuruq as chosen (a 0.23 "off" is "never"). */
    val shuruqShown: ShuruqChoice get() = if (!shuruq) ShuruqChoice.HIDE else shuruqChoice

    /** The text's scale chosen (1 = as designed). */
    val textScaleFactor: Float get() = if (textScale in 80..130) textScale / 100f else textSize.scale

    /** The place as shown: none when the place is off. */
    val placeShown: PlaceMode get() = if (!place) PlaceMode.OFF else placeMode

    /** The options as one line of text, to be kept ([decode] reads it back; unknown or missing values keep [base]'s). */
    fun encode(): String = listOf(
        "bg=${background.name}", "op=$opacity", "ac=${accent.name}", "ts=${textSize.name}", "sec=$seconds", "cd=$countdown",
        "sh=$shuruq", "pl=$place", "hi=$hijri", "ju=$jumuah", "tap=${tap.name}",
        "sl=${skyLight.name}", "c1=${hex(color1)}", "c2=${hex(color2)}", "tc=${textColor.name}", "tx=${hex(textCustom)}", "ax=${hex(accentCustom)}",
        "ic=${icons.name}", "tsp=$textScale", "al=${align.name}", "mn=${main.name}", "si=$showIcon", "sn=$showName", "st=$showTime", "sb=$showBar",
        "pm=${placeMode.name}", "gr=$gregorian", "sl2=$showLater", "cs=${countStyle.name}", "tf=${timeFormat.name}", "ap=$ampm", "dg=${digits.name}",
        "lg=${language.name}", "pa=${past.name}", "hl=${highlight.name}", "af=${after.name}",
        "ro=${rowOrder.name}", "sc=${shuruqChoice.name}",
    ).joinToString(";")

    companion object {
        private fun hex(c: Int) = "%08X".format(c)

        fun decode(text: String?, base: WidgetOptions): WidgetOptions {
            if (text.isNullOrBlank()) return base
            val m = text.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun bool(k: String, d: Boolean) = m[k]?.toBooleanStrictOrNull() ?: d
            fun int(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
            fun color(k: String, d: Int) = m[k]?.toLongOrNull(16)?.toInt() ?: d
            fun <E : Enum<E>> enum(k: String, values: Array<E>, d: E) = m[k]?.let { v -> values.firstOrNull { it.name == v } } ?: d
            return WidgetOptions(
                background = enum("bg", WidgetBackground.values(), base.background),
                opacity = int("op", base.opacity).coerceIn(0, 100),
                accent = enum("ac", WidgetAccent.values(), base.accent),
                textSize = enum("ts", WidgetTextSize.values(), base.textSize),
                seconds = bool("sec", base.seconds), countdown = bool("cd", base.countdown), shuruq = bool("sh", base.shuruq),
                place = bool("pl", base.place), hijri = bool("hi", base.hijri), jumuah = bool("ju", base.jumuah),
                tap = enum("tap", WidgetTap.values(), base.tap),
                skyLight = enum("sl", SkyLight.values(), base.skyLight),
                color1 = color("c1", base.color1), color2 = color("c2", base.color2),
                textColor = enum("tc", TextColor.values(), base.textColor), textCustom = color("tx", base.textCustom),
                accentCustom = color("ax", base.accentCustom), icons = enum("ic", IconStyle.values(), base.icons),
                textScale = int("tsp", base.textScale).let { if (it == 0 || it in 80..130) it else base.textScale },
                align = enum("al", WidgetAlign.values(), base.align), main = enum("mn", MainNumber.values(), base.main),
                showIcon = bool("si", base.showIcon), showName = bool("sn", base.showName), showTime = bool("st", base.showTime),
                showBar = bool("sb", base.showBar), placeMode = enum("pm", PlaceMode.values(), base.placeMode), gregorian = bool("gr", base.gregorian),
                showLater = bool("sl2", base.showLater), countStyle = enum("cs", CountStyle.values(), base.countStyle),
                timeFormat = enum("tf", TimeChoice.values(), base.timeFormat), ampm = bool("ap", base.ampm),
                digits = enum("dg", DigitChoice.values(), base.digits), language = enum("lg", LanguageChoice.values(), base.language),
                past = enum("pa", PastStyle.values(), base.past), highlight = enum("hl", HighlightStyle.values(), base.highlight),
                after = enum("af", AfterAdhan.values(), base.after),
                rowOrder = enum("ro", RowOrder.values(), base.rowOrder), shuruqChoice = enum("sc", ShuruqChoice.values(), base.shuruqChoice),
            )
        }
    }
}

/**
 * The widgets' options, one entry per widget id, in the app's own preferences (0.23). A widget never configured reads
 * the defaults for new widgets (0.24: Settings → Widgets; [defaults]), and those its kind's ([WidgetKind.defaults]).
 *
 * Programmatic use (a test, or a debug host that places widgets): `WidgetOptionsStore.save(context, id, options)` and
 * then `Widgets.refreshAsync(context)` (or `Widgets.refresh` from a coroutine).
 */
object WidgetOptionsStore {
    private const val PREFS = "minhad_widgets"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(id: Int) = "w.$id"
    private fun defaultsKey(kind: WidgetKind) = "defaults.${kind.name}"

    fun load(context: Context, id: Int, kind: WidgetKind): WidgetOptions = WidgetOptions.decode(prefs(context).getString(key(id), null), defaults(context, kind))

    fun has(context: Context, id: Int): Boolean = prefs(context).contains(key(id))

    fun save(context: Context, id: Int, options: WidgetOptions) { prefs(context).edit().putString(key(id), options.encode()).commit() }

    fun delete(context: Context, ids: IntArray) { prefs(context).edit().apply { for (id in ids) remove(key(id)) }.commit() }

    /** (0.24) The options a new widget of [kind] starts with. */
    fun defaults(context: Context, kind: WidgetKind): WidgetOptions = WidgetOptions.decode(prefs(context).getString(defaultsKey(kind), null), kind.defaults)

    fun saveDefaults(context: Context, kind: WidgetKind, options: WidgetOptions) { prefs(context).edit().putString(defaultsKey(kind), options.encode()).commit() }

    /** (0.24) Back to the kind's own defaults (for new widgets). */
    fun resetDefaults(context: Context, kind: WidgetKind) { prefs(context).edit().remove(defaultsKey(kind)).commit() }

    /** After a restore from a backup, the launcher gives the widgets new ids: their options follow them. */
    fun move(context: Context, from: IntArray, to: IntArray) {
        val p = prefs(context)
        val saved = from.map { p.getString(key(it), null) }
        p.edit().apply {
            for (id in from) remove(key(id))
            for ((i, id) in to.withIndex()) saved.getOrNull(i)?.let { putString(key(id), it) }
        }.commit()
    }
}
