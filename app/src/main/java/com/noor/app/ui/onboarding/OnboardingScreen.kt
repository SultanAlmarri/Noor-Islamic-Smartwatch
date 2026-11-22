package com.noor.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.AlertScheduler
import com.noor.app.R
import com.noor.app.data.LocationMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.withTick
import com.noor.app.ui.opening.LocalOpening
import com.noor.app.ui.settings.SettingsViewModel
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.Sky
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First run (0.25: redesigned as a welcome of four pages on one dawn).
 *
 * The cover says what Minhad is (its mark, its name, one line, four things it gives) with one button. Then each
 * thing Minhad needs has a page of its own, with its reason said before Android asks (Google Play's "prominent
 * disclosure"): the place ("Use my location" or "Choose a city instead"), keeping it right on the way (background
 * location), and the Adhan (with Android's notification permission, and then, only if Android does not yet let
 * Minhad ring at an exact time, a page for that). The sky is one picture for all of it ([DawnSky]): each page lets
 * in more light, and the way out is the sunrise.
 *
 * What is asked, when, and what each answer does is as it was in 0.15 to 0.24, but for three things: the cover, the
 * exact alarms' page, and the way back (Android's back gesture returns to the page before; on the cover it leaves
 * the app, as it always did).
 */
@Composable
fun OnboardingScreen(contentPadding: PaddingValues, onChooseCity: () -> Unit, onFinished: () -> Unit, vm: SettingsViewModel = viewModel()) {
    // (Saved: Android's permission dialog can outlive the activity, and the next page must still be there after it.)
    var step by rememberSaveable { mutableIntStateOf(COVER) }
    // (After the Adhan's page: the location page, when the place is to be chosen by hand, or the home screen.)
    var thenCity by rememberSaveable { mutableStateOf(false) }
    // (The welcome comes in once: not again when the screen is turned.)
    var arrived by rememberSaveable { mutableStateOf(false) }
    val ambient = LocalAmbientMotion.current
    val scope = rememberCoroutineScope()
    val chooseCity by rememberUpdatedState(onChooseCity)
    val finished by rememberUpdatedState(onFinished)

    // The way out: the welcome is recorded as done at once; with motion allowed the words then leave and the Sun's
    // edge comes over the Earth's (0.6 s) before the next page is opened.
    var leaving by remember { mutableStateOf(false) }
    val out = remember { Animatable(0f) }
    fun finish() {
        if (leaving) return
        leaving = true
        val recorded = vm.completeOnboarding()
        scope.launch {
            if (ambient) out.animateTo(1f, tween(LEAVE_MS, easing = LinearEasing))
            // (Not before the record is written: it is written in this page's own scope, which ends when the page
            // is left. MainFlowTest caught the welcome left unrecorded.)
            recorded.join()
            if (thenCity) chooseCity() else finished()
        }
    }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setBackgroundUpdates(granted); step = ADHAN
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        if (granted) { vm.setLocationMode(LocationMode.AUTO); thenCity = false; step = TRAVEL } else { thenCity = true; step = ADHAN }
    }
    // 0.15: the Adhan is Noor's heart, and it starts off: the welcome offers it, once, with Android's notification
    // permission (13+) asked right after the user said yes. (The alarm volume is checked on the Adhan tab.)
    // 0.25: since Android 12 an app may ring at an exact time only if the user allows "Alarms & reminders", and since
    // Android 14 that is off for a new install: whoever turned the Adhan on here got it late until they found the
    // warning on the Adhan tab. So, only when the Adhan was turned on and exact alarms are not allowed, one more
    // page says why and opens Android's page for it; either answer ends the welcome as before.
    val context = LocalContext.current
    // (Saved: the Adhan was turned on here, so "Not now" after going back to its page turns it off again.)
    var adhanChosen by rememberSaveable { mutableStateOf(false) }
    // (Coming back from Android's page, whatever was chosen there, ends the welcome. A launcher, so that the step is
    // not lost if Android rebuilt the activity meanwhile.)
    val exactAlarms = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> finish() }
    fun afterAdhan() { if (AlertScheduler.canScheduleExact(context)) finish() else step = MINUTE }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> afterAdhan() }
    fun adhanOn() {
        vm.setAlertsEnabled(true); adhanChosen = true
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else afterAdhan()
    }
    fun primary() {
        when (step) {
            COVER -> step = PLACE
            // Android shows its precise/approximate choice; Noor works with either.
            PLACE -> foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            TRAVEL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else { vm.setBackgroundUpdates(true); step = ADHAN }
            ADHAN -> adhanOn()
            // (The page is only met on Android 12 and later. A phone without that page: the Adhan tab says what to do.)
            else -> runCatching {
                exactAlarms.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            }.onFailure { finish() }
        }
    }
    fun secondary() {
        when (step) {
            PLACE -> { thenCity = true; step = ADHAN }
            TRAVEL -> step = ADHAN
            ADHAN -> { if (adhanChosen) { vm.setAlertsEnabled(false); adhanChosen = false }; finish() }
            MINUTE -> finish()
        }
    }
    // The way back: to the page one came from (the travel page is only met when the location was allowed).
    BackHandler(enabled = step > COVER && !leaving) {
        step = when (step) { PLACE -> COVER; TRAVEL -> PLACE; ADHAN -> if (thenCity) PLACE else TRAVEL; else -> ADHAN }
        if (step == PLACE) thenCity = false
    }

    // The dawn: out of the night once, then a little more light with each page.
    val light = remember { Animatable(if (ambient && !arrived) Dawn.BEFORE else Dawn.lightFor(step)) }
    LaunchedEffect(step, leaving, ambient) {
        val to = if (leaving && ambient) Dawn.SUNRISE else Dawn.lightFor(step)
        if (!ambient) light.snapTo(to)
        else light.animateTo(to, tween(when { leaving -> LEAVE_MS + 200; !arrived -> 2600; else -> 1500 }, easing = NoorMotion.Ease))
    }
    // The cover comes in behind the launch screen's dissolving veil: the mark from where the launch screen had it,
    // then the name, the line, what Minhad gives, the button, one after another.
    val carry = remember { Animatable(if (ambient && !arrived) 0f else 1f) }
    val enter = remember { Animatable(if (ambient && !arrived) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!arrived && ambient) {
            delay(160)
            launch { carry.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 62f)) }
            launch { enter.animateTo(1f, tween(1400, delayMillis = 200, easing = LinearEasing)) }
        }
        arrived = true
    }
    // What keeps the sky alive (the stars, each symbol's signal): only while ambient motion is allowed.
    val turn = if (ambient) rememberInfiniteTransition(label = "welcomeSky").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "turn") else null
    val phase = if (ambient) rememberInfiniteTransition(label = "welcomeSignal").animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "phase") else null

    // The Earth's edge stands just above the buttons, wherever they are (one button on the cover, two after it; a
    // short screen, large text): the sky is told where the buttons begin.
    val places = remember { Places() }
    val crest = remember { mutableFloatStateOf(0f) }
    val band = with(LocalDensity.current) { Dawn.Band.toPx() }
    fun place() {
        val b = places.box; val r = places.bar
        if (b != null && r != null && b.isAttached && r.isAttached) crest.floatValue = b.localPositionOf(r, Offset.Zero).y + band
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(Modifier.fillMaxSize().onGloballyPositioned { places.box = it; place() }) {
        DawnSky({ light.value }, { crest.floatValue }, Modifier.fillMaxSize(), twinkle = { turn?.value ?: -1f })
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            val pages = Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = (1f - out.value * 2.5f).coerceIn(0f, 1f) }
            val page: @Composable (Int) -> Unit = { s ->
                PageFrame { size ->
                    if (s == COVER) Cover(size, { enter.value }, { carry.value })
                    else {
                        // (Each page's own pieces settle as it comes in.)
                        val settle = remember { Animatable(if (ambient) 0f else 1f) }
                        LaunchedEffect(Unit) { if (ambient) settle.animateTo(1f, tween(820, delayMillis = 60, easing = LinearEasing)) }
                        Ask(s, size, { settle.value }, { phase?.value ?: -1f })
                    }
                }
            }
            if (ambient) AnimatedContent(
                targetState = step, modifier = pages,
                transitionSpec = {
                    // Forward, the next page comes from where reading goes; back, from where it came.
                    val way = (if (targetState > initialState) 1 else -1) * (if (rtl) -1 else 1)
                    (fadeIn(tween(NoorMotion.Standard, delayMillis = 80)) + slideInHorizontally(spring(dampingRatio = 0.84f, stiffness = 240f)) { way * it / 5 }) togetherWith
                        (fadeOut(tween(NoorMotion.Quick)) + slideOutHorizontally(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { -way * it / 9 })
                },
                label = "welcomePage",
            ) { s -> page(s) }
            // (With ambient motion off the next page is simply there: an animated holder keeps the page before for a
            // few frames even when told not to animate, WelcomeDesignTest.)
            else Box(pages) { key(step) { page(step) } }
            // The buttons, on the Earth, within the thumb's reach. (The band of first light is the top of this part.)
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { places.bar = it; place() }.testTag(WELCOME_BAR)
                    .graphicsLayer { alpha = part(enter.value, 6, 7) * (1f - out.value * 2.5f).coerceIn(0f, 1f) }
                    .padding(horizontal = 24.dp).wrapContentWidth().widthIn(max = 520.dp).padding(top = Dawn.Band + 20.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Dots(step.coerceAtMost(ADHAN), ambient)
                Spacer(Modifier.height(18.dp))
                GoldButton(stringResource(when (step) {
                    COVER, TRAVEL -> R.string.welcome_continue; PLACE -> R.string.perm_allow; ADHAN -> R.string.onb_adhan_on; else -> R.string.welcome_exact_allow
                }), ambient) { primary() }
                // The other way: quiet, but a button (0.15: as plain text on the dawn it was easy to miss).
                val other: @Composable () -> Unit = {
                    TextButton(onClick = withTick { secondary() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 52.dp), shape = ButtonShape) {
                        Words(stringResource(if (step == PLACE) R.string.perm_manual else R.string.welcome_not_now), ambient) {
                            Text(it, color = Sky.GoldOnSky, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (ambient) AnimatedVisibility(
                    step > COVER,
                    enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 300f)) + fadeIn(tween(NoorMotion.Standard)),
                    exit = shrinkVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) + fadeOut(tween(NoorMotion.Quick)),
                ) { other() }
                else if (step > COVER) other()
            }
        }
    }
}

