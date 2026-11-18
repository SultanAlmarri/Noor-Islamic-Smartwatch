package com.noor.app.testing

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.notify.LivePromotion
import com.noor.app.notify.StatusNotification
import com.noor.app.util.Formatter
import com.noor.prayer.HijriDate
import com.noor.prayer.Prayer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * (0.22.4, the owner: "a way for me to test the notifications ... the longest possible combination ... and other things
 * to test") The tester's notification test: the status notification with chosen content, posted through the very
 * builders, the same id, the same service and ticker as the real one ([StatusNotification.test]), so the phone shows
 * exactly what it would show for a real prayer. FOR TESTING ONLY, with [Tester].
 *
 * Nothing is stored; the prayer times, the alarms and the Adhan are not touched. "Back to the real notification",
 * leaving the Testing page or turning the tester's options off gives the real notification back.
 */
object NotificationTests {
    /** The cases to try. [offset]: the prayer's time from the moment the test starts (negative: it has begun). */
    enum class Preset(
        val label: String, val prayer: Prayer, val friday: Boolean, val at: LocalTime, val offset: Duration,
        val placeEn: String, val placeAr: String, val arabic: Boolean? = null, val numerals: Numerals? = null,
    ) {
        LONGEST_EN("Longest English", Prayer.DHUHR, true, LocalTime.of(12, 7), Duration.ofSeconds(10 * 3600 + 27 * 60 + 15),
            "Mohammed Bin Rashid Al Maktoum City, Dubai", LONG_AR_PLACE, arabic = false),
        LONGEST_AR("Longest Arabic", Prayer.DHUHR, true, LocalTime.of(12, 7), Duration.ofSeconds(10 * 3600 + 27 * 60 + 15),
            "Mohammed Bin Rashid Al Maktoum City, Dubai", LONG_AR_PLACE, arabic = true),
        ARABIC_DIGITS("Arabic-Indic digits", Prayer.DHUHR, true, LocalTime.of(12, 7), Duration.ofSeconds(10 * 3600 + 27 * 60 + 15),
            "Mohammed Bin Rashid Al Maktoum City, Dubai", LONG_AR_PLACE, numerals = Numerals.ARABIC_INDIC),
        AFTER("After a prayer (+29:59)", Prayer.ASR, false, LocalTime.of(15, 14), Duration.ofSeconds(-(29 * 60 + 50)),
            "Al Barsha South, Dubai", "البرشاء جنوب، دبي"),
        TEN_HOURS("Ten hours to Fajr", Prayer.FAJR, false, LocalTime.of(5, 17), Duration.ofSeconds(10 * 3600 + 27 * 60 + 15),
            "Dubai", "دبي"),
        MAGHRIB_LATE("Maghrib late (high latitude)", Prayer.MAGHRIB, false, LocalTime.of(22, 45), Duration.ofSeconds(4 * 3600 + 10 * 60 + 10),
            "Tromsø, Norway", "ترومسو، النرويج"),
        JUST_BEFORE("Just before the Adhan (−0:05, then +)", Prayer.MAGHRIB, false, LocalTime.of(17, 34), Duration.ofSeconds(5),
            "Dubai", "دبي"),
        SUNRISE("Sunrise (since sunrise)", Prayer.SHURUQ, false, LocalTime.of(6, 33), Duration.ofSeconds(-(12 * 60 + 40)),
            "Dubai", "دبي"),
    }

    /** What the test shows: a preset and the switches. */
    data class Case(
        val preset: Preset, val live: Boolean, val numerals: Numerals, val timeFormat: TimeFormat, val arabic: Boolean,
        /** When the test started (its countdown counts from here). */
        val start: Instant = Instant.now(),
    )

    /** The test being shown; null: the real notification. */
    var active by mutableStateOf<Case?>(null)
        private set
    /** What the phone did with the last post, for the owner to report. */
    var report by mutableStateOf<String?>(null)
        private set

    private var app: Context? = null
    private val zone: ZoneId = ZoneId.of("Asia/Dubai")

    /** Shows [case] in place of the real notification, ticking as the real one does. */
    fun show(context: Context, case: Case) {
        val a = context.applicationContext
        app = a
        // (Each test tries the chip afresh: a refusal remembered from the real notification is forgotten.)
        if (case.live) LivePromotion.retry(a)
        // (The Testing strip shows on every page while it is on: a test notification is never taken for the real one.)
        Tester.on = true
        active = case
        StatusNotification.test = source(case)
        StatusNotification.refreshAsync(a)
    }

    /** The real notification again. */
    fun stop(context: Context? = null) {
        val a = context?.applicationContext ?: app ?: return
        if (StatusNotification.test == null && active == null) return
        StatusNotification.test = null
        active = null
        report = null
        StatusNotification.refreshAsync(a)
    }

    /** What the notification says for [case] at [now] (the same [StatusNotification.Content] as the real one's). */
    internal fun content(case: Case, now: Instant): StatusNotification.Content {
        val p = case.preset
        val f = formatter(case)
        val today = LocalDate.now(zone)
        val date = if (p.friday) today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)) else today
        val countAt = case.start.plus(p.offset)
        val elapsed = !now.isBefore(countAt)
        val place = if (case.arabic) p.placeAr else p.placeEn
        val hijri = f.hijri(HijriDate.of(today, 0))
        // Before the prayer: rebuilt just after its time (it turns to "+"); after it: every half hour.
        val refreshAt = if (!elapsed) countAt.plusMillis(500) else now.plus(Duration.ofMinutes(30))
        return StatusNotification.Content(
            f.prayerName(p.prayer, date), f.timeAt(date.atTime(p.at).atZone(zone).toInstant(), zone), "$place  ·  $hijri",
            countAt, refreshAt, place, hijri, elapsed, countAt, p.prayer == Prayer.SHURUQ,
        )
    }

    internal fun formatter(case: Case) = Formatter(case.arabic, case.numerals, case.timeFormat)

    internal fun source(case: Case) = object : StatusNotification.TestSource {
        override val numerals = case.numerals
        override val onShown: (StatusNotification.Shown) -> Unit = { report = describe(it) }
        override fun context(app: Context) = StatusNotification.inLanguage(app, if (case.arabic) "ar" else "en")
        override fun content(now: Instant) = content(case, now)
        override fun formatter() = formatter(case)
        override fun live(app: Context) = case.live && !LivePromotion.refused(app)
    }

    private val FORMS = listOf("roomy (two gaps)", "one gap", "no dot between the prayer and its time", "narrow spaces", "the seconds left out")

    internal fun describe(s: StatusNotification.Shown): String = buildString {
        append(if (s.live) "Posted as a Live Update (status-bar chip)" else "Posted as Minhad's own notification")
        append(if (s.drawn) ", the countdown written by Minhad" else ", the countdown ticked by Android's Chronometer (the phone's digits are the app's)")
        if (s.drawn) append(if (s.held) ", held by the countdown service" else ", NOT held by the service (Android refused the start): to the minute")
        if (s.live) append(". First line: ${FORMS.getOrElse(s.form) { "?" }}; seconds ${if (s.form < 4 && s.seconds) "kept" else "not shown"}")
        append(".")
    }

    /** What Android did with the Live Update, from [LivePromotion]: for the page. */
    fun promotion(): String = when (LivePromotion.state.value) {
        LivePromotion.State.PROMOTED -> "Android promoted it (it may show in the status bar)"
        LivePromotion.State.REFUSED -> "Android did not promote it (the phone refused; on Samsung see Developer options → Live notifications for all apps)"
        LivePromotion.State.NOT_ALLOWED -> "Live Updates are turned off for Minhad in Android's settings"
        null -> if (android.os.Build.VERSION.SDK_INT >= 36) "Not checked yet (about 1.5 s after it is posted)" else "Needs Android 16: never promoted on this phone"
    }
}

private const val LONG_AR_PLACE = "مدينة محمد بن راشد آل مكتوم، دبي"
