package com.noor.app.ui.qibla

import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.util.Formatter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.25: the Qibla page shows the phone's compass in whole degrees (it is good to a few degrees at best), and the
 * Qibla's own calculated bearing once, to a tenth. A direction never reads 360.
 */
class QiblaDegreesTest {
    private val f = Formatter(false, Numerals.WESTERN, TimeFormat.H12)
    private val a = Formatter(true, Numerals.ARABIC_INDIC, TimeFormat.H12)
    private val lrm = "‎"

    @Test fun wholeDegrees() {
        assertEquals("${lrm}258°$lrm", f.wholeDegrees(258.31))
        assertEquals("${lrm}175°$lrm", f.wholeDegrees(174.82))
        assertEquals("${lrm}83°$lrm", f.wholeDegrees(83.49))
        assertEquals("${lrm}0°$lrm", f.wholeDegrees(359.6))
        assertEquals("${lrm}0°$lrm", f.wholeDegrees(0.2))
        assertEquals("${lrm}359°$lrm", f.wholeDegrees(-0.6))
        assertEquals("${lrm}٢٥٨°$lrm", a.wholeDegrees(258.31))
    }

    @Test fun theQiblasOwnBearingToATenth() {
        assertEquals("${lrm}258.3°$lrm", f.tenthDegrees(258.31))
        assertEquals("${lrm}0.0°$lrm", f.tenthDegrees(359.97))
        assertEquals("${lrm}45.0°$lrm", f.tenthDegrees(45.04))
        assertEquals("${lrm}٢٥٨٫٣°$lrm", a.tenthDegrees(258.31))
    }
}
