package com.noor.wear

import com.noor.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WearTextTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val asr = ZonedDateTime.of(2026, 11, 4, 15, 14, 0, 0, zone).toInstant()

    @Test fun englishTwelveHour() {
        val t = WearText(TestConfigs.dubai("en"))
        assertEquals("3:14 PM", t.time(asr, zone))
        assertEquals("Asr", t.name(Prayer.ASR))
        assertEquals("23:07", t.countdown(Duration.ofSeconds(23 * 60 + 7)))
        assertEquals("1:02:03", t.countdown(Duration.ofSeconds(3723)))
        assertEquals("1 h 3 min", t.remaining(Duration.ofSeconds(3723)))
    }

    @Test fun arabicWithArabicIndicDigits() {
        val t = WearText(TestConfigs.dubai("ar", arabicDigits = true))
        assertEquals("٣:١٤ م", t.time(asr, zone))
        assertEquals("العصر", t.name(Prayer.ASR))
        assertEquals("دبي", t.place(TestConfigs.dubai()))
        assertEquals("٢٣:٠٧", t.countdown(Duration.ofSeconds(23 * 60 + 7)))
    }

    @Test fun followsWatchLanguageWhenPhoneSaysSystem() {
        assertEquals(true, WearText(TestConfigs.dubai(""), Locale.forLanguageTag("ar")).arabic)
        assertEquals(false, WearText(TestConfigs.dubai(""), Locale.ENGLISH).arabic)
        assertEquals(false, WearText(null, Locale.ENGLISH).arabic)
    }

    @Test fun twentyFourHour() {
        val t = WearText(TestConfigs.dubai().copy(use24h = true))
        assertEquals("15:14", t.time(asr, zone))
    }
}
