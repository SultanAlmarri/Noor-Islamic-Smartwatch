package com.noor.wear.ui

import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.noor.prayer.dhikr.DhikrStep
import com.noor.prayer.dhikr.TasbihEvent
import com.noor.prayer.dhikr.TasbihPreset
import com.noor.prayer.dhikr.TasbihState
import com.noor.wear.R
import com.noor.wear.WearText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Settings the counter needs from outside; replaceable in tests. */
data class TasbihControls(
    val vibrate: Boolean = true,
    val today: () -> LocalDate = { LocalDate.now() },
    val onChange: (TasbihState) -> Unit = {},
    val onOpenPresets: () -> Unit = {},
)

/**
 * The dhikr counter. The whole screen is the button: tap anywhere (or turn the bezel/crown clockwise) to count,
 * counter-clockwise to take one back. A light tick for every count, a firmer pulse when a phrase is done.
 */
@Composable
fun TasbihScreen(initial: TasbihState, t: WearText, controls: TasbihControls) {
    var s by remember { mutableStateOf(initial) }
    var celebrate by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val haptics = remember(ctx) { Haptics(ctx) }
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun apply(result: Pair<TasbihState, TasbihEvent>, forward: Boolean) {
        val (next, e) = result
        if (e == TasbihEvent.NOTHING) { if (controls.vibrate) haptics.click(); return }
        s = next
        controls.onChange(next)
        if (controls.vibrate) when (e) {
            TasbihEvent.COUNT -> haptics.tick()
            TasbihEvent.MILESTONE -> haptics.heavy()
            TasbihEvent.STEP_DONE -> haptics.double()
            TasbihEvent.ROUND_DONE -> haptics.success()
            TasbihEvent.NOTHING -> Unit
        }
        if (e == TasbihEvent.ROUND_DONE) celebrate++
        if (forward) scope.launch { bump.snapTo(1.07f); bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    }
    val count: () -> Unit = { apply(s.increment(controls.today()), true) }
    val undo: () -> Unit = { apply(s.undo(controls.today()), false) }

    // One bezel click (or the same turn of a crown) is one count.
    val threshold = remember(ctx) { ViewConfiguration.get(ctx).scaledVerticalScrollFactor.coerceAtLeast(1f) * 0.9f }
    var rotary by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val countLabel = stringResource(R.string.tasbih_count_action)
    val spoken = phraseLabel(s.step, t) + ". " + if (s.target > 0) stringResource(R.string.tasbih_of, t.digits(s.count.toString()), t.digits(s.target.toString())) else t.digits(s.count.toString())

    ScreenScaffold { _ ->
        Box(
            Modifier.fillMaxSize()
                .onRotaryScrollEvent { e ->
                    rotary += e.verticalScrollPixels
                    while (rotary >= threshold) { rotary -= threshold; count() }
                    while (rotary <= -threshold) { rotary += threshold; undo() }
                    true
                }
                .focusRequester(focus).focusable()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = countLabel, onClick = count)
                .semantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { s.stepProgress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 300f, endAngle = 240f, strokeWidth = 6.dp,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = Gold, trackColor = Gold.copy(alpha = 0.14f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp).offset(y = 4.dp)) {
                Phrase(s.step, t)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        t.digits(s.count.toString()),
                        modifier = Modifier.scale(bump.value),
                        style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = Screen.sp(48f, 60f), fontFeatureSettings = "tnum", letterSpacing = (-1).sp,
                            lineHeight = Screen.sp(54f, 66f)),
                        color = Ink,
                    )
                }
                Text(
                    when {
                        s.target > 0 && s.preset.steps.size > 1 -> stringResource(R.string.tasbih_target_step, t.digits(s.target.toString()), t.digits((s.stepIndex + 1).toString()), t.digits(s.preset.steps.size.toString()))
                        s.target > 0 -> stringResource(R.string.tasbih_target, t.digits(s.target.toString()))
                        else -> stringResource(R.string.tasbih_free)
                    } + if (s.rounds > 0) "  ·  ×" + t.digits(s.rounds.toString()) else "",
                    style = MaterialTheme.typography.labelSmall, color = Mist, textAlign = TextAlign.Center, maxLines = 1,
                )
                Text(
                    stringResource(R.string.tasbih_today, t.count(s.todayOn(controls.today()).toLong())),
                    style = MaterialTheme.typography.labelSmall, color = Gold.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 1,
                )
            }
            // Undo and options flank the number at 9 and 3 o'clock, where a round screen is widest.
            val side = Screen.width() / 2 - 36.dp
            FilledTonalIconButton(onClick = undo, modifier = Modifier.offset(x = -side).size(IconButtonDefaults.SmallButtonSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Night, contentColor = Mist)) {
                Icon(painterResource(R.drawable.ic_undo), stringResource(R.string.tasbih_undo), Modifier.size(18.dp))
            }
            FilledTonalIconButton(onClick = controls.onOpenPresets, modifier = Modifier.offset(x = side).size(IconButtonDefaults.SmallButtonSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Night, contentColor = Mist)) {
                Icon(painterResource(R.drawable.ic_more), stringResource(R.string.tasbih_options), Modifier.size(18.dp))
            }
            RoundDone(celebrate, t)
        }
    }
}

