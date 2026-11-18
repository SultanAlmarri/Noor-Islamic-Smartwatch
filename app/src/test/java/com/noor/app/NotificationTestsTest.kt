package com.noor.app

import android.app.Notification
import android.app.NotificationManager
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.notify.StatusNotification
import com.noor.app.testing.NotificationTests
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * (0.22.4) The tester's notification tests (the owner: "put the longest possible combination for the notification, and
 * test if it works"): each preset, as a Live Update and as Minhad's own notification, in both languages and digit sets,
 * is posted through the real builders under the real id; its first line fits on the owner's 411 dp Galaxy and keeps
 * the prayer, its time and the whole countdown (sign first, the app's digits); "Back to the real notification" ends it.
 * Robolectric (Android 14), not the phone: whether the phone promotes the chip is seen there, and the page reports it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class NotificationTestsTest {
    @Test fun eachPresetFitsAndKeepsItsInformation() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        app.container.settings.setOnboardingDone()
        val nm = app.getSystemService(NotificationManager::class.java)
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888); android.graphics.Paint()
        val parent by lazy { android.widget.FrameLayout(app) }
        var count = 0
        try {
            for (preset in NotificationTests.Preset.entries) for (live in listOf(true, false)) for (h24 in listOf(false, true)) {
                val arabic = preset.arabic ?: false
                val numerals = preset.numerals ?: Numerals.WESTERN
                val case = NotificationTests.Case(preset, live, numerals, if (h24) TimeFormat.H24 else TimeFormat.H12, arabic)
                var shown: StatusNotification.Shown? = null
                val source = NotificationTests.source(case)
                StatusNotification.test = object : StatusNotification.TestSource by source {
                    override val onShown: (StatusNotification.Shown) -> Unit = { shown = it; NotificationTests.describe(it) }
                }
                StatusNotification.refresh(app)
                val label = "${preset.name} live=$live h24=$h24"
                val posted = shadowOf(nm).allNotifications.single { it.channelId == StatusNotification.CHANNEL }
                assertNotNull(label, shown)
                assertTrue(label, NotificationTests.describe(shown!!).isNotEmpty())
                val c = NotificationTests.content(case, java.time.Instant.now())
                val digit = if (numerals == Numerals.ARABIC_INDIC) "[٠-٩]" else "[0-9]"
                val sign = if (preset.offset.isNegative) "\\+" else "−"
                val countdown = Regex("^$sign($digit+:)?$digit{2}:$digit{2}$")
                if (live) {
                    val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE)!!
                    val t = title.toString()
                    // The prayer, its time and the whole countdown, in that order, all on one line on the owner's phone.
                    assertTrue("$label: $t", t.contains(c.prayer))
                    assertTrue("$label: $t", t.contains(c.time.substringBefore(' ')))
                    val cd = t.substringAfter("⁦‎").substringBefore('⁩')
                    assertTrue("$label: the countdown '$cd'", countdown.matches(cd))
                    assertTrue("$label: $t", t.indexOf(c.prayer) < t.indexOf(c.time.substringBefore(' ')))
                    val ctx = StatusNotification.inLanguage(app, if (arabic) "ar" else "en")
                    assertTrue("$label does not fit: $t", StatusNotification.titleFits(ctx, (411 * ctx.resources.displayMetrics.density).toInt())(title))
                    assertTrue("$label: seconds dropped", shown!!.form < 4)
                    assertTrue(label, posted.extras.getBoolean(StatusNotification.EXTRA_REQUEST_PROMOTED))
                    assertTrue(label, posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(c.place!!))
                } else {
                    val v = posted.contentView.apply(app, parent)
                    assertEquals(label, "${c.prayer}  ·  ${c.time}", v.findViewById<android.widget.TextView>(R.id.status_prayer).text.toString())
                    // The phone here writes Western digits: Android's Chronometer ticks them; Arabic-Indic chosen, Minhad does.
                    if (shown!!.drawn) {
                        val cd = v.findViewById<android.widget.TextView>(R.id.status_countdown_text).text.toString()
                        assertTrue("$label: the countdown '$cd'", countdown.matches(cd))
                    } else assertEquals(label, android.view.View.VISIBLE, v.findViewById<android.view.View>(R.id.status_countdown).visibility)
                    assertEquals(label, numerals == Numerals.ARABIC_INDIC, shown!!.drawn)
                }
                count++
            }
        } finally {
            StatusNotification.test = null
        }
        assertEquals(NotificationTests.Preset.entries.size * 4, count)
        // "Just before the Adhan" turns to "+" at its time, as the real one does at a prayer.
        run {
            val case = NotificationTests.Case(NotificationTests.Preset.JUST_BEFORE, true, Numerals.WESTERN, TimeFormat.H12, false)
            val before = NotificationTests.content(case, case.start)
            val after = NotificationTests.content(case, case.start.plusSeconds(6))
            assertTrue(!before.elapsed && after.elapsed)
            assertEquals(before.countAt.plusMillis(500), before.refreshAt)
            val f = NotificationTests.formatter(case)
            assertEquals("−00:05", before.countdown(case.start, f))
            assertEquals("+00:01", after.countdown(case.start.plusSeconds(6), f))
        }
        // The longest English line is the owner's example, whole.
        run {
            val case = NotificationTests.Case(NotificationTests.Preset.LONGEST_EN, true, Numerals.WESTERN, TimeFormat.H12, false)
            val c = NotificationTests.content(case, case.start)
            assertEquals("Jumu’ah", c.prayer)
            assertEquals("12:07 PM", c.time)
            assertEquals("−10:27:15", c.countdown(case.start, NotificationTests.formatter(case)))
            assertEquals("Mohammed Bin Rashid Al Maktoum City, Dubai", c.place)
        }
        // Back to the real notification: the test is gone.
        NotificationTests.show(app, NotificationTests.Case(NotificationTests.Preset.SUNRISE, false, Numerals.WESTERN, TimeFormat.H12, false))
        assertNotNull(StatusNotification.test)
        NotificationTests.stop(app)
        assertNull(StatusNotification.test)
        assertNull(NotificationTests.active)
        Unit
    }
}
