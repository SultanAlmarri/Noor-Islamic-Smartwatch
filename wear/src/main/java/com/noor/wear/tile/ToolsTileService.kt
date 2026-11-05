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
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
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
                fun tool(id: String, dest: String, label: Int) = iconButton(
                    onClick = open(context, dest),
                    iconContent = { icon(id) },
                    width = expand(), height = dp(52f),
                    colors = filledTonalButtonColors(),
                    modifier = LayoutModifier.contentDescription(context.getString(label)),
                )
                primaryLayout(
                    titleSlot = { text(context.getString(R.string.app_name).layoutString, color = colorScheme.primary) },
                    mainSlot = {
                        column(
                            text((t.digits(h.day.toString()) + " " + (if (t.arabic) h.monthNameAr else h.monthNameEn)).layoutString,
                                typography = Typography.TITLE_MEDIUM, color = colorScheme.onSurface),
                            spacer(height = dp(8f)),
                            buttonGroup {
                                // Read in the language's direction: Qibla first, on the right in Arabic.
                                val tools = listOf(Triple(IC_QIBLA, Dest.QIBLA, R.string.qibla), Triple(IC_TASBIH, Dest.TASBIH, R.string.tasbih),
                                    Triple(IC_CALENDAR, Dest.CALENDAR, R.string.calendar))
                                for ((id, dest, label) in if (t.arabic) tools.reversed() else tools) buttonGroupItem { tool(id, dest, label) }
                            },
                            spacer(height = dp(6f)),
                            text(
                                (if (tasbihToday > 0) context.getString(R.string.tasbih_today, t.digits(tasbihToday.toString())) else context.getString(R.string.tile_tools_hint)).layoutString,
                                typography = Typography.BODY_SMALL, color = colorScheme.onSurfaceVariant,
                            ),
                        )
                    },
                    bottomSlot = { textEdgeButton(onClick = open(context, null)) { text(context.getString(R.string.tile_open_times).layoutString) } },
                )
            }
        }
    }
}
