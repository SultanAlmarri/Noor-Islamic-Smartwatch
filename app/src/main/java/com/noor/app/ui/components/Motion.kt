package com.noor.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Motion rules. Every animation in Noor has one of three jobs: show where something came from (entrances),
 * confirm a change (state), or keep the sky alive (ambient: twinkling stars, the sun's glow). Ambient motion stops when
 * the user turns animations off in Android's accessibility settings, and in tests.
 */
object NoorMotion {
    /** Calm deceleration used for entrances and state changes. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val Quick = 180
    const val Standard = 320
    const val Entrance = 900
    /** A page pushed or popped (0.25): a touch longer, on [Glide]. */
    const val Page = 420
    /** Fast off the mark and a long, soft arrival, as a page settles on an iPhone. */
    val Glide = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)
}

/**
 * What is touched gives under the finger (0.25): it draws in a little while pressed and springs back when let go, as
 * a button of an iPhone. For cards and buttons that act; [source] is the same interaction source their click uses.
 * Nothing moves when the phone's animations are off.
 */
fun androidx.compose.ui.Modifier.pressScale(source: androidx.compose.foundation.interaction.InteractionSource, to: Float = 0.97f): androidx.compose.ui.Modifier = composed {
    if (!LocalAmbientMotion.current) return@composed this
    val pressed by source.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) to else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = if (pressed) 900f else 420f), label = "press")
    graphicsLayer { scaleX = k; scaleY = k }
}

/** False when ambient (endless) animations must not run. */
val LocalAmbientMotion = compositionLocalOf { true }

/** Whether Android's "Remove animations" is off. */
fun animationsOn(ctx: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f

/** Read again each time the app comes back, so a change made in Android's settings meanwhile is seen (0.14). */
@Composable
fun systemAllowsAmbientMotion(): Boolean {
    val ctx = LocalContext.current
    var on by remember(ctx) { androidx.compose.runtime.mutableStateOf(animationsOn(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(ctx) { on = animationsOn(ctx); onPauseOrDispose { } }
    return on
}
