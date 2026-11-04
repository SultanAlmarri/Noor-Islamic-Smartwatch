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

    @Test fun countdown() {
        val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
        assertEquals("1:07:05", f.countdown(Duration.ofSeconds(3600 + 7 * 60 + 5)))
        assertEquals("7:05", f.countdown(Duration.ofSeconds(7 * 60 + 5)))
        assertEquals(1L to 8L, f.hoursMinutes(Duration.ofSeconds(3600 + 7 * 60 + 5)))
    }
}
