package com.noor.app

import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlertSettings
import com.noor.prayer.alerts.PrayerAlarm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.26: on the first start of 0.26 every rise is turned off (the owner: the Adhan "on its normal volume from the
 * beginning"), whatever its length; afterwards "Start softly" is each prayer's choice and is never undone again.
 * (One test method: DataStore allows one instance per process.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdhanMigrationTest {
    @Test fun everyRiseIsTurnedOffOnceAndOnlyOnce() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        val repo = app.container.settings
        val prefs = app.getSharedPreferences("noor_migrations", 0)
        // A phone that 0.25 left rising over 20 seconds (which the 0.24.1 change, for 10 only, did not touch).
        val old = AlertSettings.decode("v=1;on=1;dev=PHONE;grad=20;dnd=1;quiet=0;FAJR=1,ADHAN,adhan.dubai_fajr,70,1,0")
            .withAlarm(PrayerAlarm(0, Prayer.FAJR, 25, after = false))
        runBlocking {
            // (The app runs the change itself as it starts, in the background: let that run end first, then start again
            // from 0.25's state.)
            val until = System.currentTimeMillis() + 20_000
            while (!prefs.getBoolean("adhan_full_from_start_026", false) && System.currentTimeMillis() < until) Thread.sleep(50)
            assertTrue("the app ran the change as it started", prefs.getBoolean("adhan_full_from_start_026", false))
            prefs.edit().clear().commit()
            repo.updateAlerts { old }
            assertTrue(Prayer.entries.all { repo.current().alerts.alert(it).gradualSeconds == 20 })
            adhanMigrations(app, repo)
        }
        val now = runBlocking { repo.current().alerts }
        assertEquals(0, now.gradualSeconds)
        assertTrue("every prayer at full volume from the start", Prayer.entries.all { now.alert(it).gradualSeconds == 0 })
        // Nothing else is changed.
        assertEquals(70, now.alert(Prayer.FAJR).volumePercent)
        assertEquals(old.alarms, now.alarms)
        assertTrue(now.enabled)
        // A rise chosen afterwards is the user's: the change does not run again.
        runBlocking {
            repo.updateAlerts { it.with(Prayer.ASR, it.alert(Prayer.ASR).copy(gradualSeconds = 10)) }
            adhanMigrations(app, repo)
            assertEquals(10, repo.current().alerts.alert(Prayer.ASR).gradualSeconds)
        }
        assertTrue(prefs.getBoolean("adhan_full_from_start_026", false))
    }
}
