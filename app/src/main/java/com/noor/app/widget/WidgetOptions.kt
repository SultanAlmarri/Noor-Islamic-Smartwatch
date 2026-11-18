package com.noor.app.widget

import android.content.Context

/** What is behind a widget's content (0.23). */
enum class WidgetBackground {
    /** The home sky of the moment ([WidgetSky]), with a scrim for reading. */
    SKY,
    /** Translucent ink with a soft light at its top edge, as the app's glass cards. */
    GLASS,
    DARK,
    LIGHT,
    /** The wallpaper's colours (Android 12 and later; elsewhere [DARK]). */
    YOU,
    /** Nothing: the wallpaper itself, the text with a shadow. */
    CLEAR,
}

/** The colour of the prayer counted to, the marks and the rings (0.23). */
enum class WidgetAccent { GOLD, TEAL, ROSE, BLUE, WHITE, YOU }

/** The text's size, over the system's font size (0.23). */
enum class WidgetTextSize(val scale: Float) { SMALL(0.88f), NORMAL(1f), LARGE(1.12f) }

/** Where a tap on the widget leads (0.23). */
enum class WidgetTap { HOME, QIBLA, ADHAN }

/**
 * One widget's options (0.23), chosen in [WidgetConfigActivity] and kept per widget id ([WidgetOptionsStore]). Every
 * widget has every option; a kind shows in its settings only those that change it ([WidgetKind.options]).
 */
data class WidgetOptions(
    val background: WidgetBackground = WidgetBackground.SKY,
    /** 0..100, for [WidgetBackground.GLASS], [WidgetBackground.DARK] and [WidgetBackground.LIGHT]. */
    val opacity: Int = 70,
    val accent: WidgetAccent = WidgetAccent.GOLD,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    /** The countdown's seconds (otherwise hours and minutes, changing each minute). */
    val seconds: Boolean = true,
    /** The countdown (otherwise only the prayer and its time). */
    val countdown: Boolean = true,
    val shuruq: Boolean = true,
    val place: Boolean = true,
    val hijri: Boolean = true,
    /** Friday's Dhuhr named Jumu'ah, as in the app. */
    val jumuah: Boolean = true,
    val tap: WidgetTap = WidgetTap.HOME,
) {
    /** The options as one line of text, to be kept ([decode] reads it back; unknown or missing values keep [base]'s). */
    fun encode(): String = listOf(
        "bg=${background.name}", "op=$opacity", "ac=${accent.name}", "ts=${textSize.name}", "sec=$seconds", "cd=$countdown",
        "sh=$shuruq", "pl=$place", "hi=$hijri", "ju=$jumuah", "tap=${tap.name}",
    ).joinToString(";")

    companion object {
        fun decode(text: String?, base: WidgetOptions): WidgetOptions {
            if (text.isNullOrBlank()) return base
            val m = text.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun bool(k: String, d: Boolean) = m[k]?.toBooleanStrictOrNull() ?: d
            return WidgetOptions(
                background = m["bg"]?.let { v -> WidgetBackground.entries.firstOrNull { it.name == v } } ?: base.background,
                opacity = m["op"]?.toIntOrNull()?.coerceIn(0, 100) ?: base.opacity,
                accent = m["ac"]?.let { v -> WidgetAccent.entries.firstOrNull { it.name == v } } ?: base.accent,
                textSize = m["ts"]?.let { v -> WidgetTextSize.entries.firstOrNull { it.name == v } } ?: base.textSize,
                seconds = bool("sec", base.seconds), countdown = bool("cd", base.countdown), shuruq = bool("sh", base.shuruq),
                place = bool("pl", base.place), hijri = bool("hi", base.hijri), jumuah = bool("ju", base.jumuah),
                tap = m["tap"]?.let { v -> WidgetTap.entries.firstOrNull { it.name == v } } ?: base.tap,
            )
        }
    }
}

/**
 * The widgets' options, one entry per widget id, in the app's own preferences (0.23). A widget never configured reads
 * its kind's defaults ([WidgetKind.defaults]), which are 0.22's look: widgets placed before 0.23 keep it.
 *
 * Programmatic use (a test, or a debug host that places widgets): `WidgetOptionsStore.save(context, id, options)` and
 * then `Widgets.refreshAsync(context)` (or `Widgets.refresh` from a coroutine).
 */
object WidgetOptionsStore {
    private const val PREFS = "minhad_widgets"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(id: Int) = "w.$id"

    fun load(context: Context, id: Int, kind: WidgetKind): WidgetOptions = WidgetOptions.decode(prefs(context).getString(key(id), null), kind.defaults)

    fun has(context: Context, id: Int): Boolean = prefs(context).contains(key(id))

    fun save(context: Context, id: Int, options: WidgetOptions) { prefs(context).edit().putString(key(id), options.encode()).commit() }

    fun delete(context: Context, ids: IntArray) { prefs(context).edit().apply { for (id in ids) remove(key(id)) }.commit() }

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
