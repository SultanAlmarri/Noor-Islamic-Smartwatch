package com.noor.app.sky

import com.noor.app.data.SkyGraphics
import org.junit.Assert.assertEquals
import org.junit.Test

/** (0.19) The graphics levels: what automatic chooses for a phone, and how an older choice is carried over. */
class SkyLevelsTest {
    @Test fun automaticGuessesFromThePhone() {
        // A current phone with OpenGL ES 3 and memory to spare: the real sky.
        assertEquals(SkyLevel.HIGH, SkyLevels.guess(sdk = 34, memGb = 8.0, lowRam = false, es3 = true))
        assertEquals(SkyLevel.HIGH, SkyLevels.guess(sdk = 29, memGb = 4.0, lowRam = false, es3 = true))
        // Little memory, but the 0.9 sky's GPU shaders (Android 13): the medium graphics.
        assertEquals(SkyLevel.MEDIUM, SkyLevels.guess(sdk = 33, memGb = 3.0, lowRam = false, es3 = true))
        // No OpenGL ES 3 (the real sky cannot run) on Android 13: medium.
        assertEquals(SkyLevel.MEDIUM, SkyLevels.guess(sdk = 34, memGb = 6.0, lowRam = false, es3 = false))
        // A low-memory device, very little memory, or an old phone without the GPU shaders: the simplest sky.
        assertEquals(SkyLevel.LOW, SkyLevels.guess(sdk = 34, memGb = 8.0, lowRam = true, es3 = true))
        assertEquals(SkyLevel.LOW, SkyLevels.guess(sdk = 30, memGb = 2.0, lowRam = false, es3 = true))
        assertEquals(SkyLevel.LOW, SkyLevels.guess(sdk = 30, memGb = 3.0, lowRam = false, es3 = true))
        assertEquals(SkyLevel.LOW, SkyLevels.guess(sdk = 26, memGb = 6.0, lowRam = false, es3 = false))
    }

    @Test fun anOlderChoiceIsCarriedOver() {
        // 0.10-0.18: Ultra, High, Medium and Low were steps of the real sky; Classic was the 0.9 sky.
        assertEquals(SkyGraphics.AUTO, SkyGraphics.fromOld(null))
        assertEquals(SkyGraphics.AUTO, SkyGraphics.fromOld("AUTO"))
        for (n in listOf("ULTRA", "HIGH", "MEDIUM", "LOW")) assertEquals(n, SkyGraphics.HIGH, SkyGraphics.fromOld(n))
        assertEquals(SkyGraphics.MEDIUM, SkyGraphics.fromOld("CLASSIC"))
    }
}