private const val COVER = 0
private const val PLACE = 1
private const val TRAVEL = 2
private const val ADHAN = 3
/** Only when the Adhan was turned on and Android does not let Minhad ring at an exact time yet (Android 12 and later). */
private const val MINUTE = 4
private const val LEAVE_MS = 620
internal const val WELCOME_BAR = "welcomeBar"

/** The words on the welcome's sky. `WelcomeDesignTest` checks both against the sky's drawn pixels. */
internal val WelcomeText = Sky.OnSky
internal val WelcomeTextQuiet = Color(0xE0FFFFFF)
/** The main button: gold, with ink on it. */
internal val ButtonGold = listOf(Color(0xFFFFEBC0), Color(0xFFEDC57C))
internal val ButtonInk = Color(0xFF1B1407)
private val ButtonShape = RoundedCornerShape(28.dp)

private class Places { var box: LayoutCoordinates? = null; var bar: LayoutCoordinates? = null }

/** How far the [order]-th of [of] pieces has come in, when the whole entrance is at [t] (0..1). */
private fun part(t: Float, order: Int, of: Int): Float {
    if (t >= 1f) return 1f
    val span = 0.46f
    val start = (1f - span) * order / (of - 1).coerceAtLeast(1)
    return NoorMotion.Ease.transform(((t - start) / span).coerceIn(0f, 1f))
}

