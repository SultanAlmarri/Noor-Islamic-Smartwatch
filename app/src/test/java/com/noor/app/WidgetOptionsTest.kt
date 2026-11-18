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

    @Test fun everyCustomisationIsKept() {
        // (0.24) Every option the page offers, far from its default, read back the same; a 0.23 line reads as before,
        // the new options at their defaults; its "no Shuruq" is "never Shuruq".
        val a = WidgetOptions(background = WidgetBackground.GRADIENT, opacity = 64, accent = WidgetAccent.CUSTOM, accentCustom = 0xFF6BFFB0.toInt(),
            skyLight = com.noor.app.widget.SkyLight.GOLDEN, color1 = 0xFF123456.toInt(), color2 = 0xFFABCDEF.toInt(), textColor = com.noor.app.widget.TextColor.CUSTOM,
            textCustom = 0xFFFEDCBA.toInt(), icons = com.noor.app.widget.IconStyle.MONO, textScale = 115, align = com.noor.app.widget.WidgetAlign.CENTER,
            main = com.noor.app.widget.MainNumber.ADHAN, showIcon = false, showName = false, showTime = false, showBar = false,
            placeMode = com.noor.app.widget.PlaceMode.AREA, gregorian = true, showLater = false, countStyle = com.noor.app.widget.CountStyle.WORDS,
            timeFormat = com.noor.app.widget.TimeChoice.H24, ampm = false, digits = com.noor.app.widget.DigitChoice.ARABIC_INDIC,
            language = com.noor.app.widget.LanguageChoice.ARABIC, past = com.noor.app.widget.PastStyle.HIDE, highlight = com.noor.app.widget.HighlightStyle.OUTLINE,
            after = com.noor.app.widget.AfterAdhan.NEXT, rowOrder = com.noor.app.widget.RowOrder.FAJR_LEFT, shuruqChoice = com.noor.app.widget.ShuruqChoice.SHOW)
        assertEquals(a, WidgetOptions.decode(a.encode(), WidgetOptions()))
        val old = WidgetOptions.decode("bg=GLASS;op=78;ac=TEAL;ts=LARGE;sec=false;cd=true;sh=false;pl=true;hi=true;ju=true;tap=QIBLA", WidgetOptions())
        assertEquals(WidgetOptions(background = WidgetBackground.GLASS, opacity = 78, accent = WidgetAccent.TEAL, textSize = WidgetTextSize.LARGE, seconds = false,
            shuruq = false, hijri = true, tap = WidgetTap.QIBLA), old)
        assertEquals(com.noor.app.widget.ShuruqChoice.HIDE, old.shuruqShown)
        assertEquals(1.12f, old.textScaleFactor, 0.001f)
        // A size outside the slider's is not taken.
        assertEquals(0, WidgetOptions.decode("tsp=300", WidgetOptions()).textScale)
        // The defaults for new widgets: kept per kind, and back to the kind's own on reset.
        WidgetOptionsStore.saveDefaults(app, WidgetKind.NEXT, a)
        assertEquals(a, WidgetOptionsStore.defaults(app, WidgetKind.NEXT))
        assertEquals(a, WidgetOptionsStore.load(app, 1234, WidgetKind.NEXT))
        assertEquals(WidgetKind.TODAY.defaults, WidgetOptionsStore.load(app, 1234, WidgetKind.TODAY))
        WidgetOptionsStore.resetDefaults(app, WidgetKind.NEXT)
        assertEquals(WidgetKind.NEXT.defaults, WidgetOptionsStore.load(app, 1234, WidgetKind.NEXT))
    }

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
            // (0.24: the Hijri date is no longer on the information line unless asked for; the Date widget has it.)
            assertTrue(k.defaults.countdown && k.defaults.seconds && k.defaults.place && !k.defaults.hijri && k.defaults.jumuah)
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

    @Test fun theCountdownsSignComesFirstInEveryDigits() {
        // "−١:٠٤" (Arabic-Indic digits, which are right-to-left numbers to Android's text layout): drawn as one
        // left-to-right run, the minus first, on the left, as "−1:04" is; never "١:٠٤−".
        for (text in listOf("\u22121:04", "\u2212\u0661:\u0660\u0664", "+\u0661\u0663")) {
            val bmp = com.noor.app.widget.CountPicture.draw(app, com.noor.app.widget.CountPicture.Spec(text, 1f, shadow = false, colon = false))
            // The first column with ink, and the rows inked in the next few columns: a sign is a short mark in the middle
            // of the line (the minus a bar, the plus a cross), a digit runs most of its height.
            val first = (0 until bmp.width).first { x -> (0 until bmp.height).any { y -> Color.alpha(bmp.getPixel(x, y)) > 128 } }
            val cols = first until minOf(bmp.width, first + 4)
            val rows = (0 until bmp.height).filter { y -> cols.any { x -> Color.alpha(bmp.getPixel(x, y)) > 128 } }
            assertTrue("$text: a sign first (ink in ${rows.size} of ${bmp.height} rows)", rows.size < bmp.height * 0.35)
        }
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
