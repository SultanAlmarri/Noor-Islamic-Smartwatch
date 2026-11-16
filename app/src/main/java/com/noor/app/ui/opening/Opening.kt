package com.noor.app.ui.opening

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.sky.RealSky
import com.noor.app.ui.theme.Sky
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * How Noor comes in (0.14).
 *
 * - [FULL]: first light. The app's launch screen (dusk blue, Noor's crescent and star) does not cut to the home screen:
 *   light gathers round the mark, the mark dissolves into it, and the dusk opens outward from that point like an iris
 *   onto the home screen, where meanwhile the sky runs from the start of the current prayer's time to this moment
 *   (the sweep of 0.9.6), the place, the prayer and the countdown settle into the sky, the day's times come up one
 *   after another and the tab bar rises. About two seconds; a touch anywhere finishes it at once.
 * - [BRIEF]: the launch screen simply dissolves (first run, where the welcome page has its own sunrise).
 * - [NONE]: nothing moves: "Remove animations" is on, or Noor was already open.
 */
enum class OpeningMode { NONE, BRIEF, FULL }

/** The opening's timetable, in milliseconds from the moment the launch screen hands over. */
object Opening {
    /** The light round the mark. */
    const val GLOW_MS = 950
    /** The mark dissolves into the light. */
    const val MARK_FADE_AT = 150
    const val MARK_FADE_MS = 330
    /** The light leaves the mark for the place of the Sun in the sky behind. */
    const val TRAVEL_AT = 120
    const val TRAVEL_MS = 560
    /** The dusk opens. */
    const val IRIS_AT = 300
    const val IRIS_MS = 1000
    /** From here the veil is gone and touches reach the home screen. */
    const val VEIL_END = IRIS_AT + IRIS_MS
    /** The sky's own sweep starts as the dusk begins to open, and takes this long (by how far the Sun has to run). */
    const val SKY_AT = IRIS_AT
    const val SKY_MIN_MS = 1500
    const val SKY_MAX_MS = 1900
    /** The place, the prayer and the countdown settle into the sky, one after another. */
    const val HEADER_AT = 520
    /** The date and the day's times come up, one after another. */
    const val PANEL_AT = 760
    /** One step of a cascade, and how long each piece takes to settle. */
    const val STEP_MS = 55
    const val RISE_MS = 520
    /** The tab bar rises. */
    const val TAB_AT = 640
    const val FULL_MS = SKY_AT + SKY_MAX_MS + 60
    const val BRIEF_MS = 460

    /** When Noor comes back after this long in the background, the home screen comes in again (without the veil). */
    const val RETURN_AFTER_MS = 15 * 60_000L
    /** How long the dusk may wait, closed, for the real sky's first picture (ms). */
    const val HOLD_MAX_MS = 700f
    /** Without the veil there is nothing to wait for: the home screen's parts come in this much earlier. */
    const val NO_VEIL_LEAD_MS = 500

    /** Quick off the mark, a long soft landing: things arrive, they do not bounce. */
    val Settle: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Once per process: a cold start opens in full; the activity being rebuilt (language, theme) does not. */
    @Volatile var playedInThisProcess = false

    /** What a start of the activity gets. [home]: it starts on the home screen (not on the welcome page). */
    fun modeAtStart(animations: Boolean, home: Boolean): OpeningMode {
        if (playedInThisProcess) return OpeningMode.NONE
        playedInThisProcess = true
        return when { !animations -> OpeningMode.NONE; home -> OpeningMode.FULL; else -> OpeningMode.BRIEF }
    }

    /** Whether the home screen comes in again when Noor returns from the background. */
    fun replaysOnReturn(awayMs: Long, animations: Boolean, onHome: Boolean): Boolean = animations && onHome && awayMs >= RETURN_AFTER_MS

    /** The sweep's length: longer for a longer run of the sky ([start] and [now] are fractions of the day). */
    fun skyMs(start: Float, now: Float): Int = (SKY_MIN_MS + (SKY_MAX_MS - SKY_MIN_MS) * ((now - start) / 0.2f).coerceIn(0f, 1f)).toInt()
}

/**
 * The opening as the screens see it: one clock, started when the launch screen hands over, from which every part
 * takes its own share with [part]. Read [part] in a draw or layer block, so the opening redraws and never recomposes.
 */
