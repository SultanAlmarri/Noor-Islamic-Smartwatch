package com.noor.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.21: a recording the user replaces (same id, new file) reaches the watch again; nothing else is sent twice. */
class WatchSoundSenderTest {
    @Test fun aReplacedRecordingIsSentAgain() {
        val want = setOf("adhan.madinah", "custom.a", "custom.b")
        val have = setOf("adhan.madinah", "custom.a")
        // What the watch lacks.
        assertEquals(setOf("custom.b"), WatchSoundSender.toSend(want, have, emptySet()))
        // A replaced recording it has: sent again (until 0.20 the watch kept playing the old file).
        assertEquals(setOf("custom.a", "custom.b"), WatchSoundSender.toSend(want, have, setOf("custom.a")))
        // A replaced recording no prayer on the watch uses: not sent.
        assertEquals(setOf("custom.b"), WatchSoundSender.toSend(want, have, setOf("custom.z")))
    }
}
