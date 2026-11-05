package com.noor.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.noor.prayer.HijriDate
import com.noor.prayer.WatchConfig
import com.noor.prayer.qibla.Qibla
import com.noor.wear.NoorWearApp
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.localizedFor
import com.noor.wear.ui.Dest
import com.noor.wear.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId

private fun open(context: Context, dest: String, code: Int): PendingIntent = PendingIntent.getActivity(
    context, code, MainActivity.intent(context, dest), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun text(s: String) = PlainComplicationText.Builder(s).build()
private fun icon(context: Context, res: Int) = MonochromaticImage.Builder(Icon.createWithResource(context, res)).build()

/** The day's midnights from [today], for timelines that change with the date. */
private fun midnights(today: LocalDate, zone: ZoneId, days: Int) = (0..days).map { today.plusDays(it.toLong()).atStartOfDay(zone).toInstant() }

/**
 * Today's Hijri date for any watch face, in the language and digits chosen for Noor, with the user's Hijri adjustment.
 * Two days of entries switch at midnight on their own.
 */
class HijriComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val config = (application as NoorWearApp).config.currentOrPull()
        val zone = config?.zone ?: ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val m = midnights(today, zone, 3)
        val first = HijriData.data(this, config, request.complicationType, today) ?: return null
        val entries = (1..2).mapNotNull { i ->
            HijriData.data(this, config, request.complicationType, today.plusDays(i.toLong()))?.let { TimelineEntry(TimeInterval(m[i], m[i + 1]), it) }
        }
        return ComplicationDataTimeline(first, entries)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = HijriData.data(this, null, type, LocalDate.of(2026, 11, 5))
}

internal object HijriData {
    private val SHORT_EN = listOf("Muh", "Saf", "Rab I", "Rab II", "Jum I", "Jum II", "Raj", "Sha'b", "Ram", "Shaw", "Dhu Q", "Dhu H")
    private val SHORT_AR = listOf("محرم", "صفر", "ربيع ١", "ربيع ٢", "جمادى ١", "جمادى ٢", "رجب", "شعبان", "رمضان", "شوال", "القعدة", "الحجة")

    fun shortMonth(h: HijriDate, arabic: Boolean): String = (if (arabic) SHORT_AR else SHORT_EN)[h.month - 1]

    fun data(context: Context, config: WatchConfig?, type: ComplicationType, date: LocalDate): ComplicationData? {
        val t = WearText(config)
        val h = HijriDate.of(date, config?.hijriAdjust ?: 0)
        val ctx = context.localizedFor(config)
        val tap = open(context, Dest.CALENDAR, 21)
        val described = text(t.hijri(h))
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(t.digits(h.day.toString())), described)
                .setTitle(text(shortMonth(h, t.arabic)))
                .setMonochromaticImage(icon(context, R.drawable.ic_calendar))
                .setTapAction(tap).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text(t.hijri(h) + if (t.arabic) " هـ" else " AH"), described)
                .setTitle(text(ctx.getString(R.string.hijri_date)))
                .setMonochromaticImage(icon(context, R.drawable.ic_calendar))
                .setTapAction(tap).build()
            else -> null
        }
    }
}

/** Today's dhikr count, one tap from the counter. */
class TasbihComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val app = application as NoorWearApp
        val config = app.config.current()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val count = app.tasbih.current().todayOn(today)
        val m = midnights(today, zone, 2)
        val first = data(this, config, request.complicationType, count) ?: return null
        // The day total starts again at midnight even if the app is not opened.
        val reset = data(this, config, request.complicationType, 0)?.let { listOf(TimelineEntry(TimeInterval(m[1], m[2]), it)) }.orEmpty()
        return ComplicationDataTimeline(first, reset)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(this, null, type, 33)

    companion object {
        fun data(context: Context, config: WatchConfig?, type: ComplicationType, count: Int): ComplicationData? {
            if (type != ComplicationType.SHORT_TEXT) return null
            val t = WearText(config)
            val ctx = context.localizedFor(config)
            return ShortTextComplicationData.Builder(text(t.digits(count.toString())), text(ctx.getString(R.string.tasbih_today, t.digits(count.toString()))))
                .setTitle(text(ctx.getString(R.string.tasbih)))
                .setMonochromaticImage(icon(context, R.drawable.ic_tasbih))
                .setTapAction(open(context, Dest.TASBIH, 22)).build()
        }
    }
}

/** The Qibla bearing for this place, one tap from the compass. */
class QiblaComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val config = (application as NoorWearApp).config.currentOrPull()
        return data(this, config, request.complicationType)?.let { ComplicationDataTimeline(it, emptyList()) }
            ?: ComplicationDataTimeline(NoDataComplicationData(), emptyList())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(this, null, type)

    companion object {
        fun data(context: Context, config: WatchConfig?, type: ComplicationType): ComplicationData? {
            val t = WearText(config)
            val ctx = context.localizedFor(config)
            val tap = open(context, Dest.QIBLA, 23)
            val label = ctx.getString(R.string.qibla)
            return when (type) {
                ComplicationType.SHORT_TEXT -> {
                    // Without a place yet the bearing is unknown; the complication still opens the compass.
                    val bearing = config?.let { t.digits("%.0f°".format(Qibla.from(it.point).bearingDeg)) } ?: "—"
                    ShortTextComplicationData.Builder(text(bearing), text(ctx.getString(R.string.qibla_from_north, bearing)))
                        .setTitle(text(label))
                        .setMonochromaticImage(icon(context, R.drawable.ic_qibla))
                        .setTapAction(tap).build()
                }
                ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(icon(context, R.drawable.ic_qibla), text(label))
                    .setTapAction(tap).build()
                else -> null
            }
        }
    }
}