@Stable
class OpeningState(mode: OpeningMode = OpeningMode.NONE, veil: Boolean = true) {
    var mode by mutableStateOf(mode)
        private set
    /** The dusk veil over the screen (a cold start); without it only the home screen's own parts come in (a return). */
    var veil by mutableStateOf(veil)
        private set
    /** The home screen is the page on show (told by the app's frame; a return replays the opening only there). */
    @Volatile var onHome = false
    /** Counts the openings, so each part can start again with each. */
    var run by mutableIntStateOf(0)
        private set
    private val elapsed = mutableFloatStateOf(0f)
    private var started by mutableStateOf(false)
    /** A touch asked for the end: the rest runs at eight times the speed, so nothing cuts. */
    var skipped by mutableStateOf(false)
        private set
    /** Where the launch screen drew Noor's mark, in the window's pixels (null: the middle of the screen). */
    var mark: Rect? by mutableStateOf(null)
    /** Where the light goes and the dusk opens from: the Sun of the home screen's sky, told by the home screen (px). */
    var focus: Offset? by mutableStateOf(null)
    private var skyRun = -1

    val totalMs: Int get() = when (mode) { OpeningMode.FULL -> Opening.FULL_MS; OpeningMode.BRIEF -> Opening.BRIEF_MS; OpeningMode.NONE -> 0 }
    val elapsedMs: Float get() = elapsed.floatValue
    // (Derived: who reads these is told when they turn, not at every frame of the clock they are worked out from.)
    /** Something is still moving. */
    val active: Boolean by derivedStateOf { this.mode != OpeningMode.NONE && elapsed.floatValue < totalMs }
    /** The home screen's parts are coming in (place, countdown, times, sky). */
    val arriving: Boolean by derivedStateOf { this.mode == OpeningMode.FULL && elapsed.floatValue < totalMs }
    /** The veil is on the screen (and takes the touches). */
    val veiled: Boolean by derivedStateOf {
        this.veil && this.mode != OpeningMode.NONE && elapsed.floatValue < (if (this.mode == OpeningMode.FULL) Opening.VEIL_END else Opening.BRIEF_MS)
    }

    /** 0 → 1 over [durationMs] from [startMs] of a full opening, eased; 1 when there is none. */
    fun part(startMs: Int, durationMs: Int, easing: Easing = Opening.Settle): Float {
        if (mode != OpeningMode.FULL) return 1f
        return easing.transform(((elapsed.floatValue - startMs) / durationMs).coerceIn(0f, 1f))
    }

    /** The launch screen has gone (or there was none): the clock may run. */
    fun start() { started = true }

    /** A touch: finish now. */
    fun skip() { if (active) skipped = true }

    /**
     * Sets an opening up from its beginning. After a cold start its clock waits for [start] (the launch screen's
     * hand-over); with [now] it runs at once (Noor returns after a long while: the home screen comes in again).
     */
    fun begin(mode: OpeningMode, veil: Boolean = true, now: Boolean = false) {
        this.mode = mode; this.veil = veil
        elapsed.floatValue = 0f; skipped = false; started = now
        run++
    }

    /** The sky's sweep belongs to an opening once (the home screen may be left and come back while it runs). */
    fun claimSky(): Boolean { if (mode != OpeningMode.FULL || skyRun == run) return false; skyRun = run; return true }

    /** Runs the clock to the end of this opening. */
    suspend fun drive() {
        if (mode == OpeningMode.NONE) return
        // The system hands the launch screen over a moment after the first frame; if it never says so, go anyway.
        if (!started) withTimeoutOrNull(450) { snapshotFlow { started }.first { it } }
        started = true
        var last = withFrameNanos { it }
        var held = 0f
        while (elapsed.floatValue < totalMs) {
            val now = withFrameNanos { it }
            // (A frame that took long, the first ones of a cold start, does not eat the animation: at most 34 ms a frame.)
            val dt = ((now - last) / 1e6f).coerceIn(0f, 34f)
            last = now
            var next = (elapsed.floatValue + dt * if (skipped) 8f else 1f).coerceAtMost(totalMs.toFloat())
            // The dusk does not open before there is a scene behind it: while the real sky is still reading its first
            // picture the clock waits where the iris would begin, for at most HOLD_MAX_MS (then it opens anyway, onto
            // the waiting sky, as before 0.14). A touch ends the wait.
            if (veil && mode == OpeningMode.FULL && !skipped && elapsed.floatValue <= Opening.IRIS_AT && next > Opening.IRIS_AT &&
                com.noor.app.sky.RealSky.waitingForPicture.value && held < Opening.HOLD_MAX_MS) { held += dt; next = Opening.IRIS_AT.toFloat() }
            elapsed.floatValue = next
        }
    }

    /** For tests and previews: the clock put at [ms]. */
    internal fun seek(ms: Float) { started = true; elapsed.floatValue = ms }

    companion object {
        /** No opening: everything in its place. */
        val None = OpeningState(OpeningMode.NONE)
    }
}

