package com.noor.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 0.18: a light tick under the finger for every touch that does something (the owner: "I don't like the lack of
 * vibration in this app"): [onClick] with the system's own click feedback first. It follows the phone's "touch
 * feedback" setting, so a user who turned vibration off for touches feels nothing.
 */
@Composable
fun withTick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onClick)
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); action() } }
}

/** The same for a choice that takes a value (a switch, a day, a segment). */
@Composable
fun <T> withTick(onChange: (T) -> Unit): (T) -> Unit {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onChange)
    return remember(haptics) { { v: T -> haptics.performHapticFeedback(HapticFeedbackType.ContextClick); action(v) } }
}
