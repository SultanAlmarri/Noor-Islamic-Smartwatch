package com.noor.app

import android.app.Notification
import com.noor.app.data.AppSettings
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * (0.22) The prayer countdown as a chip in the status bar, as Samsung's Clock shows its timer (the owner's wish): an
 * Android 16 Live Update. Android promotes only its own templates, so this notification has no custom view; its
 * chronometer counts to the prayer (or from it), and it asks to be promoted. On by default, with the notification.
 * (Robolectric stops at Android 15: whether a phone shows the chip is seen on the phone.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LiveUpdateTest {
    @Test fun theCountdownCanBeAChipInTheStatusBar() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        // On by default, both.
        assertTrue(AppSettings().statusNotification)
        assertTrue(AppSettings().statusBarChip)
        val s = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb")
        val config = WearSync.configFor(s, 0L, app.container.timetables)!!
        val dubai = ZoneId.of("Asia/Dubai")

        // 9:37:16 on 4 Nov 2026: Dhuhr (12:05) 2:27:44 away.
        val before = ZonedDateTime.of(2026, 11, 4, 9, 37, 16, 0, dubai).toInstant()
        val c = StatusNotification.content(config, s, before, arabic = false)
        val f = com.noor.app.util.Formatter(false, s.numerals, s.timeFormat)
        val n = StatusNotification.buildLive(app, app, c, before, f)
        assertNull("a custom view is never promoted", n.contentView)
        assertNull(n.bigContentView)
        // (0.24, the owner) By default the chip shows the countdown alone: the small icon is Minhad's crescent, transparent
        // (every pixel of it); with the setting on, the crescent itself.
        assertFalse(AppSettings().statusChipIcon)
        StatusNotification.chipIcon = false
        val clear = StatusNotification.buildLive(app, app, c, before, f).smallIcon
        assertEquals(R.drawable.ic_notification_clear, clear.resId)
        run {
            val bm = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            clear.loadDrawable(app)!!.apply { setBounds(0, 0, 96, 96) }.draw(android.graphics.Canvas(bm))
            for (y in 0 until 96) for (x in 0 until 96) assertEquals("pixel $x,$y", 0, android.graphics.Color.alpha(bm.getPixel(x, y)))
        }
        StatusNotification.chipIcon = true
        assertEquals(R.drawable.ic_notification, StatusNotification.buildLive(app, app, c, before, f).smallIcon.resId)
        StatusNotification.chipIcon = false
        // (0.22, the owner: the countdown was small, in the header, and in the phone's Arabic-Indic digits.) The title is
        // the countdown, written by Minhad in the app's digits; no chronometer of Android's, no "Next prayer".
        assertEquals("\u2068Dhuhr · 12:05 PM\u2069\u2003\u2066\u200E\u22122:27:44\u2069", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Jumada"))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNull(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("asks to be promoted", n.extras.getBoolean(StatusNotification.EXTRA_REQUEST_PROMOTED))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Jumada"))
        assertEquals("\u2066\u200E\u22122:27:44\u2069", StatusNotification.chipText(c.countdown(before, f)))
        // (0.22.2) The chip in the app's digits whatever the phone's language: with the phone in Arabic, Western digits
        // chosen in Minhad stay Western (and Arabic-Indic chosen stay Arabic-Indic).
        val phoneLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-AE"))
            android.os.LocaleList.setDefault(android.os.LocaleList(java.util.Locale.forLanguageTag("ar-AE")))
            val w = com.noor.app.util.Formatter(false, com.noor.app.data.Numerals.WESTERN, s.timeFormat)
            assertEquals("\u2066\u200E\u22122:27:44\u2069", StatusNotification.chipText(c.countdown(before, w)))
            val ai = com.noor.app.util.Formatter(false, com.noor.app.data.Numerals.ARABIC_INDIC, s.timeFormat)
            assertEquals("\u2066\u200E\u2212\u0662:\u0662\u0667:\u0664\u0664\u2069", StatusNotification.chipText(c.countdown(before, ai)))
        } finally { java.util.Locale.setDefault(phoneLocale) }
        // With Arabic-Indic digits chosen in the app, the countdown is in them, whatever the phone's language.
        val ai = com.noor.app.util.Formatter(false, com.noor.app.data.Numerals.ARABIC_INDIC, s.timeFormat)
        assertTrue(StatusNotification.buildLive(app, app, c, before, ai).extras.getCharSequence(Notification.EXTRA_TITLE).toString().endsWith("\u2066\u200E\u2212\u0662:\u0662\u0667:\u0664\u0664\u2069"))

        // After the prayer, during its "since" period: "+" and the time since it.
        val after = ZonedDateTime.of(2026, 11, 4, 12, 20, 0, 0, dubai).toInstant()
        val a = StatusNotification.content(config, s, after, arabic = false)
        val m = StatusNotification.buildLive(app, app, a, after, f)
        assertTrue(a.elapsed)
        assertTrue(m.extras.getCharSequence(Notification.EXTRA_TITLE).toString().endsWith("\u2066\u200E+15:00\u2069"))
        assertEquals("\u2066\u200E+15:00\u2069", StatusNotification.chipText("+15:00"))

        // (0.22.2) Laid out right to left, as the shade of a phone whose system is in Arabic lays it out (the owner's Galaxy
        // wrote "2:08:17−"): the sign stays before the digits, "−" and "+" alike.
        for (txt in listOf(StatusNotification.ltr("\u22122:08:17"), StatusNotification.ltr("+15:21"))) {
            val paint = android.text.TextPaint().apply { textSize = 40f }
            val lay = android.text.StaticLayout.Builder.obtain(txt, 0, txt.length, paint, 1000)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL).build()
            val sign = txt.indexOfFirst { it == '\u2212' || it == '+' }
            val digit = txt.indexOfFirst { it.isDigit() }
            assertTrue("$txt: the sign is drawn after the digits", lay.getPrimaryHorizontal(sign) < lay.getPrimaryHorizontal(digit))
        }
        // The whole first line in a right-to-left shade: the countdown on the left (its sign first), the prayer on the right.
        run {
            val line = StatusNotification.titleLine("Asr  ·  3:10 PM", "\u22122:08:17")
            val lay = android.text.StaticLayout.Builder.obtain(line, 0, line.length, android.text.TextPaint().apply { textSize = 40f }, 2000)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL).build()
            val x = { ch: Char -> lay.getPrimaryHorizontal(line.indexOf(ch)) }
            assertTrue("countdown left of the prayer", x('\u2212') < x('A'))
            assertTrue("sign before the digits", x('\u2212') < lay.getPrimaryHorizontal(line.indexOf("2:08")))
        }

        // (0.22.3) The first line always fits on one line (the owner's Galaxy cut it to "−1:…"): the longest names, times
        // and countdowns, in both languages and both digit sets, at three font sizes on phones from 360 to 448 dp wide;
        // the seconds kept wherever any spacing can hold them (always at 1.15× and below on 384 dp and wider).
        run {
            val names = listOf("Maghrib", "Jumu\u2019ah", "Shuruq", "\u0627\u0644\u0645\u063A\u0631\u0628", "\u0627\u0644\u062C\u0645\u0639\u0629", "\u0627\u0644\u0634\u0631\u0648\u0642")
            val times = listOf("12:07 PM", "11:45 PM", "10:58 AM", "\u0661\u0662:\u0660\u0667 \u0645", "12:07 \u0645")
            val counts = listOf("\u221210:27:15", "\u221223:59:59", "\u22121:46:17", "+29:59", "\u2212\u0661\u0660:\u0662\u0667:\u0661\u0665")
            var dropped = 0; var total = 0; var extremes = 0
            for (scale in listOf(1.0f, 1.15f, 1.3f)) for (wdp in listOf(360, 384, 411, 448)) {
                val cfg = android.content.res.Configuration(app.resources.configuration).apply { fontScale = scale; screenWidthDp = wdp }
                val ctx = app.createConfigurationContext(cfg)
                val dm = ctx.resources.displayMetrics
                val fits = StatusNotification.titleFits(ctx, (wdp * dm.density).toInt())
                for (n in names) for (t in times) for (cd in counts) {
                    val line = StatusNotification.fittedTitle(n, t, cd, fits)
                    total++
                    val kept = line.toString().contains(cd)
                    if (!kept) dropped++
                    // Every combination, even impossible ones (Maghrib ten hours away at noon), fits on phones like the
                    // owner's (384 dp and wider, font up to 1.15×); on smaller phones or larger fonts, every one that can
                    // happen (below), and the rest are counted.
                    if (scale <= 1.15f && wdp >= 384) assertTrue("does not fit at ${scale}x, $wdp dp: $line", fits(line))
                    else if (!fits(line)) extremes++
                    assertTrue("the order changed: $line", line.toString().indexOf(n) < line.toString().indexOf(t.substringBefore(' ')))
                    if (scale <= 1.15f && wdp >= 411) assertTrue("seconds dropped at ${scale}x, $wdp dp: $line", kept)
                }
            }
            println("titles: $total, seconds dropped in $dropped, impossible extremes not fitting on small phones or large fonts: $extremes")
            // What can really happen, on the smallest phones at 1.3×: the long countdowns only before Fajr (after Isha), the
            // noon-ish times only for Dhuhr/Jumu'ah, late evening times only for Maghrib/Isha at high latitudes.
            val real = listOf(
                Triple("Fajr", "5:17 AM", "\u221210:27:15"), Triple("\u0627\u0644\u0641\u062C\u0631", "5:17 \u0635", "\u221210:27:15"),
                Triple("Jumu\u2019ah", "12:07 PM", "\u22125:33:12"), Triple("\u0627\u0644\u062C\u0645\u0639\u0629", "12:07 \u0645", "\u22125:33:12"),
                Triple("Maghrib", "10:45 PM", "\u22124:10:10"), Triple("\u0627\u0644\u0645\u063A\u0631\u0628", "10:45 \u0645", "\u22124:10:10"),
                Triple("Isha", "11:59 PM", "\u22122:59:59"), Triple("Shuruq", "6:33 AM", "\u22121:16:00"), Triple("Maghrib", "5:34 PM", "+29:59"),
                Triple("\u0627\u0644\u0645\u063A\u0631\u0628", "\u0665:\u0663\u0664 \u0645", "\u2212\u0661:\u0664\u0666:\u0661\u0667"))
            for (scale in listOf(1.0f, 1.15f, 1.3f)) for (wdp in listOf(360, 384, 411, 448)) {
                val ctx = app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply { fontScale = scale; screenWidthDp = wdp })
                val fits = StatusNotification.titleFits(ctx, (wdp * ctx.resources.displayMetrics.density).toInt())
                for ((n, t, cd) in real) {
                    val line = StatusNotification.fittedTitle(n, t, cd, fits)
                    // (The smallest phones at the largest fonts, 360 dp at 1.3×, are reported, not required.)
                    if (wdp >= 384 || scale <= 1.15f) assertTrue("a real case does not fit at ${scale}x, $wdp dp: $line", fits(line))
                    else if (!fits(line)) println("too long on 360 dp at 1.3x: $line")
                    if (scale <= 1.15f && wdp >= 384) assertTrue("a real case lost its seconds at ${scale}x, $wdp dp: $line", line.toString().contains(cd))
                }
            }
            // The owner's case: Galaxy, 411 dp, his font: "Maghrib · 5:34 PM  −1:46:17" whole.
            val ctx = app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply { fontScale = 1.15f })
            val line = StatusNotification.fittedTitle("Maghrib", "5:34 PM", "\u22121:46:17", StatusNotification.titleFits(ctx, (411 * ctx.resources.displayMetrics.density).toInt()))
            assertTrue(line.toString(), line.toString().contains("\u22121:46:17") && line.toString().contains("Maghrib") && line.toString().contains("5:34"))
        }
        // The place with its city (the owner): "Madinat Al Mataar, Dubai"; none added where there is no context or a comma.
        run {
            val p = com.noor.app.data.ResolvedPlace(com.noor.prayer.GeoPoint(24.9, 55.17), java.time.ZoneId.of("Asia/Dubai"), "Madinat Al Mataar", "\u0645\u062F\u064A\u0646\u0629 \u0627\u0644\u0645\u0637\u0627\u0631",
                true, "AE", "DU", null, "Dubai, United Arab Emirates", "\u062F\u0628\u064A\u060C \u0627\u0644\u0625\u0645\u0627\u0631\u0627\u062A")
            assertEquals("Madinat Al Mataar, Dubai", StatusNotification.withCity("Madinat Al Mataar", p, false))
            assertEquals("\u0645\u062F\u064A\u0646\u0629 \u0627\u0644\u0645\u0637\u0627\u0631\u060C \u062F\u0628\u064A", StatusNotification.withCity("\u0645\u062F\u064A\u0646\u0629 \u0627\u0644\u0645\u0637\u0627\u0631", p, true))
            assertEquals("Al Barsha South, Dubai", StatusNotification.withCity("Al Barsha South, Dubai", p, false))
            assertEquals("Dubai", StatusNotification.withCity("Dubai", p.copy(contextEn = "Dubai"), false))
        }

        // Minhad's own notification says no "Next prayer" either (its header showed it).
        assertNull(StatusNotification.build(app, app, c, before, f).extras.getCharSequence(Notification.EXTRA_SUB_TEXT))

        // Below Android 16 the notification keeps Minhad's own look (no chip there).
        assertFalse(StatusNotification.liveWanted(app, s))
        assertFalse(StatusNotification.promotedAllowed(app))
    }
}
