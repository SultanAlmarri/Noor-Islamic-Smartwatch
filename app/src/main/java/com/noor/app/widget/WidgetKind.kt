package com.noor.app.widget

import com.noor.app.R

/** An option a widget's settings page can show (0.23). */
enum class WidgetOption { BACKGROUND, ACCENT, TEXT_SIZE, COUNTDOWN, SECONDS, SHURUQ, PLACE, HIJRI, JUMUAH, TAP }

/**
 * The kinds of Minhad widget (0.23). Each has its receiver ([provider], whose class name a placed widget is bound to:
 * never rename one), its name, its defaults (for a widget placed before 0.23, its 0.22 look), the options its settings
 * page shows, the size its preview is drawn at, and its builder in [WidgetRender].
 *
 * Adding a kind: a receiver class (below, `: WidgetProvider()`), an entry here, a builder in [WidgetRender.build], its
 * layouts (tools/widget/layouts.py), its `res/xml/widget_*_info.xml` and its `<receiver>` in the manifest.
 */
enum class WidgetKind(
    val provider: Class<out WidgetProvider>,
    val nameRes: Int,
    val defaults: WidgetOptions,
    val options: List<WidgetOption>,
    /** The size it is first placed at on a typical phone, in dp (the settings page's preview before it is placed). */
    val previewDp: Pair<Int, Int>,
    /** Whether it shows the prayers (and so needs a place); the Date widget does not. */
    val prayers: Boolean = true,
) {
    NEXT(NextPrayerWidget::class.java, R.string.widget_next_name, WidgetOptions(background = WidgetBackground.SKY),
        listOf(WidgetOption.BACKGROUND, WidgetOption.ACCENT, WidgetOption.TEXT_SIZE, WidgetOption.COUNTDOWN, WidgetOption.SECONDS,
            WidgetOption.PLACE, WidgetOption.JUMUAH, WidgetOption.TAP), 180 to 96),
    TODAY(TodayWidget::class.java, R.string.widget_today_name, WidgetOptions(background = WidgetBackground.SKY),
        WidgetOption.entries.toList(), 340 to 190),
    SKY(SkyWidget::class.java, R.string.widget_sky_name, WidgetOptions(background = WidgetBackground.SKY),
        listOf(WidgetOption.ACCENT, WidgetOption.TEXT_SIZE, WidgetOption.COUNTDOWN, WidgetOption.SECONDS, WidgetOption.PLACE,
            WidgetOption.HIJRI, WidgetOption.JUMUAH, WidgetOption.TAP), 340 to 190),
    RING(RingWidget::class.java, R.string.widget_ring_name, WidgetOptions(background = WidgetBackground.GLASS, opacity = 78),
        listOf(WidgetOption.BACKGROUND, WidgetOption.ACCENT, WidgetOption.TEXT_SIZE, WidgetOption.COUNTDOWN, WidgetOption.SECONDS,
            WidgetOption.JUMUAH, WidgetOption.TAP), 170 to 170),
    STRIP(StripWidget::class.java, R.string.widget_strip_name, WidgetOptions(background = WidgetBackground.GLASS, opacity = 78, shuruq = false),
        listOf(WidgetOption.BACKGROUND, WidgetOption.ACCENT, WidgetOption.TEXT_SIZE, WidgetOption.COUNTDOWN, WidgetOption.SECONDS,
            WidgetOption.SHURUQ, WidgetOption.JUMUAH, WidgetOption.TAP), 340 to 96),
    DATE(DateWidget::class.java, R.string.widget_date_name, WidgetOptions(background = WidgetBackground.GLASS, opacity = 78),
        listOf(WidgetOption.BACKGROUND, WidgetOption.ACCENT, WidgetOption.TEXT_SIZE, WidgetOption.TAP), 340 to 96, prayers = false);

    companion object {
        fun of(provider: Class<*>): WidgetKind? = entries.firstOrNull { it.provider == provider }
        fun ofClassName(name: String?): WidgetKind? = entries.firstOrNull { it.provider.name == name }
    }
}

/** The next prayer: its name and time, and a live countdown (0.9.10). */
class NextPrayerWidget : WidgetProvider()

/** Today's prayers: all six times, the next one marked, the place, the Hijri date and the countdown (0.9.10). */
class TodayWidget : WidgetProvider()

/** The home sky in small: the sky, weather and scenery of the moment, the Sun's path and the next prayer (0.9.10). */
class SkyWidget : WidgetProvider()

/** (0.23) A ring from the last prayer to the next, with the countdown and the next prayer inside it. */
class RingWidget : WidgetProvider()

/** (0.23) The day's prayers in one slim row, the next one marked, with its countdown. */
class StripWidget : WidgetProvider()

/** (0.23) The Hijri date large, the Gregorian date and weekday, and the next Islamic occasion. */
class DateWidget : WidgetProvider()
