package com.noor.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
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
import com.noor.wear.NoorWearApp
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor
import com.noor.wear.ui.Dest
import com.noor.wear.ui.MainActivity
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
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(entry(layout(this@ToolsTileService, device, config, today, count), null, midnight))
                .addTimelineEntry(entry(layout(this@ToolsTileService, device, config, today.plusDays(1), 0), midnight, midnight + 86_400_000L))
                .build()
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

    private class Tool(val icon: String, val dest: String, val label: Int, val tint: Int, val onTint: Int)

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

        fun layout(base: Context, device: DeviceParameters, config: WatchConfig?, date: LocalDate, tasbihToday: Int): LayoutElement {
            val context = base.localizedFor(config)
            val t = WearText(config)
            val h = HijriDate.of(date, config?.hijriAdjust ?: 0)
            return materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = PrayerTileService.Scheme) {
                // 0.25: each tool on a round button of its own colour, the colours of the app's home screen.
                fun tool(id: String, dest: String, label: Int, tint: Int, onTint: Int) = iconButton(
                    onClick = open(context, dest),
                    iconContent = { icon(id) },
                    width = expand(), height = dp(54f),
                    shape = shapes.full,
                    colors = ButtonColors(containerColor = tint.argb, iconColor = onTint.argb),
                    modifier = LayoutModifier.contentDescription(context.getString(label)),
                )
                primaryLayout(
                    titleSlot = { PrayerTileService.label(context.getString(R.string.app_name), 14f, PrayerTileService.GOLD, LayoutElementBuilders.FONT_WEIGHT_BOLD) },
                    mainSlot = {
                        column(
                            PrayerTileService.label(t.digits(h.day.toString()) + " " + (if (t.arabic) h.monthNameAr else h.monthNameEn),
                                20f, PrayerTileService.INK, LayoutElementBuilders.FONT_WEIGHT_BOLD),
                            spacer(height = dp(8f)),
                            buttonGroup {
                                // Read in the language's direction: Qibla first, on the right in Arabic.
                                val white = 0xFFFFFFFF.toInt()
                                val tools = listOf(
                                    Tool(IC_QIBLA, Dest.QIBLA, R.string.qibla, PrayerTileService.GOLD, PrayerTileService.ON_GOLD),
                                    Tool(IC_TASBIH, Dest.TASBIH, R.string.tasbih, 0xFF34C96B.toInt(), 0xFF05230F.toInt()),
                                    Tool(IC_CALENDAR, Dest.CALENDAR, R.string.calendar, 0xFF6E6BF2.toInt(), white),
                                )
                                for (x in if (t.arabic) tools.reversed() else tools) buttonGroupItem { tool(x.icon, x.dest, x.label, x.tint, x.onTint) }
                            },
                            spacer(height = dp(6f)),
                            PrayerTileService.label(
                                if (tasbihToday > 0) context.getString(R.string.tasbih_today, t.digits(tasbihToday.toString())) else context.getString(R.string.tile_tools_hint),
                                13f, PrayerTileService.QUIET,
                            ),
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(onClick = open(context, null)) {
                            PrayerTileService.label(context.getString(R.string.tile_open_times), 15f, PrayerTileService.ON_GOLD, LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        }
                    },
                )
            }
        }
    }
}
