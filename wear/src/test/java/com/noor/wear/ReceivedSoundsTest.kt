package com.noor.wear

import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertMode
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.SoundIds
import com.noor.wear.data.ReceivedSounds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 0.21: the Adhans the phone sends the watch stay there while the watch's alerts use them. With "Random", the phone
 * sends every voice it may pick (WatchSoundSender.needed); until 0.20 the next settings to arrive deleted them all
 * (only the prayer's own id, "random.favourites", was kept), and the watch played the Adhan's opening instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceivedSoundsTest {
    @Test fun theVoicesOfRandomAreKept() {
        val app = RuntimeEnvironment.getApplication()
        val dir = ReceivedSounds.dir(app).apply { mkdirs() }
        val files = listOf("adhan.madinah", "adhan.egypt", "adhan.dubai_fajr", "custom.abc", "adhan.old").map { File(dir, "$it.ogg").apply { writeText("x") } }
        val favourites = listOf("adhan.madinah", "adhan.egypt", "adhan.dubai_fajr")
        val random = Prayer.entries.fold(AlertSettings(enabled = true, device = AlertDevice.WATCH, favourites = favourites)) { s, p ->
            if (p == Prayer.SHURUQ) s else s.with(p, s.alert(p).copy(mode = AlertMode.ADHAN, soundId = SoundIds.RANDOM))
        }.let { it.with(Prayer.ASR, it.alert(Prayer.ASR).copy(mode = AlertMode.CUSTOM, soundId = "custom.abc")) }
        ReceivedSounds.prune(app, TestConfigs.dubai().copy(alerts = random))
        assertEquals(setOf("adhan.madinah", "adhan.egypt", "adhan.dubai_fajr", "custom.abc"), ReceivedSounds.ids(app))

        // Alerts moved back to the phone alone: the watch lets them all go.
        ReceivedSounds.prune(app, TestConfigs.dubai().copy(alerts = random.copy(device = AlertDevice.PHONE)))
        assertEquals(emptySet<String>(), ReceivedSounds.ids(app))
        files.forEach { it.delete() }
    }
}
