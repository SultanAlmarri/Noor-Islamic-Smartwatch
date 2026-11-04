package com.noor.prayer

import com.noor.prayer.geo.Flags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlagsTest {
    @Test fun regionalIndicators() {
        assertEquals("🇦🇪", Flags.emoji("AE")) // UAE
        assertEquals("🇩🇪", Flags.emoji("de"))  // Germany, lower case accepted
        assertEquals("🇦🇺", Flags.emoji("AU"))
    }

    @Test fun israelShowsThePalestinianFlag() {
        assertEquals("PS", Flags.displayCountry("IL"))
        assertEquals(Flags.emoji("PS"), Flags.emoji("IL"))
        assertEquals("PS", Flags.displayCountry("PS"))
    }

    @Test fun invalidCodes() {
        assertNull(Flags.emoji(null)); assertNull(Flags.emoji("")); assertNull(Flags.emoji("UAE")); assertNull(Flags.emoji("1A"))
    }
}
