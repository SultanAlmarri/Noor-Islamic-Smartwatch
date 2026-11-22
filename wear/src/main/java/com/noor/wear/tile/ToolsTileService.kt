package com.noor.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.argb
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.noor.prayer.HijriDate
import com.noor.prayer.WatchConfig
import com.noor.wear.Interval
import com.noor.wear.Intervals
import com.noor.wear.NoorWearApp
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor
import com.noor.wear.ui.Dest
import com.noor.wear.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Noor" tile: today's Hijri date and one tap to the Qibla, the tasbih and the calendar, with today's dhikr count.
 * Changes only with the date, so one entry per day is enough.
 */
class ToolsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val app = application as NoorWearApp
            val config = app.config.current()
            val zone = config?.zone ?: ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val count = app.tasbih.current().todayOn(LocalDate.now())
            val device = requestParams.deviceConfiguration
            // 0.25: one entry for each stretch between prayers and midnights (the top line names the next prayer).
            val now = Instant.now()
            val intervals = config?.let { Intervals.upcoming(it, now) }.orEmpty()
            val timeline = TimelineBuilders.Timeline.Builder().apply {
                for (s in ToolsTimeline.stretches(intervals, now, zone)) {
                    addTimelineEntry(entry(layout(this@ToolsTileService, device, config, s.date, if (s.date == today) count else 0, s.next), s.startMillis, s.endMillis))
                }
            }.build()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(12 * 60 * 60 * 1000L)
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { resources() }

    private fun entry(root: LayoutElement, start: Long?, end: Long): TimelineBuilders.TimelineEntry =
        TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
            .setValidity(TimelineBuilders.TimeInterval.Builder().apply { if (start != null) setStartMillis(start) }.setEndMillis(end).build())
            .build()

    private class Tool(val icon: String, val dest: String, val name: String, val tint: Int, val onTint: Int)

    companion object {
        private const val RESOURCES_VERSION = "2"
        private const val IC_QIBLA = "qibla"
        private const val IC_TASBIH = "tasbih"
        private const val IC_CALENDAR = "calendar"

        fun resources(): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
            .apply {
                for ((id, res) in listOf(IC_QIBLA to R.drawable.ic_qibla, IC_TASBIH to R.drawable.ic_tasbih, IC_CALENDAR to R.drawable.ic_calendar)) {
                    addIdToImageMapping(id, ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build()).build())
                }
            }.build()

        private fun open(context: Context, dest: String?) = clickable(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(MainActivity::class.java.name)
                    .apply { if (dest != null) addKeyToExtraMapping(MainActivity.EXTRA_DEST, ActionBuilders.AndroidStringExtra.Builder().setValue(dest).build()) }
                    .build()
            ).build(),
            id = dest ?: "home",
        )

        fun layout(base: Context, device: DeviceParameters, config: WatchConfig?, date: LocalDate, tasbihToday: Int, next: Interval? = null): LayoutElement {
            val context = base.localizedFor(config)
            val t = WearText(config)
            val h = HijriDate.of(date, config?.hijriAdjust ?: 0)
            val small = device.screenWidthDp < 210
            return materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = PrayerTileService.Scheme) {
                // 0.25: each tool on a round button of its own colour (the colours of the app's home screen); their three
                // names in one line under them in both languages, the Tasbih's with today's count.
                fun tool(x: Tool): LayoutElement = iconButton(
                    onClick = open(context, x.dest),
                    iconContent = { icon(x.icon) },
                    width = expand(), height = dp(if (small) 40f else 50f),
                    shape = shapes.full,
                    colors = ButtonColors(containerColor = x.tint.argb, iconColor = x.onTint.argb),
                    modifier = LayoutModifier.contentDescription(x.name),
                )
                val tasbih = when {
                    tasbihToday <= 0 -> context.getString(R.string.tasbih)
                    // (From a thousand on the figure alone, so the line still fits the smallest watch.)
                    tasbihToday >= 1000 -> t.count(tasbihToday.toLong())
                    else -> context.getString(R.string.tile_tasbih_count, t.digits(tasbihToday.toString()))
                }
                val tools = listOf(
                    Tool(IC_QIBLA, Dest.QIBLA, context.getString(R.string.qibla), PrayerTileService.GOLD, PrayerTileService.ON_GOLD),
                    Tool(IC_TASBIH, Dest.TASBIH, tasbih, 0xFF34C96B.toInt(), 0xFF05230F.toInt()),
                    Tool(IC_CALENDAR, Dest.CALENDAR, context.getString(R.string.calendar_short), 0xFF6E6BF2.toInt(), 0xFFFFFFFF.toInt()),
                )
                primaryLayout(
                    // The top line is the next prayer and its time (the app's name before anything is set up).
                    titleSlot = {
                        PrayerTileService.label(
                            if (config != null && next != null) context.getString(R.string.tile_next, t.name(next.next), t.time(next.end, config.zone)) else context.getString(R.string.app_name),
                            14f, PrayerTileService.GOLD, LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    },
                    mainSlot = {
                        column(
                            PrayerTileService.label(t.digits(h.day.toString()) + " " + (if (t.arabic) h.monthNameAr else h.monthNameEn),
                                if (small) 17f else 20f, PrayerTileService.INK, LayoutElementBuilders.FONT_WEIGHT_BOLD),
                            spacer(height = dp(if (small) 5f else 8f)),
                            buttonGroup {
                                // Read in the language's direction: Qibla first, on the right in Arabic.
                                for (x in if (t.arabic) tools.reversed() else tools) buttonGroupItem { tool(x) }
                            },
                            spacer(height = dp(4f)),
                            // (Written in reading order: in Arabic the line itself runs from the right, like the buttons.)
                            PrayerTileService.label(tools.joinToString("  ·  ") { it.name }, if (small) 11f else 12f, PrayerTileService.QUIET),
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(onClick = open(context, null)) {
                            PrayerTileService.label(context.getString(R.string.tile_all_times), 15f, PrayerTileService.ON_GOLD, LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        }
                    },
                )
            }
        }
    }
}
