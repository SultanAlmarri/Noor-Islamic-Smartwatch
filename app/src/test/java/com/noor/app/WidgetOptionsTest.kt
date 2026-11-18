package com.noor.app

import android.graphics.Color
import com.noor.app.widget.WidgetAccent
import com.noor.app.widget.WidgetArt
import com.noor.app.widget.WidgetBackground
import com.noor.app.widget.WidgetKind
import com.noor.app.widget.WidgetOptions
import com.noor.app.widget.WidgetOptionsStore
import com.noor.app.widget.WidgetPalette
import com.noor.app.widget.WidgetRender
import com.noor.app.widget.WidgetTap
import com.noor.app.widget.WidgetTextSize
import com.noor.prayer.calendar.IslamicEvent
import com.noor.prayer.calendar.IslamicCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The widgets' options and pieces (0.23): options kept per widget id, read back, removed with the widget and moved
 * with it on a restore; a widget never configured reads its kind's defaults (0.22's look for the three placed before
 * 0.23); every text colour readable on its background; the ring's progress; the next occasion and its days. (No
 * DataStore here, so several test methods.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetOptionsTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun optionsAreKeptPerWidgetAndGoWithIt() {
        val a = WidgetOptions(background = WidgetBackground.LIGHT, opacity = 55, accent = WidgetAccent.TEAL, textSize = WidgetTextSize.LARGE,
            seconds = false, countdown = true, shuruq = false, place = false, hijri = false, jumuah = false, tap = WidgetTap.QIBLA)
        assertEquals(a, WidgetOptions.decode(a.encode(), WidgetOptions()))
        // Unknown or missing values keep the base's; nothing throws on rubbish.
        assertEquals(WidgetOptions(accent = WidgetAccent.ROSE), WidgetOptions.decode("ac=ROSE;bg=PURPLE;op=x;zz", WidgetOptions()))
        assertEquals(WidgetKind.RING.defaults, WidgetOptions.decode(null, WidgetKind.RING.defaults))

        WidgetOptionsStore.save(app, 41, a)
        WidgetOptionsStore.save(app, 42, WidgetOptions(accent = WidgetAccent.BLUE))
        assertEquals(a, WidgetOptionsStore.load(app, 41, WidgetKind.TODAY))
        assertEquals(WidgetAccent.BLUE, WidgetOptionsStore.load(app, 42, WidgetKind.NEXT).accent)
        // Never configured: the kind's defaults. The three widgets placed before 0.23 keep the sky of 0.22.
        for (k in listOf(WidgetKind.NEXT, WidgetKind.TODAY, WidgetKind.SKY)) {
            assertEquals(k.defaults, WidgetOptionsStore.load(app, 99, k))
            assertEquals(WidgetBackground.SKY, k.defaults.background)
            assertTrue(k.defaults.countdown && k.defaults.seconds && k.defaults.place && k.defaults.hijri && k.defaults.jumuah)
        }
        assertFalse(WidgetKind.STRIP.defaults.shuruq)
        // Removed with its widget; the other stays.
        WidgetOptionsStore.delete(app, intArrayOf(41))
        assertFalse(WidgetOptionsStore.has(app, 41))
        assertEquals(WidgetKind.TODAY.defaults, WidgetOptionsStore.load(app, 41, WidgetKind.TODAY))
        assertTrue(WidgetOptionsStore.has(app, 42))
        // Restored from a backup with new ids: the options follow.
        WidgetOptionsStore.move(app, intArrayOf(42), intArrayOf(77))
        assertFalse(WidgetOptionsStore.has(app, 42))
        assertEquals(WidgetAccent.BLUE, WidgetOptionsStore.load(app, 77, WidgetKind.NEXT).accent)
    }

    @Test fun everyKindHasItsReceiverAndOptions() {
        // The receivers placed widgets are bound to: never renamed.
        assertEquals("com.noor.app.widget.NextPrayerWidget", WidgetKind.NEXT.provider.name)
        assertEquals("com.noor.app.widget.TodayWidget", WidgetKind.TODAY.provider.name)
        assertEquals("com.noor.app.widget.SkyWidget", WidgetKind.SKY.provider.name)
        for (k in WidgetKind.entries) {
            assertEquals(k, WidgetKind.ofClassName(k.provider.name))
            assertTrue(k.options.isNotEmpty())
        }
        // Each is declared in the manifest, with its settings page.
        val pm = app.packageManager
        for (k in WidgetKind.entries) {
            val info = pm.getReceiverInfo(android.content.ComponentName(app, k.provider), android.content.pm.PackageManager.GET_META_DATA)
            assertNotNull("${k.name} declared", info.metaData?.getInt("android.appwidget.provider"))
        }
    }

    /** WCAG contrast of two opaque colours. */
    private fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
        }
        val l1 = lum(a); val l2 = lum(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /** A translucent colour over an opaque one. */
    private fun over(fg: Int, bg: Int): Int {
        val a = Color.alpha(fg) / 255f
        fun mix(f: Int, b: Int) = (f * a + b * (1 - a)).toInt()
        return Color.rgb(mix(Color.red(fg), Color.red(bg)), mix(Color.green(fg), Color.green(bg)), mix(Color.blue(fg), Color.blue(bg)))
    }

    @Test fun everyTextReadsOnItsBackground() {
        // Over the opaque Dark and Light backgrounds, and over Glass's lightest ink at full opacity: every text colour
        // and every accent meets WCAG AA (4.5:1), as the app's own text does (ThemeContrastTest).
        val grounds = mapOf(WidgetBackground.DARK to 0xFF121826.toInt(), WidgetBackground.LIGHT to 0xFFF4F1EA.toInt(), WidgetBackground.GLASS to 0xFF222B3E.toInt())
        for ((bg, ground) in grounds) for (accent in WidgetAccent.entries.filter { it != WidgetAccent.YOU }) {
            val p = WidgetPalette.of(app, WidgetOptions(background = bg, opacity = 100, accent = accent))
            for ((what, c) in listOf("text" to p.text, "secondary" to p.secondary, "muted" to p.muted, "accent" to p.accent)) {
                val ratio = contrast(over(c, ground), ground)
                assertTrue("$what of $bg/$accent: $ratio", ratio >= 4.5)
            }
            // The prayer counted to, in the accent on its pill.
            val pill = over(p.pill, ground)
            assertTrue("accent on the pill, $bg/$accent", contrast(p.accent, pill) >= 4.5)
        }
    }

    @Test fun theRingShowsTheTimeGone() {
        val d = 3f
        val accent = 0xFFF6D696.toInt(); val track = 0x33FFFFFF
        val ring = WidgetArt.ring(300, d, 0.25f, accent, track)
        val c = 150; val r = 150 - 5f * d
        fun at(deg: Double) = ring.getPixel((c + r * kotlin.math.cos(Math.toRadians(deg - 90))).toInt(), (c + r * kotlin.math.sin(Math.toRadians(deg - 90))).toInt())
        // A quarter gone: from the top clockwise to three o'clock in the accent, the rest the track.
        for (deg in listOf(10.0, 45.0, 80.0)) assertEquals("at $deg°", accent, at(deg) or 0xFF000000.toInt())
        for (deg in listOf(120.0, 200.0, 300.0)) assertTrue("track at $deg°", Color.alpha(at(deg)) in 1..0x60)
        // Nothing gone: only the track and the head at the top.
        val empty = WidgetArt.ring(300, d, 0f, accent, track)
        assertTrue(Color.alpha(empty.getPixel(c + 60, c - r.toInt())) < 0x60)
    }

    @Test fun theNextOccasionAndItsDays() {
        // 4 November 2026 (24 Jumada al-Ula 1448): Ramadan begins on 1 Ramadan 1448, the White Days (13–15 Jumada
        // al-Akhirah) are further than two days and are not shown in its place.
        val today = LocalDate.of(2026, 11, 4)
        val next = WidgetRender.nextOccasion(today, 0)!!
        assertEquals(IslamicEvent.RAMADAN, next.event)
        val ramadan = IslamicCalendar.gregorian(1448, 9, 1)!!
        assertEquals(ChronoUnit.DAYS.between(today, ramadan), next.daysUntil)
        // The user's Hijri adjustment moves it: a day later by the calendar is a day sooner here (the inverse of HijriDate.of).
        assertEquals(next.daysUntil - 1, WidgetRender.nextOccasion(today, 1)!!.daysUntil)
        // The White Days while they are under way, and two days before.
        val white = IslamicCalendar.gregorian(1448, 6, 13)!!
        assertEquals(IslamicEvent.WHITE_DAYS, WidgetRender.nextOccasion(white, 0)!!.event)
        assertEquals(2L, WidgetRender.nextOccasion(white.minusDays(2), 0)!!.daysUntil)
        assertEquals(IslamicEvent.RAMADAN, WidgetRender.nextOccasion(white.minusDays(3), 0)!!.event)
        // On the day: 0 days.
        assertEquals(0L, WidgetRender.nextOccasion(ramadan, 0)!!.daysUntil)
    }
}
