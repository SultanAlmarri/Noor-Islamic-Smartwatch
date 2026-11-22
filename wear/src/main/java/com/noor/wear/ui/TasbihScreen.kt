package com.noor.wear.ui

import android.view.ViewConfiguration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
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
        if (forward) scope.launch { bump.snapTo(1.08f); bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
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
            // The phrase's progress round the edge; it glides to each count.
            Ring(s.stepProgress, Modifier.fillMaxSize().padding(4.dp), stroke = 6.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp).offset(y = 5.dp)) {
                Phrase(s.step, t)
                // The count as large as its digits allow between the two buttons.
                val digits = s.count.toString().length
                val size = when {
                    digits <= 2 -> Screen.sp(62f, 76f)
                    digits == 3 -> Screen.sp(46f, 58f)
                    digits == 4 -> Screen.sp(35f, 45f)
                    else -> Screen.sp(27f, 36f)
                }
                Numeral(t.digits(s.count.toString()), size, Modifier.padding(vertical = 3.dp).scale(bump.value))
                // The target, and which phrase of the set this is: a dot for each, gold up to this one.
                val steps = s.preset.steps.size
                val stepWords = stringResource(R.string.tasbih_target_step, t.digits(s.target.toString()), t.digits((s.stepIndex + 1).toString()), t.digits(steps.toString()))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.height(18.dp).semantics(mergeDescendants = true) { if (s.target > 0 && steps > 1) contentDescription = stepWords }) {
                    if (s.target > 0) Text(stringResource(R.string.tasbih_target, t.digits(s.target.toString())), style = Type.detail, color = Quiet, maxLines = 1)
                    if (s.target > 0 && steps > 1) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(steps) { i -> Box(Modifier.size(5.dp).background(if (i <= s.stepIndex) Gold else PlatterHigh, CircleShape)) }
                    }
                    if (s.rounds > 0) Text("×" + t.digits(s.rounds.toString()), style = Type.detail, color = Quiet, maxLines = 1)
                }
                // At nought (in Arabic-Indic digits a lone dot) the line says what to do; after that, today's total.
                if (s.count == 0) {
                    Text(stringResource(R.string.tasbih_start), style = Type.note, color = Quiet, textAlign = TextAlign.Center, maxLines = 1)
                } else {
                    Text(stringResource(R.string.tasbih_today, t.count(s.todayOn(controls.today()).toLong())),
                        style = Type.detail.copy(fontWeight = FontWeight.SemiBold), color = Gold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            // Undo and options flank the number at 9 and 3 o'clock, where a round screen is widest: 44 dp discs
            // (40 on the smallest watches) inside 48 dp of touch.
            val side = Screen.width() / 2 - Screen.dp(31f, 33f)
            SideButton(R.drawable.ic_undo, stringResource(R.string.tasbih_undo), Screen.dp(40f, 44f), Modifier.offset(x = -side), undo)
            SideButton(R.drawable.ic_more, stringResource(R.string.tasbih_options), Screen.dp(40f, 44f), Modifier.offset(x = side), controls.onOpenPresets)
            WatchClock()
            RoundDone(celebrate, t)
        }
    }
}

@Composable
private fun SideButton(@DrawableRes icon: Int, label: String, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).pressable(CircleShape, Color.Transparent, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).background(Platter, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, Modifier.size(20.dp), tint = Mist)
        }
    }
}

/** A moment of acknowledgement when a round is complete; it fades by itself and never blocks counting. */
@Composable
private fun RoundDone(key: Int, t: WearText) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) { if (key > 0) { visible = true; delay(1600); visible = false } }
    // The whole screen goes black (nothing of the counter shows through), then three things only: the green check,
    // "Done", and the prayer for it, each at its own height.
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(350))) {
        val h = Screen.height()
        val disc = Screen.dp(62f, 72f)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = h * 0.38f - disc / 2).size(disc).background(Success, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(disc * 0.58f), tint = Color.Black)
            }
            Text(stringResource(R.string.tasbih_round_done), style = if (t.arabic) Type.kufi(24.sp) else Type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Ink, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = h * 0.62f - 15.dp).height(30.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true))
            Text(stringResource(R.string.tasbih_round_done_body), style = Type.body.copy(fontWeight = FontWeight.Normal), color = Quiet, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = h * 0.74f - 11.dp).height(22.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true))
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
        Text(stringResource(R.string.tasbih_free_title), style = Type.title, color = Ink)
        return
    }
    val main = if (t.arabic) d.ar else d.translit
    val long = main.length > 24
    Text(
        main, style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = if (long) 11.sp else Screen.sp(16f, 19f), lineHeight = if (long) 15.sp else 23.sp),
        color = Ink, textAlign = TextAlign.Center, maxLines = if (long) 3 else 1, overflow = TextOverflow.Ellipsis,
        modifier = if (long) Modifier.padding(horizontal = 6.dp) else Modifier,
    )
    if (!t.arabic && !long) Text(d.en, style = Type.note, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun phraseLabel(step: DhikrStep, t: WearText): String = step.dhikr?.let { if (t.arabic) it.ar else it.translit } ?: ""

/** Choose what to count, start again, and see the totals. */
@Composable
fun TasbihOptionsScreen(s: TasbihState, t: WearText, vibrate: Boolean, today: LocalDate, onChoose: (TasbihPreset) -> Unit, onReset: () -> Unit, onVibrate: (Boolean) -> Unit) {
    val list = rememberScalingLazyListState(initialCenterItemIndex = 0)
    PageScaffold(list) {
        NoorList(list) {
            item { Header(stringResource(R.string.tasbih)) }
            TasbihPreset.entries.forEach { p ->
                item {
                    val first = p.steps.first().dhikr
                    val chosen = p == s.preset
                    NoorChip(
                        label = when {
                            p == TasbihPreset.AFTER_PRAYER -> stringResource(R.string.tasbih_after_prayer)
                            first == null -> stringResource(R.string.tasbih_free_title)
                            t.arabic -> first.ar
                            else -> first.translit
                        },
                        icon = null,
                        onClick = { onChoose(p) },
                        secondary = when {
                            p == TasbihPreset.AFTER_PRAYER -> t.digits("33 + 33 + 33 + 1")
                            p.roundTotal == 0 -> stringResource(R.string.tasbih_no_target)
                            t.arabic -> stringResource(R.string.tasbih_times, t.digits(p.roundTotal.toString()))
                            else -> first?.en + " · ×" + p.roundTotal
                        },
                        labelLines = 2,
                        container = if (chosen) PlatterHigh else Platter,
                        modifier = Modifier.semantics { selected = chosen },
                        trailing = if (chosen) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(22.dp), tint = Success) }) else null,
                    )
                }
            }
            item { NoorChip(stringResource(R.string.tasbih_reset), R.drawable.ic_reset, onReset, iconTint = Tint.Gray) }
            item { NoorSwitch(vibrate, onVibrate, stringResource(R.string.tasbih_vibrate), icon = R.drawable.ic_vibrate, iconTint = Tint.Orange) }
            item {
                Footnote(stringResource(R.string.tasbih_totals, t.count(s.todayOn(today).toLong()), t.count(s.lifetime)) + "\n" + stringResource(R.string.tasbih_private))
            }
        }
    }
}