val LocalOpening = staticCompositionLocalOf { OpeningState.None }

/** The colour of the launch screen and of the veil: the sky that holds the scene's place until its first picture. */
val Dusk: Color get() = RealSky.WAITING_SKY
private val GlowCore = Color(0xFFFFF6E0)
private val GlowEdge = Color(0xFFF2A65A)

/**
 * A part of the home screen that settles into place during the opening: it fades in and rises [lift], the [order]-th
 * of its cascade starting at [startMs]. Outside an opening it is nothing at all (no layer).
 */
@Composable
fun Modifier.arrives(order: Int, startMs: Int, lift: Dp = 18.dp): Modifier {
    val o = LocalOpening.current
    if (!o.arriving) return this
    return graphicsLayer {
        val lead = if (o.veil) 0 else Opening.NO_VEIL_LEAD_MS
        val p = o.part((startMs - lead).coerceAtLeast(0) + order * Opening.STEP_MS, Opening.RISE_MS)
        alpha = p
        translationY = (1f - p) * lift.toPx()
    }
}

/**
 * The veil of the opening, over everything: see [OpeningMode.FULL]. It runs the opening's clock, takes the touches
 * while it is there (a touch finishes the opening), and leaves the composition as soon as the dusk has opened.
 */
@Composable
fun OpeningVeil(state: OpeningState, modifier: Modifier = Modifier) {
    LaunchedEffect(state, state.run) { state.drive() }
    if (!state.veiled) return
    val mark = painterResource(R.drawable.ic_splash_mark)
    val skip = stringResource(R.string.opening_skip)
    Canvas(
        modifier.fillMaxSize()
            // (Its own layer: the opening is cut out of the dusk, not painted over what is behind.)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .pointerInput(state) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); state.skip() } }
            .semantics { onClick(label = skip) { state.skip(); true } },
    ) {
        val box = state.mark ?: (288.dp.toPx()).let { s -> Rect(Offset((size.width - s) / 2f, (size.height - s) / 2f), Size(s, s)) }
        val c = box.center
        if (state.mode == OpeningMode.BRIEF) {
            val out = FastOutSlowInEasing.transform((state.elapsedMs / Opening.BRIEF_MS).coerceIn(0f, 1f))
            drawRect(Dusk, alpha = 1f - out)
            translate(box.left, box.top) { with(mark) { draw(box.size, alpha = (1f - out * 1.6f).coerceIn(0f, 1f)) } }
            return@Canvas
        }
        drawRect(Dusk)
        // The light gathers round the mark, then leaves it for the place of the Sun in the sky behind (or, at night,
        // the middle of the sky), and it is from there that the dusk opens: the mark's light becomes the day's.
        val to = state.focus ?: Offset(size.width / 2f, size.height * 0.3f)
        val travel = state.part(Opening.TRAVEL_AT, Opening.TRAVEL_MS, FastOutSlowInEasing)
        val at = Offset(c.x + (to.x - c.x) * travel, c.y + (to.y - c.y) * travel)
        val g = state.part(0, Opening.GLOW_MS, FastOutSlowInEasing)
        val glow = sin(g * Math.PI).toFloat()
        if (glow > 0f) {
            val r = box.width * (0.36f + 1.5f * g)
            drawCircle(
                Brush.radialGradient(0f to GlowCore.copy(alpha = 0.95f * glow), 0.1f to Sky.SunGlow.copy(alpha = 0.6f * glow), 0.42f to GlowEdge.copy(alpha = 0.2f * glow),
                    1f to Color.Transparent, center = at, radius = r),
                r, at, blendMode = BlendMode.Plus,
            )
        }
        // The mark dissolves into the light, growing a touch as it goes.
        val m = state.part(Opening.MARK_FADE_AT, Opening.MARK_FADE_MS, FastOutSlowInEasing)
        if (m < 1f) scale(1f + 0.14f * m, c) { translate(box.left, box.top) { with(mark) { draw(box.size, alpha = 1f - m) } } }
        // The dusk opens: a circle of nothing with a soft edge, growing until none of the veil is left.
        val o = state.part(Opening.IRIS_AT, Opening.IRIS_MS, FastOutSlowInEasing)
        if (o > 0f) {
            val reach = max(max(hypot(at.x, at.y), hypot(size.width - at.x, at.y)), max(hypot(at.x, size.height - at.y), hypot(size.width - at.x, size.height - at.y)))
            val r = o * reach / 0.62f
            drawCircle(Brush.radialGradient(0f to Color.Black, 0.62f to Color.Black, 1f to Color.Transparent, center = at, radius = r), r, at, blendMode = BlendMode.DstOut)
        }
    }
}
