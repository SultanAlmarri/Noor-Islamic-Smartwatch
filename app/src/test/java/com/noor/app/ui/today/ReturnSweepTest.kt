package com.noor.app.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.noor.app.NoorApplication
import com.noor.app.data.PrayerProvider
import com.noor.app.design.RenderHarness
import com.noor.app.ui.opening.Opening
import com.noor.app.ui.opening.OpeningMode
import com.noor.app.ui.opening.OpeningState
import com.noor.prayer.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Back after a night away: the app comes in again (no veil) and the Sun runs to now. For a frame or two the screen
 * still has the state of the evening before (the view model's last one, until its fresh one is ready). Until 0.22 the
 * Sun ran to that evening's moment and stayed there until the next minute turned, then jumped to the morning: the
 * sky showed night for up to a minute. The sweep must end on this moment.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ReturnSweepTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theSunRunsToThisMomentNotToTheOneTheAppLeftAt() {
        val tables = (RuntimeEnvironment.getApplication() as NoorApplication).container.timetables
        val s = RenderHarness.DUBAI
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val cache = DayCache(tables)
        fun ui(at: Instant): TodayUi {
            val state = Schedule.state(at, place.zone) { cache.get(s, place, profile, it) }
            return TodayUi(s, place, profile, PrayerProvider.authority(s, place), at, state, at.atZone(place.zone).toLocalDate(), refreshing = false, lastRefresh = null)
        }
        val dubai = ZoneId.of("Asia/Dubai")
        val evening = ZonedDateTime.of(2026, 11, 3, 22, 40, 0, 0, dubai).toInstant()
        val morning = ZonedDateTime.of(2026, 11, 4, 7, 10, 30, 0, dubai).toInstant()

        var u by mutableStateOf(ui(evening))
        // (As the app decides it: a moment on show is this moment's when it is this minute or the one before.)
        var clockNow by mutableStateOf(evening)
        val h = RenderHarness(rule)
        h.tabRoute = com.noor.app.Routes.PRAYERS
        h.screen = { pad -> TodayContent(u, pad, { cache.resolved(s, place, profile, it) }, {}, {}, {}, clock = { u.now }, fresh = { t -> !t.isBefore(clockNow.minusSeconds(120)) }) }
        h.ambient = true
        val opening = OpeningState()
        h.opening = opening
        rule.mainClock.autoAdvance = false
        h.start()
        rule.mainClock.advanceTimeBy(1_000)
        val eveningAt = SunPaths.of(evening.atZone(dubai).toLocalDate(), place.point, dubai).fraction(evening)
        assertEquals("before leaving, the sky shows the evening", eveningAt, OpeningSky.shownForTests, 0.002)

        // Back in the morning: the opening starts on the old state, and the fresh one comes two frames later.
        val morningAt = SunPaths.of(morning.atZone(dubai).toLocalDate(), place.point, dubai).fraction(morning.truncatedTo(ChronoUnit.MINUTES))
        rule.runOnUiThread { clockNow = morning; opening.begin(OpeningMode.FULL, veil = false, now = true) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { u = ui(morning) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        // Through the opening the Sun runs up to this morning, never on to last night's moment and back.
        var t = 0
        while (t < Opening.FULL_MS + 600) {
            rule.mainClock.advanceTimeBy(50); t += 50
            assertTrue("at $t ms the sky shows ${OpeningSky.shownForTests}, past this morning ($morningAt)", OpeningSky.shownForTests <= morningAt + 0.002)
        }
        assertEquals("after the opening (well within the minute), the sky shows this morning", morningAt, OpeningSky.shownForTests, 0.002)
    }
}
