package com.noor.wear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.noor.prayer.WatchConfig
import com.noor.wear.Interval
import com.noor.wear.Intervals
import com.noor.wear.NoorWearApp
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor
import com.noor.wear.ui.MainActivity
import java.time.Instant

/** "Prayer times" tile: the next prayer in gold and the two after it. Switches at each prayer by itself. */
class PrayerTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val config = (application as NoorWearApp).config.currentOrPull()
            val device = requestParams.deviceConfiguration
            val timeline = TimelineBuilders.Timeline.Builder()
            if (config == null) {
                timeline.addTimelineEntry(entry(setupLayout(this@PrayerTileService, device), null))
            } else {
                val all = Intervals.upcoming(config, Instant.now())
                all.take(14).forEachIndexed { i, iv ->
                    val following = all.drop(i + 1).take(2)
                    timeline.addTimelineEntry(entry(prayerLayout(this@PrayerTileService, device, config, iv, following), iv))
                }
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline.build())
                // Two days of entries are included; this only extends them.
                .setFreshnessIntervalMillis(6 * 60 * 60 * 1000L)
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private fun entry(root: LayoutElement, iv: Interval?): TimelineBuilders.TimelineEntry =
        TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
            .apply {
                if (iv != null) setValidity(
                    TimelineBuilders.TimeInterval.Builder().setStartMillis(iv.start.toEpochMilli()).setEndMillis(iv.end.toEpochMilli()).build()
                )
            }
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"

        val Scheme = ColorScheme(
            primary = 0xFFE3C27E.toInt().argb,
            onPrimary = 0xFF241A08.toInt().argb,
            primaryContainer = 0xFF3A2E17.toInt().argb,
            onPrimaryContainer = 0xFFF4E2B8.toInt().argb,
            surfaceContainer = 0xFF16233A.toInt().argb,
            onSurface = 0xFFF7F2E7.toInt().argb,
            onSurfaceVariant = 0xFFB7C0D2.toInt().argb,
            background = 0xFF000000.toInt().argb,
        )

        private fun openApp(context: Context) = clickable(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()
        )

        private fun scope(context: Context, device: DeviceParameters, layout: MaterialScope.() -> LayoutElement) =
            materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = Scheme, layout = layout)

        fun prayerLayout(base: Context, device: DeviceParameters, config: WatchConfig, iv: Interval, following: List<Interval>): LayoutElement {
            val context = base.localizedFor(config)
            val t = WearText(config)
            val zone = config.zone
            return scope(context, device) {
                primaryLayout(
                    titleSlot = { text(t.placeWithFlag(config).layoutString, color = colorScheme.onSurfaceVariant) },
                    mainSlot = {
                        column(
                            text(context.getString(R.string.next).uppercase().layoutString, typography = Typography.LABEL_SMALL, color = colorScheme.onSurfaceVariant),
                            text(t.name(iv.next.prayer).layoutString, typography = Typography.DISPLAY_SMALL, color = colorScheme.primary),
                            text(t.time(iv.end, zone).layoutString, typography = Typography.TITLE_LARGE, color = colorScheme.onSurface),
                            spacer(height = dp(6f)),
                            text(
                                following.joinToString("   ") { t.name(it.next.prayer) + " " + t.timeShort(it.end, zone) }.layoutString,
                                typography = Typography.BODY_SMALL, color = colorScheme.onSurfaceVariant, maxLines = 2,
                            ),
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(onClick = openApp(context)) { text(context.getString(R.string.today).layoutString) }
                    },
                )
            }
        }

        fun setupLayout(context: Context, device: DeviceParameters): LayoutElement = scope(context, device) {
            primaryLayout(
                titleSlot = { text(context.getString(R.string.app_name).layoutString, color = colorScheme.primary) },
                mainSlot = {
                    text(context.getString(R.string.setup_title).layoutString, typography = Typography.TITLE_MEDIUM, maxLines = 3)
                },
                bottomSlot = {
                    textEdgeButton(onClick = openApp(context)) { text(context.getString(R.string.open).layoutString) }
                },
            )
        }
    }
}
