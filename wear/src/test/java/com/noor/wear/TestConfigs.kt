package com.noor.wear

import com.noor.prayer.GeoPoint
import com.noor.prayer.HighLatitudeRule
import com.noor.prayer.Profiles
import com.noor.prayer.WatchConfig
import java.time.ZoneId
import java.time.ZonedDateTime

object TestConfigs {
    fun dubai(language: String = "en", arabicDigits: Boolean = false) = WatchConfig(
        GeoPoint(25.2048, 55.2708, 5.0), "Asia/Dubai", "Dubai", "دبي", false, Profiles.DUBAI_IACAD.id, null,
        HighLatitudeRule.ANGLE_BASED, emptyMap(), -1, language, arabicDigits, false, 0L, countryCode = "AE",
    )

    /** 4 Nov 2026, 14:51:00 in Dubai: Dhuhr is current, Asr (15:14) is next. */
    val afternoon = ZonedDateTime.of(2026, 11, 4, 14, 51, 0, 0, ZoneId.of("Asia/Dubai")).toInstant()
}
