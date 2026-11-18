package com.noor.app.testing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noor.app.data.Scenery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The tester's options (0.14): what the owner asked for while the app is being tested, "where I can control the
 * seasons and times and location ... so I can see how everything looks in all the situations".
 *
 * FOR TESTING ONLY. To be removed before the app is released (the owner's word): this object, the Testing screen in
 * Settings, and the few places that read it (search for `Tester.`).
 *
 * Nothing here is stored: every option is back to "as it really is" when the app is started again, and whenever
 * [reset] is called. Nothing here touches the prayer times, the alerts or the watch: only what the home screen's sky
 * shows. The countdown and the list of times always stay real.
 */
object Tester {
    /** Any option below counts only while this is on. */
    var on by mutableStateOf(false)

    /** The time of day the sky shows, as a fraction of the day (0 = midnight, 0.5 = noon); null: the clock. */
    var time by mutableStateOf<Float?>(null)
    /** The day runs by itself, one whole day in [daySeconds]. */
    var playing by mutableStateOf(false)
    var daySeconds by mutableFloatStateOf(90f)

    /** The time of year the land and the weather are shown in (0 = midwinter .. 0.5 = midsummer); null: as in Settings. */
    var year by mutableStateOf<Float?>(null)

    /** The kind of weather (0 clear, 1 fair, 2 convective, 3 stormy, 4 layered, 5 overcast, 6 rainy, 7 mixed); null: the day's own. */
    var weather by mutableStateOf<Int?>(null)
    /** Another day's sky of the same kind: counts up for "another one". */
    var weatherDay by mutableIntStateOf(0)

    /** Morning fog, whatever the morning: 0 none .. 1 the thickest; null: the day's own (some mornings have it). */
    var fog by mutableStateOf<Float?>(null)

    /** What the demo is showing now, for the banner on the home screen; null when it is not running. */
    var demoLabel by mutableStateOf<String?>(null)

    val SCENERY_NAMES = mapOf(
        Scenery.DUNES to "Dunes", Scenery.RAINFOREST to "Forest lake", Scenery.ARCTIC to "Arctic", Scenery.SEA to "Beach",
        Scenery.SAVANNA to "Green hills", Scenery.CANYON to "Red rock",
    )
    val WEATHER_NAMES = listOf("Clear", "Fair", "Building clouds", "Stormy", "Layered", "Overcast", "Rainy", "Mixed")
    val SEASONS = listOf("Winter" to 0.085f, "Spring" to 0.335f, "Summer" to 0.585f, "Autumn" to 0.865f)

    /** The moment of the day the sky shows: [real] unless the tester has set one. */
    fun shown(real: Double): Double = if (on) time?.toDouble() ?: real else real

    /** True while the sky must be drawn at every frame because the tester is moving it. */
    val moving: Boolean get() = on && (playing || demoLabel != null)

    fun reset() {
        stopDemo()
        NotificationTests.stop()
        on = false; time = null; playing = false; year = null; weather = null; weatherDay = 0; fog = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Runs [block] for as long as the app lives (the demo changes the scenery after the Testing page has gone). */
    fun launch(block: suspend () -> Unit) { scope.launch { block() } }
    private var clock: Job? = null
    private var demo: Job? = null
    /** Counts the demos started: only the latest gives the scenery back when it ends. */
    private var demoRun = 0

    /** Starts or stops the day running by itself. */
    fun play(run: Boolean) {
        playing = run
        clock?.cancel(); clock = null
        if (!run) return
        on = true
        clock = scope.launch {
            var last = awaitFrame()
            while (isActive) {
                val t = awaitFrame()
                val dt = ((t - last) / 1e9f).coerceIn(0f, 0.1f); last = t
                time = (((time ?: 0.25f) + dt / daySeconds.coerceAtLeast(5f)) % 1f)
            }
        }
    }

    /**
     * The demo: every scenery in turn, each through a whole day (dawn to deep night) in a season and a weather of its
     * own, so that everything the sky can do passes by in a few minutes. [setScenery] changes the scenery in Settings;
     * [restore] is the scenery to go back to when the demo ends or is stopped.
     */
    fun startDemo(context: android.content.Context, restore: Scenery, setScenery: suspend (Scenery) -> Unit) {
        // (0.22) The user's own scenery is kept on disk while the demo runs: until 0.22 it lived only in the demo's
        // coroutine, so "Start again" during a demo took the demo's scenery of the moment for the user's, and an app
        // closed by Android during a demo never put it back. [restoreAfterDemo] puts it back at the next start.
        val prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
        val own = prefs.getString(K_RESTORE, null)?.let { n -> Scenery.entries.firstOrNull { it.name == n } } ?: restore
        prefs.edit().putString(K_RESTORE, own.name).apply()
        val mine = ++demoRun
        stopDemo()
        on = true
        demo = scope.launch {
            try {
                // (Scenery, season, weather: chosen so that each scenery's own character and each kind of sky is seen.)
                val tour = listOf(
                    Triple(Scenery.DUNES, 1, 1), Triple(Scenery.RAINFOREST, 3, 4), Triple(Scenery.ARCTIC, 0, 0),
                    Triple(Scenery.SEA, 2, 2), Triple(Scenery.SAVANNA, 1, 7), Triple(Scenery.CANYON, 3, 3),
                    Triple(Scenery.SAVANNA, 3, 1), Triple(Scenery.RAINFOREST, 0, 5), Triple(Scenery.SEA, 2, 6),
                )
                for ((n, stop) in tour.withIndex()) {
                    val (scenery, season, kind) = stop
                    setScenery(scenery)
                    year = SEASONS[season].second; weather = kind; weatherDay = n
                    demoLabel = "Demo ${n + 1}/${tour.size}: ${SCENERY_NAMES[scenery] ?: scenery.name}, ${SEASONS[season].first.lowercase()}, ${WEATHER_NAMES[kind].lowercase()}"
                    // From before dawn through the day into the night: 0.18 .. 0.98 of the day in 40 seconds, slower
                    // through sunrise and sunset, which are what one wants to look at.
                    var f = 0.18f
                    time = f
                    delay(1500)                                   // (the scenery's pictures are read)
                    var last = awaitFrame()
                    while (isActive && f < 0.98f) {
                        val t = awaitFrame()
                        val dt = ((t - last) / 1e9f).coerceIn(0f, 0.1f); last = t
                        val nearHorizon = minOf(kotlin.math.abs(f - 0.27f), kotlin.math.abs(f - 0.73f)) < 0.06f
                        f += dt / (if (nearHorizon) 110f else 34f)
                        time = f
                    }
                }
            } finally {
                // (Only the latest demo ends things: one stopped by "Start again" leaves them to the new one.)
                if (demoRun == mine) {
                    demoLabel = null; time = null; year = null; weather = null; weatherDay = 0
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        setScenery(own)
                        prefs.edit().remove(K_RESTORE).apply()
                    }
                }
            }
        }
    }

    fun stopDemo() { demo?.cancel(); demo = null }

    private const val PREFS = "noor_tester"
    private const val K_RESTORE = "demo_restore_scenery"

    /** At the app's start: a demo the app was closed during gives the user's own scenery back ([startDemo]). */
    suspend fun restoreAfterDemo(context: android.content.Context, setScenery: suspend (Scenery) -> Unit) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
        val own = prefs.getString(K_RESTORE, null)?.let { n -> Scenery.entries.firstOrNull { it.name == n } } ?: return
        if (demo != null) return
        setScenery(own)
        prefs.edit().remove(K_RESTORE).apply()
    }
}
