package com.noor.app

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.util.Formatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class FormatterTest {
    @Test fun westernTwelveHour() {
        val f = Formatter(arabic = false, numerals = Numerals.WESTERN, timeFormat = TimeFormat.H12)
        assertEquals("5:09" to "AM", f.time(5 * 60 + 9))
        assertEquals("12:05" to "PM", f.time(12 * 60 + 5))
        assertEquals("12:00" to "AM", f.time(0))
    }

    @Test fun arabicIndicDigitsIndependentOfLanguage() {
        val f = Formatter(arabic = false, numerals = Numerals.ARABIC_INDIC, timeFormat = TimeFormat.H24)
        assertEquals("١٧:٤٠" to null, f.time(17 * 60 + 40))
        val g = Formatter(arabic = true, numerals = Numerals.WESTERN, timeFormat = TimeFormat.H12)
        assertEquals("5:40" to "م", g.time(17 * 60 + 40))
    }

    /**
     * 0.9.8: Noor's digits whatever the process's default locale: an app process can run with the phone's language
     * (Arabic, Persian) as its default, and String.format writes that language's digits. Seen on an Android 15
     * emulator set to Arabic: Noor in English showed "−٣:٠٤:٢٣" in the notification.
     */
    @Test fun noorsDigitsWhateverTheDefaultLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            for (tag in listOf("ar-SA", "ar-EG", "fa-IR", "en-US")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
                val w = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
                assertEquals(tag, "1:07:05", w.countdown(Duration.ofSeconds(3600 + 7 * 60 + 5)))
                assertEquals(tag, "12:05" to "PM", w.time(12 * 60 + 5))
                val a = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H24)
                assertEquals(tag, "١:٠٧:٠٥", a.countdown(Duration.ofSeconds(3600 + 7 * 60 + 5)))
                assertEquals(tag, "١٧:٤٠" to null, a.time(17 * 60 + 40))
            }
        } finally { java.util.Locale.setDefault(saved) }
        // Persian digits are read as digits too.
        assertEquals("2026", Formatter(false, Numerals.WESTERN, TimeFormat.H12).digits("۲۰۲۶"))
    }

    @Test fun countdown() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("1:07:05", f.countdown(Duration.ofSeconds(3600 + 7 * 60 + 5)))
        assertEquals("7:05", f.countdown(Duration.ofSeconds(7 * 60 + 5)))
        assertEquals(1L to 8L, f.hoursMinutes(Duration.ofSeconds(3600 + 7 * 60 + 5)))
    }

    @Test fun timerReadsLikeAndroidsChronometer() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("1:07:05", f.timer(Duration.ofSeconds(3600 + 7 * 60 + 5)))
        assertEquals("07:05", f.timer(Duration.ofSeconds(7 * 60 + 5)))
        assertEquals("00:00", f.timer(Duration.ofSeconds(-3)))
    }

    @Test fun degreesHaveExactlyTwoDecimals() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("\u200E259.39°\u200E", f.degrees(259.3912))
        assertEquals("\u200E0.05°\u200E", f.degrees(0.049))
        assertEquals("\u200E+\u200E83.49°\u200E", f.signedDegrees(83.49))
        val a = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)
        assertEquals("\u200E٢٥٩٫٣٩°\u200E", a.degrees(259.39))
        assertEquals("1,633 km", f.kilometres(1633.169))
    }

    @Test fun soundLengthsReadNaturally() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("0.5 s", f.duration(0.45))
        assertEquals("22 s", f.duration(22.4))
        assertEquals("2:56", f.duration(176.5))
        val a = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)
        assertEquals("٠٫٥ ث", a.duration(0.45))
        assertEquals("٤:٠٣", a.duration(242.9))
    }
}
