package com.noor.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Prayer
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import com.noor.wear.Interval
import com.noor.wear.Intervals
import com.noor.wear.NoorWearApp
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * "Next prayer" for any watch face: name, icon and a countdown that the watch face itself keeps current.
 * One request returns two days of entries, so the switch at each prayer happens on time with the app asleep.
 */
class NextPrayerComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val config = (application as NoorWearApp).config.currentOrPull()
            ?: return ComplicationDataTimeline(NoDataComplicationData(), emptyList())
        val now = Instant.now()
        val intervals = Intervals.upcoming(config, now)
        if (intervals.isEmpty()) return ComplicationDataTimeline(NoDataComplicationData(), emptyList())
        val build = ComplicationBuilder(this, config)
        val current = intervals.first()
        val default = build.data(request.complicationType, current, now) ?: return null
        val entries = intervals.drop(1).mapNotNull { iv ->
            build.data(request.complicationType, iv, iv.start)?.let { TimelineEntry(TimeInterval(iv.start, iv.end), it) }
        }
        return ComplicationDataTimeline(default, entries)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = WatchConfig(
            GeoPoint(25.2048, 55.2708), "Asia/Dubai", "Dubai", "دبي", false, Profiles.DUBAI_IACAD.id, null,
            HighLatitudeRule.ANGLE_BASED, emptyMap(), 0, "", false, false, 0,
        )
        val now = Instant.now()
        val iv = Intervals.upcoming(sample, now).firstOrNull() ?: return null
        return ComplicationBuilder(this, sample).data(type, iv, now)
    }
}

/** Builds the data for one interval in the shape the watch face asked for. */
internal class ComplicationBuilder(private val context: Context, private val config: WatchConfig) {
    private val text = WearText(config)

    private val tap: PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun icon(p: Prayer) = MonochromaticImage.Builder(Icon.createWithResource(context, iconRes(p))).build()

    private fun countdown(iv: Interval, template: String? = null): ComplicationText =
        TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountDownTimeReference(iv.end))
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .apply { if (template != null) setText(template) }
            .build()

    private fun description(iv: Interval) =
        PlainComplicationText.Builder(text.name(iv.next.prayer) + " " + text.time(iv.end, config.zone)).build()

    fun data(type: ComplicationType, iv: Interval, at: Instant): ComplicationData? {
        val name = text.name(iv.next.prayer)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(countdown(iv), description(iv))
                .setTitle(PlainComplicationText.Builder(name).build())
                .setMonochromaticImage(icon(iv.next.prayer))
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                // "^1" is replaced by the live countdown, drawn by the watch face.
                countdown(iv, "$name ${text.time(iv.end, config.zone)} · ^1"), description(iv),
            )
                .setTitle(PlainComplicationText.Builder(text.place(config)).build())
                .setMonochromaticImage(icon(iv.next.prayer))
                .setTapAction(tap)
                .build()

            ComplicationType.RANGED_VALUE -> {
                // Fills as the interval passes; the watch evaluates the value itself, every second if it wants.
                val total = iv.length.seconds.toFloat().coerceAtLeast(1f)
                val elapsedNow = Duration.between(iv.start, at).seconds.toFloat().coerceIn(0f, total)
                val dynamic = DynamicInstant.withSecondsPrecision(iv.start)
                    .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
                    .toIntSeconds().asFloat()
                RangedValueComplicationData.Builder(dynamic, elapsedNow, 0f, total, description(iv))
                    .setText(countdown(iv))
                    .setTitle(PlainComplicationText.Builder(name).build())
                    .setMonochromaticImage(icon(iv.next.prayer))
                    .setTapAction(tap)
                    .build()
            }

            else -> null
        }
    }

    companion object {
        fun iconRes(p: Prayer): Int = when (p) {
            Prayer.FAJR -> R.drawable.ic_prayer_fajr
            Prayer.SHURUQ -> R.drawable.ic_prayer_sunrise
            Prayer.DHUHR -> R.drawable.ic_prayer_dhuhr
            Prayer.ASR -> R.drawable.ic_prayer_asr
            Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
            Prayer.ISHA -> R.drawable.ic_prayer_isha
        }
    }
}