private fun Modifier.arrives(enter: () -> Float, order: Int, of: Int, lift: Dp = 22.dp): Modifier = graphicsLayer {
    val p = part(enter(), order, of)
    alpha = p
    translationY = (1f - p) * lift.toPx()
}

/**
 * The page's part of the screen, when the page is longer than it (a short screen, large text): where there is more
 * to scroll to, the words fade away over the last lines, so that a page cut between two lines does not look whole.
 */
private fun Modifier.fadedEdges(scroll: ScrollState): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scroll.canScrollBackward) {
            val e = 20.dp.toPx()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = e), size = Size(size.width, e), blendMode = BlendMode.DstIn)
        }
        if (scroll.canScrollForward) {
            val e = 44.dp.toPx()
            drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - e, endY = size.height), Offset(0f, size.height - e), Size(size.width, e), blendMode = BlendMode.DstIn)
        }
    }

/**
 * One page: in the middle of its part of the screen when it fits, scrolling when it does not (a short screen, large
 * text: the buttons stay where they are), at a readable width on a wide screen.
 */
@Composable
private fun PageFrame(content: @Composable ColumnScope.(size: PageSize) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // (A short screen has less of everything but words; an unfolded phone or a tablet has more.)
        val size = when { maxHeight < 500.dp -> PageSize.COMPACT; maxWidth >= 600.dp -> PageSize.WIDE; else -> PageSize.PHONE }
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxWidth().fadedEdges(scroll).verticalScroll(scroll).heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 14.dp)
                .wrapContentWidth().widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content(size) }
    }
}

private enum class PageSize { COMPACT, PHONE, WIDE }