/** A moment of acknowledgement when a round is complete; it fades by itself and never blocks counting. */
@Composable
private fun RoundDone(key: Int, t: WearText) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) { if (key > 0) { visible = true; delay(1600); visible = false } }
    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f), exit = fadeOut(tween(400))) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).background(Success.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(26.dp), tint = Success)
                }
                Text(stringResource(R.string.tasbih_round_done), style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.tasbih_round_done_body), style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The phrase being said: in Arabic script when the watch is in Arabic; in English, its transliteration with the meaning
 * beneath, so an English screen carries no Arabic text.
 */
@Composable
private fun Phrase(step: DhikrStep, t: WearText) {
    val d = step.dhikr
    if (d == null) {
        Text(stringResource(R.string.tasbih_free_title), style = MaterialTheme.typography.titleMedium, color = GoldSoft)
        return
    }
    val main = if (t.arabic) d.ar else d.translit
    val long = main.length > 24
    Text(
        main, style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = if (long) 11.sp else Screen.sp(16f, 19f), lineHeight = if (long) 15.sp else 24.sp),
        color = GoldSoft, textAlign = TextAlign.Center, maxLines = if (long) 3 else 1, overflow = TextOverflow.Ellipsis,
        modifier = if (long) Modifier.padding(horizontal = 6.dp) else Modifier,
    )
    if (!t.arabic && !long) Text(d.en, style = MaterialTheme.typography.labelSmall, color = Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun phraseLabel(step: DhikrStep, t: WearText): String = step.dhikr?.let { if (t.arabic) it.ar else it.translit } ?: ""

/** Choose what to count, start again, and see the totals. */
@Composable
fun TasbihOptionsScreen(s: TasbihState, t: WearText, vibrate: Boolean, today: LocalDate, onChoose: (TasbihPreset) -> Unit, onReset: () -> Unit, onVibrate: (Boolean) -> Unit) {
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.tasbih)) }
            TasbihPreset.entries.forEach { p ->
                item {
                    val first = p.steps.first().dhikr
                    NoorChip(
                        label = when {
                            p == TasbihPreset.AFTER_PRAYER -> stringResource(R.string.tasbih_after_prayer)
                            first == null -> stringResource(R.string.tasbih_free_title)
                            t.arabic -> first.ar
                            else -> first.translit
                        },
                        icon = if (p == s.preset) R.drawable.ic_check else null,
                        onClick = { onChoose(p) },
                        secondary = when {
                            p == TasbihPreset.AFTER_PRAYER -> t.digits("33 + 33 + 33 + 1")
                            p.roundTotal == 0 -> stringResource(R.string.tasbih_no_target)
                            t.arabic -> stringResource(R.string.tasbih_times, t.digits(p.roundTotal.toString()))
                            else -> first?.en + " · ×" + p.roundTotal
                        },
                        labelLines = 2,
                    )
                }
            }
            item { NoorChip(stringResource(R.string.tasbih_reset), R.drawable.ic_reset, onReset, iconTint = Mist) }
            item {
                SwitchButton(checked = vibrate, onCheckedChange = onVibrate, modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_vibrate), null) },
                    label = { Text(stringResource(R.string.tasbih_vibrate)) })
            }
            item {
                Footnote(stringResource(R.string.tasbih_totals, t.count(s.todayOn(today).toLong()), t.count(s.lifetime)) + "\n" + stringResource(R.string.tasbih_private))
            }
        }
    }
}
