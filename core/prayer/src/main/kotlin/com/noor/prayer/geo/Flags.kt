package com.noor.prayer.geo

/**
 * Country flags for display, shared by phone and watch so the mapping is identical everywhere.
 *
 * Product decision (owner's requirement): for locations identified as Israel (IL) the Palestinian flag is shown.
 * Only the flag changes; the place name and the country code used for calculations are left as identified.
 */
object Flags {
    private val displayOverrides = mapOf("IL" to "PS")

    /** The country whose flag is shown for [countryCode] (ISO 3166-1 alpha-2), or null if the code is not valid. */
    fun displayCountry(countryCode: String?): String? {
        val cc = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } } ?: return null
        return displayOverrides[cc] ?: cc
    }

    /** Flag emoji (two regional-indicator symbols) for [countryCode], or null if unknown. */
    fun emoji(countryCode: String?): String? {
        val cc = displayCountry(countryCode) ?: return null
        return buildString { cc.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
    }
}