/** The cover: the mark, the name, what Minhad is in one line, and four things it gives. */
@Composable
private fun ColumnScope.Cover(size: PageSize, enter: () -> Float, carry: () -> Float) {
    val of = 7
    val compact = size == PageSize.COMPACT; val wide = size == PageSize.WIDE
    Spacer(Modifier.weight(0.5f))
    // Wordmark: منهاد in Kufi is Minhad's logo (shown in both languages, like the icon); the Latin name beneath only in English.
    val name = MaterialTheme.typography.displayMedium.copy(fontFamily = Kufi, fontSize = if (compact) 36.sp else if (wide) 54.sp else 50.sp, fontWeight = FontWeight.Normal)
    if (compact) {
        // (A short screen: the mark and the name side by side, so that all Minhad gives is seen without scrolling.)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LaunchMark(38.dp, carry)
            Spacer(Modifier.width(10.dp))
            Text("منهاد", style = name, color = Sky.GoldOnSky, modifier = Modifier.arrives(enter, 0, of))
        }
    } else {
        LaunchMark(if (wide) 84.dp else 76.dp, carry)
        Text("منهاد", style = name, color = Sky.GoldOnSky, modifier = Modifier.arrives(enter, 0, of))
    }
    if (!isArabicUi()) Text("MINHAD", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 7.sp, fontSize = 12.sp), color = WelcomeTextQuiet,
        modifier = Modifier.padding(start = 7.dp).arrives(enter, 0, of))
    Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
    Text(stringResource(R.string.onboarding_tagline),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = if (compact) 24.sp else if (wide) 30.sp else 28.sp,
            lineHeight = if (compact) 30.sp else if (wide) 38.sp else 35.sp, lineBreak = LineBreak.Heading),
        color = WelcomeText, textAlign = TextAlign.Center, modifier = Modifier.arrives(enter, 1, of).semantics { heading() })
    Spacer(Modifier.height(if (compact) 16.dp else if (wide) 30.dp else 34.dp))
    Column(Modifier.widthIn(max = if (wide) 420.dp else 380.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
        Gift(rememberVectorPainter(Icons.Rounded.Schedule), Color(0xFFFFB84D), Color(0xFFEE6A1F), R.string.welcome_times, R.string.welcome_times_line, wide, Modifier.arrives(enter, 2, of))
        Gift(painterResource(R.drawable.ic_tab_today), Color(0xFF5DBBFF), Color(0xFF2A62E6), R.string.welcome_sky, R.string.welcome_sky_line, wide, Modifier.arrives(enter, 3, of))
        Gift(rememberVectorPainter(Icons.Rounded.NotificationsActive), Color(0xFF4FDC97), Color(0xFF14975A), R.string.welcome_adhan, R.string.welcome_adhan_line, wide, Modifier.arrives(enter, 4, of))
        Gift(rememberVectorPainter(Icons.Rounded.Explore), Color(0xFFB896FF), Color(0xFF7146E4), R.string.welcome_qibla, R.string.welcome_qibla_line, wide, Modifier.arrives(enter, 5, of))
    }
    Spacer(Modifier.weight(1f))
}

/** One thing Minhad gives: a sign, a few bold words, one quiet line. */
@Composable
private fun Gift(sign: Painter, top: Color, low: Color, title: Int, line: Int, wide: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        SignTile(sign, top, low, if (wide) 50.dp else 46.dp)
        Spacer(Modifier.width(if (wide) 18.dp else 16.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = if (wide) 18.sp else 17.sp), color = WelcomeText)
            Text(stringResource(line), style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (wide) 16.sp else 15.sp, lineHeight = if (wide) 22.sp else 21.sp), color = WelcomeTextQuiet)
        }
    }
}

/**
 * The mark on the cover. On a first start it is drawn where the launch screen drew it (behind the launch screen's
 * dissolving veil, so nothing jumps) and settles from there into its place: [carry] 0 → 1.
 */
@Composable
private fun LaunchMark(crescent: Dp, carry: () -> Float) {
    val opening = LocalOpening.current
    val view = LocalView.current
    val mark = painterResource(R.drawable.ic_splash_mark)
    val field = crescent * (Mark.FIELD / (2f * Mark.R))
    var at by remember { mutableStateOf(Offset.Unspecified) }
    Canvas(Modifier.size(field * Mark.BOX).onGloballyPositioned { at = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) }.clearAndSetSemantics { }) {
        val p = carry()
        val mine = field.toPx()
        if (p >= 1f || !at.isSpecified) { drawMark(mark, mine, center); return@Canvas }
        // The launch screen's mark: its whole field in the launch icon's box (the middle of the window when unknown).
        val launch = opening.mark
        val theirs = launch?.width ?: 288.dp.toPx()
        val left = launch?.left ?: ((view.rootView.width - theirs) / 2f)
        val top = launch?.top ?: ((view.rootView.height - theirs) / 2f)
        val from = center + (Offset(left + theirs * Mark.CX / Mark.FIELD, top + theirs * Mark.CY / Mark.FIELD) - at)
        drawMark(mark, lerp(theirs, mine, p), lerp(from, center, p))
    }
}

