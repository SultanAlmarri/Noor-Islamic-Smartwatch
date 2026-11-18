package com.noor.app.data

import android.location.Address
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The display rule (CLAUDE.md rule 9): a place identified as Israel (IL) is shown as Palestine, flag and name. Until 0.22
 * only the flag followed it: the country list, a place's confirmation and the search results said "Israel" under the
 * Palestinian flag (Android's own name for the country), and the Palestinian territories were "Palestinian Territories".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CountryNamesTest {
    @Test fun israelIsNamedPalestine() {
        assertEquals("Palestine", WorldStore.countryName("IL", false))
        assertEquals("فلسطين", WorldStore.countryName("IL", true))
        assertEquals("Palestine", WorldStore.countryName("PS", false))
        assertEquals("فلسطين", WorldStore.countryName("PS", true))
        assertEquals("United Arab Emirates", WorldStore.countryName("AE", false))
        // The phone's geocoder names the country itself: the same rule, in the geocoder's language.
        fun address(cc: String, name: String, locale: Locale) = Address(locale).apply { countryCode = cc; countryName = name }
        assertEquals("Palestine", WorldStore.countryName(address("IL", "Israel", Locale.ENGLISH)))
        assertEquals("فلسطين", WorldStore.countryName(address("IL", "إسرائيل", Locale.forLanguageTag("ar"))))
        assertEquals("United Arab Emirates", WorldStore.countryName(address("AE", "United Arab Emirates", Locale.ENGLISH)))
    }
}
