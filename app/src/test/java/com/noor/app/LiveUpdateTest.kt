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
@Config(sdk = [34])
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
        // (0.22, the owner: the countdown was small, in the header, and in the phone's Arabic-Indic digits.) The title is
        // the countdown, written by Minhad in the app's digits; no chronometer of Android's, no "Next prayer".
        assertEquals("\u2068Dhuhr  ·  12:05 PM\u2069\u2003\u2003\u2066\u200E\u22122:27:44\u2069", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
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

        // Minhad's own notification says no "Next prayer" either (its header showed it).
        assertNull(StatusNotification.build(app, app, c, before, f).extras.getCharSequence(Notification.EXTRA_SUB_TEXT))

        // Below Android 16 the notification keeps Minhad's own look (no chip there).
        assertFalse(StatusNotification.liveWanted(app, s))
        assertFalse(StatusNotification.promotedAllowed(app))
    }
}