/** A page that asks for one thing: its symbol, what it is for in a few large words, and the honest reason. */
@Composable
private fun ColumnScope.Ask(step: Int, size: PageSize, settle: () -> Float, phase: () -> Float) {
    val compact = size == PageSize.COMPACT; val wide = size == PageSize.WIDE
    val symbol = when (step) { PLACE -> Symbol.PLACE; TRAVEL -> Symbol.TRAVEL; ADHAN -> Symbol.ADHAN; else -> Symbol.MINUTE }
    val title = stringResource(when (step) { PLACE -> R.string.perm_title; TRAVEL -> R.string.bg_perm_title; ADHAN -> R.string.onb_adhan_title; else -> R.string.welcome_exact_title })
    // The words come first: the symbol stands at the same height on every page and takes the room they leave, up to
    // its own size; where they need all of it (a short screen with large text) it steps aside, so that the reason
    // is read whole before Android is asked.
    Spacer(Modifier.weight(if (compact) 0.2f else 1f))
    SymbolArt(symbol, Modifier.weight(8f, fill = false).size(if (compact) 150.dp else if (wide) 240.dp else 232.dp).graphicsLayer {
        val p = part(settle(), 0, 3)
        alpha = p; scaleX = 0.84f + 0.16f * p; scaleY = scaleX
    }, phase)
    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
    // (A page of its own to TalkBack too: its title is said when it comes.)
    Text(title,
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = if (wide) 36.sp else if (compact) 26.sp else 30.sp,
            lineHeight = if (wide) 45.sp else if (compact) 33.sp else 38.sp,
            lineBreak = LineBreak.Heading), color = WelcomeText,
        textAlign = TextAlign.Center, modifier = Modifier.arrives(settle, 1, 3).semantics { heading(); paneTitle = title })
    Spacer(Modifier.height(12.dp))
    Text(stringResource(when (step) { PLACE -> R.string.perm_body; TRAVEL -> R.string.bg_perm_body; ADHAN -> R.string.onb_adhan_body; else -> R.string.welcome_exact_body }),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (wide) 19.sp else 17.sp, lineHeight = if (wide) 29.sp else 26.sp, lineBreak = LineBreak.Paragraph.copy(strategy = LineBreak.Strategy.Balanced)),
        color = WelcomeTextQuiet, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = if (wide) 480.dp else 400.dp).arrives(settle, 2, 3))
    Spacer(Modifier.weight(0.5f))
}

/** Where one is in the welcome: four pages, this one lit. (The exact alarms' page is the second half of the Adhan's.) */
@Composable
private fun Dots(step: Int, ambient: Boolean) {
    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            val w by animateDpAsState(if (i == step) 24.dp else 7.dp, if (ambient) spring(dampingRatio = 0.7f, stiffness = 380f) else snap(), label = "dot")
            val a by animateFloatAsState(if (i == step) 1f else 0.38f, if (ambient) tween(NoorMotion.Standard) else snap(), label = "dotLight")
            Box(Modifier.size(width = w, height = 7.dp).clip(CircleShape).background(if (i == step) Sky.GoldOnSky else Color.White.copy(alpha = a)))
        }
    }
}

/** The main button: a capsule of gold that gives a little under the finger. */
@Composable
private fun GoldButton(text: String, ambient: Boolean, onClick: () -> Unit) {
    val touch = remember { MutableInteractionSource() }
    val pressed by touch.collectIsPressedAsState()
    val give by animateFloatAsState(if (pressed && ambient) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    Button(
        onClick = withTick(onClick), interactionSource = touch, shape = ButtonShape, elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ButtonInk),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = give; scaleY = give }.heightIn(min = 58.dp).background(Brush.verticalGradient(ButtonGold), ButtonShape),
    ) { Words(text, ambient) { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), textAlign = TextAlign.Center) } }
}

/** A button's words: when they change with the page, the new ones come as the old ones go (at once without ambient motion). */
@Composable
private fun Words(text: String, ambient: Boolean, content: @Composable (String) -> Unit) {
    if (ambient) AnimatedContent(text, transitionSpec = { fadeIn(tween(NoorMotion.Standard, delayMillis = 70)) togetherWith fadeOut(tween(NoorMotion.Quick)) },
        contentAlignment = Alignment.Center, label = "buttonWords") { content(it) }
    else content(text)
}
