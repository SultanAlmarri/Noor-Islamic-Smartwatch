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
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
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

/**
 * "Prayer times" tile: the next prayer in gold with a live countdown, and the two after it. Switches at each prayer by
 * itself (one timeline entry per interval, two days ahead).
 */
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
                            text(countdown(context, t, config, iv), typography = Typography.LABEL_MEDIUM, color = colorScheme.primary),
                            spacer(height = dp(4f)),
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

        /**
         * "in 1 h 23 min", kept current by the watch itself every minute (a dynamic expression), so the tile never
         * shows a stale countdown. Rounded up like the app. With Arabic-Indic digits chosen, the watch's renderer
         * would format numbers its own way, so the line then says only "Next" in the chosen language.
         */
        internal fun countdown(context: Context, t: WearText, config: WatchConfig, iv: Interval): LayoutString {
            val fallback = context.getString(R.string.next)
            if (config.arabicDigits) return LayoutString(fallback)
            val until = DynamicInstant.platformTimeWithSecondsPrecision().durationUntil(DynamicInstant.withSecondsPrecision(iv.end))
            val minutes = until.toIntSeconds().plus(59).div(60)
            val h = minutes.div(60); val m = minutes.rem(60)
            val (hu, mu, pre) = if (t.arabic) Triple(" س ", " د", "بعد ") else Triple(" h ", " min", "in ")
            val withHours = DynamicString.constant(pre).concat(h.format()).concat(DynamicString.constant(hu)).concat(m.format()).concat(DynamicString.constant(mu))
            val onlyMinutes = DynamicString.constant(pre).concat(m.format()).concat(DynamicString.constant(mu))
            // Past the time (the tile not yet switched to the next entry): "Now", never a negative countdown.
            val dynamic = DynamicString.onCondition(minutes.lt(1)).use(DynamicString.constant(context.getString(R.string.now)))
                .elseUse(DynamicString.onCondition(h.gt(0)).use(withHours).elseUse(onlyMinutes))
            return LayoutString(fallback, dynamic, StringLayoutConstraint.Builder(pre + "88" + hu + "88" + mu).build())
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
