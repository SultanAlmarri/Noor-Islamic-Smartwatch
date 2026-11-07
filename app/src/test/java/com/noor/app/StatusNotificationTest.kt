package com.noor.app

import android.app.AlarmManager
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.noor.app.data.AppSettings
import com.noor.app.data.Language
import com.noor.app.data.LocationMode
import com.noor.app.data.WearSync
import com.noor.app.notify.StatusNotification
import com.noor.prayer.Prayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The persistent prayer notification (0.9.3): what it says at any moment (0.9.7: − before a prayer, + for a while after
 * it, always the same as the home screen), that it is silent and ongoing in its own
 * low-importance category, that its next update is armed at the next prayer with a non-waking alarm, and that turning it
 * off removes it. One test method (DataStore allows one instance per process).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class StatusNotificationTest {
    @Test fun showsTheNextPrayerAndUpdatesAtItsTime() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        val base = AppSettings(onboardingDone = true, locationMode = LocationMode.MANUAL, manualCityId = "dxb", statusNotification = true)
        val config = WearSync.configFor(base, 0L, app.container.timetables)!!
        val dubai = ZoneId.of("Asia/Dubai")

        // 1 · Content at fixed moments (pure): Wednesday 4 Nov 2026 in Dubai (Asr 15:14, Maghrib 17:40).
        val f = com.noor.app.util.Formatter(false, base.numerals, base.timeFormat)
        val day = { h: Int, m: Int, sec: Int -> ZonedDateTime.of(2026, 11, 4, h, m, sec, 0, dubai).toInstant() }
        val before = day(16, 0, 0)
        val c = StatusNotification.content(config, base, before, arabic = false)
        assertEquals("Maghrib", c.prayer)
        assertEquals("5:40 PM", c.time)
        assertEquals("Dubai  ·  24 Jumada al-Ula 1448 AH", c.line)
        assertEquals("Dubai", c.place)
        assertEquals("24 Jumada al-Ula 1448 AH", c.hijri)
        assertFalse(c.elapsed)
        assertEquals("\u22121:40:00", c.countdown(before, f))
        assertEquals(config.state(before).next.at, c.nextAt)
        assertEquals(c.nextAt, c.countAt)
        // Rebuilt half a second after the prayer's time, when it has become "+ since".
        assertEquals(c.nextAt.plusMillis(500), c.refreshAt)
        // 0.9.7: once Asr has come (15:14), + time since it for a while (0.9.8: 30 minutes), then − to Maghrib.
        val asr = day(15, 29, 21)
        val a = StatusNotification.content(config, base, asr, arabic = false)
        assertEquals("Asr", a.prayer)
        assertEquals("3:14 PM", a.time)
        assertTrue(a.elapsed)
        assertEquals("+15:21", a.countdown(asr, f))
        assertEquals(day(15, 44, 0).plusMillis(500), a.refreshAt)
        assertEquals("Maghrib", StatusNotification.content(config, base, day(15, 44, 0).plusMillis(500), arabic = false).prayer)
        // The second before Maghrib, the moment of it, and the end of its 15 minutes.
        assertEquals("\u221200:01", StatusNotification.content(config, base, day(17, 39, 59), false).countdown(day(17, 39, 59), f))
        StatusNotification.content(config, base, day(17, 40, 0), false).let {
            assertEquals("Maghrib", it.prayer); assertTrue(it.elapsed); assertEquals("+00:00", it.countdown(day(17, 40, 0), f))
            assertEquals(day(17, 55, 0).plusMillis(500), it.refreshAt)
        }
        assertEquals("Isha", StatusNotification.content(config, base, day(17, 55, 1), false).prayer)
        // Turned off in Settings: straight to the next prayer.
        assertEquals("Isha", StatusNotification.content(config, base.copy(elapsedAfterPrayer = false), day(17, 41, 0), false).prayer)
        // The notification and the home screen say the same thing at every moment of two days, across midnight.
        var t = day(0, 0, 7)
        while (t.isBefore(day(0, 0, 7).plus(java.time.Duration.ofDays(2)))) {
            val home = com.noor.app.ui.today.countdownText(com.noor.prayer.Countdowns.of(config.state(t), t, base.elapsedAfterPrayer), f)
            val n = StatusNotification.content(config, base, t, arabic = false)
            assertEquals(t.toString(), home, n.countdown(t, f))
            assertTrue(t.toString(), n.refreshAt.isAfter(t))
            // Nothing it shows changes before its next update.
            val justBefore = n.refreshAt.minusMillis(1001)
            if (justBefore.isAfter(t)) StatusNotification.content(config, base, justBefore, false).let {
                assertEquals(t.toString(), n.prayer to n.line, it.prayer to it.line); assertEquals(n.elapsed, it.elapsed)
            }
            t = t.plusSeconds(97)
        }
        // After Isha the next prayer is tomorrow's Fajr, but the date line changes at midnight, so it updates then.
        val late = ZonedDateTime.of(2026, 11, 4, 22, 0, 0, 0, dubai).toInstant()
        val n = StatusNotification.content(config, base, late, arabic = false)
        assertEquals("Fajr", n.prayer)
        assertEquals(ZonedDateTime.of(2026, 11, 5, 0, 0, 1, 0, dubai).toInstant(), n.refreshAt)
        // Friday: Jumu'ah; Arabic: Arabic only, with the digits chosen.
        val friday = ZonedDateTime.of(2026, 11, 6, 11, 0, 0, 0, dubai).toInstant()
        assertEquals("Jumu’ah", StatusNotification.content(config, base, friday, arabic = false).prayer)
        val ar = StatusNotification.content(config, base.copy(numerals = com.noor.app.data.Numerals.ARABIC_INDIC), before, arabic = true)
        assertEquals("المغرب", ar.prayer)
        assertTrue(ar.line.startsWith("دبي"))
        assertFalse(Regex("[A-Za-z]").containsMatchIn(ar.prayer + ar.time + ar.line))

        // 2 · Posted for real: silent, ongoing, its own low-importance category, the next update armed (non-waking).
        repo.setManualCity("dxb"); repo.setLocationMode(LocationMode.MANUAL); repo.setOnboardingDone(); repo.setStatusNotification(true)
        StatusNotification.refresh(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(nm).allNotifications.single { it.channelId == StatusNotification.CHANNEL }
        assertTrue(posted.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(StatusNotification.CHANNEL).importance)
        assertNull(nm.getNotificationChannel(StatusNotification.CHANNEL).sound)
        assertEquals(NotificationCompat.CATEGORY_STATUS, posted.category)
        assertNotNull(posted.contentView)
        val now = java.time.Instant.now()
        val expected = StatusNotification.content(config, base, now, arabic = false)
        val title = posted.extras.getString(NotificationCompat.EXTRA_TITLE)!!
        assertTrue(title, Prayer.entries.any { title.startsWith(it.en) } || title.startsWith("Jumu’ah"))
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single { it.operation?.creatorPackage == app.packageName && it.type == AlarmManager.RTC }
        assertEquals(expected.refreshAt.toEpochMilli(), alarm.triggerAtTime)

        // The countdown is Android's own Chronometer, reading exactly like the home screen's.
        for (sec in listOf(0L, 5L, 65L, 599L, 3599L, 3600L, 7322L, 36000L))
            assertEquals(android.text.format.DateUtils.formatElapsedTime(sec), f.timer(java.time.Duration.ofSeconds(sec)))
        val parent = android.widget.FrameLayout(app)
        for (view in listOf(posted.contentView, posted.bigContentView)) {
            val chrono = view.apply(app, parent).findViewById<android.widget.Chronometer>(R.id.status_countdown)
            // Counting from the prayer's time: Android writes the minus before it, our format the plus after it.
            assertEquals(if (expected.elapsed) "+%s" else null, chrono.format)
            assertFalse(chrono.isCountDown)
        }
        // What the Chronometer shows, before and after a prayer, and in the moment before the rebuild: the same text
        // as the home screen, and never a doubled sign.
        for ((moment, label) in listOf(day(17, 39, 43) to "before Maghrib", day(17, 41, 5) to "after Maghrib")) {
            val cm = StatusNotification.content(config, base, moment, arabic = false)
            val nn = StatusNotification.build(app, app, cm, moment)
            val chrono = nn.contentView.apply(app, parent).findViewById<android.widget.Chronometer>(R.id.status_countdown)
            assertEquals(label, cm.countdown(moment, f), chrono.text.toString())
        }
        // 0.9.8: Noor's digits, whatever the phone's language. A phone in Arabic writes Arabic-Indic digits in its own
        // countdown; with Western digits chosen in Noor, Noor draws the countdown itself (and the other way round).
        val original = StatusNotification.systemZeroDigit
        try {
            for ((system, numerals, drawn) in listOf(Triple('\u0660', com.noor.app.data.Numerals.WESTERN, true), Triple('0', com.noor.app.data.Numerals.WESTERN, false),
                    Triple('0', com.noor.app.data.Numerals.ARABIC_INDIC, true), Triple('\u0660', com.noor.app.data.Numerals.ARABIC_INDIC, false))) {
                StatusNotification.systemZeroDigit = { system }
                assertEquals("$system $numerals", !drawn, StatusNotification.systemDigitsMatch(numerals))
                val fd = com.noor.app.util.Formatter(false, numerals, base.timeFormat)
                val moment = day(16, 0, 0)
                val cm = StatusNotification.content(config, base.copy(numerals = numerals), moment, arabic = false)
                val nn = StatusNotification.build(app, app, cm, moment, if (drawn) fd else null)
                for (view in listOf(nn.contentView, nn.bigContentView)) {
                    val v = view.apply(app, parent)
                    val text = v.findViewById<android.widget.TextView>(R.id.status_countdown_text)
                    val chrono = v.findViewById<android.widget.Chronometer>(R.id.status_countdown)
                    assertEquals(drawn, text.visibility == android.view.View.VISIBLE)
                    assertEquals(!drawn, chrono.visibility == android.view.View.VISIBLE)
                    if (drawn) assertEquals(if (numerals == com.noor.app.data.Numerals.WESTERN) "\u22121:40:00" else "\u2212\u0661:\u0664\u0660:\u0660\u0660", text.text.toString())
                }
            }
        } finally { StatusNotification.systemZeroDigit = original }
        // The expanded view says what the countdown means once (in the header), not twice.
        val bigView = posted.bigContentView.apply(app, parent)
        val texts = ArrayList<String>()
        fun collect(v: android.view.View) { if (v is android.widget.TextView && v.visibility == android.view.View.VISIBLE) texts += v.text.toString(); if (v is android.view.ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i)) }
        collect(bigView)
        assertTrue(texts.toString(), texts.none { it == "Next prayer" || it == "Time since the prayer began" })
        run {
            // Built 2 s before Maghrib, seen 1.2 s after it (the rebuild not yet done): "00:01", not "−−00:01".
            val cm = StatusNotification.content(config, base, day(17, 39, 58), arabic = false)
            val nn = StatusNotification.build(app, app, cm, day(17, 39, 58))
            val chrono = nn.contentView.apply(app, parent).findViewById<android.widget.Chronometer>(R.id.status_countdown)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(3200))
            chrono.base = chrono.base // redraws its text at the new time
            assertEquals("00:01", chrono.text.toString())
        }

        // Software renders of the notification as Android builds it, for review (not photos of a phone's shade), with a
        // usual and a very long place name: collapsed, the date and the place shrink to fit within Android's 48dp;
        // expanded, they wrap and are read in full. The width is the content area of a notification on a narrow phone
        // (about 270dp).
        val long = config.copy(placeEn = "Al Barsha South Third, Dubai, United Arab Emirates", placeAr = "البرشاء جنوب الثالثة، دبي، الإمارات العربية المتحدة")
        val usual = config.copy(placeEn = "Al Barsha South, Dubai", placeAr = "البرشاء جنوب، دبي")
        for ((name, arabic, cfg) in listOf(Triple("en", false, usual), Triple("ar", true, usual), Triple("en_long", false, long), Triple("ar_long", true, long))) {
            val ctx = if (arabic) app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).also { it.setLocale(java.util.Locale("ar")) }) else app
            val lc = StatusNotification.content(cfg, base, asr, arabic)
            val n = StatusNotification.build(ctx, app, lc, asr)
            for ((kind, view, maxDp) in listOf(Triple("collapsed", n.contentView, 48), Triple("expanded", n.bigContentView, 252))) {
                val v = view.apply(ctx, parent)
                val d = app.resources.displayMetrics
                val w = (270 * d.density).toInt()
                // Twice, as Android does when the text shrinks to fit and asks for a new layout.
                repeat(2) {
                    v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
                    v.layout(0, 0, w, v.measuredHeight)
                }
                assertTrue("$name $kind ${v.measuredHeight / d.density}dp", v.measuredHeight <= maxDp * d.density + 0.5f)
                for (id in listOf(R.id.status_line, R.id.status_place, R.id.status_hijri, R.id.status_prayer)) {
                    val tv = v.findViewById<android.widget.TextView>(id) ?: continue
                    if (tv.visibility != android.view.View.VISIBLE) continue
                    val layout = tv.layout!!
                    val cut = (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
                    if (id == R.id.status_line) {
                        // Collapsed: one line, never below 10sp; the Hijri date always whole; a usual place whole too.
                        assertEquals(1, layout.lineCount)
                        assertTrue("$name ${tv.textSize / d.scaledDensity}sp", tv.textSize / d.scaledDensity >= 9.99f)
                        assertTrue("$name: ${tv.text}", layout.getEllipsisStart(0) >= lc.hijri.length + 3 || cut == 0)
                        if (cfg === usual) assertEquals("$name collapsed: ${tv.text}", 0, cut)
                    } else {
                        // Expanded: the prayer, the place and the date, every word of them.
                        assertEquals("$name $kind ${tv.text}", 0, cut)
                        assertTrue("$name $kind ${tv.text}", layout.getLineEnd(layout.lineCount - 1) == tv.text.length)
                    }
                }
                runCatching {
                    val bmp = android.graphics.Bitmap.createBitmap(w, v.measuredHeight.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp); canvas.drawColor(0xFFF2F2F2.toInt()); v.draw(canvas)
                    val dir = java.io.File("build/screenshots").apply { mkdirs() }
                    java.io.File(dir, "55_status_notification_${kind}_$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }

        // 3 · Off: removed, and nothing left armed.
        repo.setStatusNotification(false)
        StatusNotification.refresh(app)
        assertTrue(shadowOf(nm).allNotifications.none { it.channelId == StatusNotification.CHANNEL })
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.none { it.type == AlarmManager.RTC })
        Unit
    }
}
