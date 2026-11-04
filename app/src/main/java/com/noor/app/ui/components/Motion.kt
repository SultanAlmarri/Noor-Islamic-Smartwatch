package com.noor.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
}

/** False when ambient (endless) animations must not run. */
val LocalAmbientMotion = compositionLocalOf { true }

@Composable
fun systemAllowsAmbientMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
}
