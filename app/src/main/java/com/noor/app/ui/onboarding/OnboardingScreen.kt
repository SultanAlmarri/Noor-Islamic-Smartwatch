package com.noor.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.ui.components.isArabicUi
import com.noor.app.data.LocationMode
import com.noor.app.ui.components.LocalAmbientMotion
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.settings.SettingsViewModel
import com.noor.app.ui.theme.Kufi
import com.noor.app.ui.theme.Sky

/**
 * First run. Dawn breaks over the screen while Noor introduces itself, then explains why it needs the location
 * before Android asks (Play policy: prominent disclosure); background updates are offered separately, or a city by hand.
 */
@Composable
fun OnboardingScreen(contentPadding: PaddingValues, onChooseCity: () -> Unit, onFinished: () -> Unit, vm: SettingsViewModel = viewModel()) {
    var step by remember { mutableIntStateOf(0) }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setBackgroundUpdates(granted); vm.completeOnboarding(); onFinished()
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        if (granted) { vm.setLocationMode(LocationMode.AUTO); step = 1 } else { vm.completeOnboarding(); onChooseCity() }
    }
    val ambient = LocalAmbientMotion.current
    // The sun rises from deep twilight to just above the horizon, once.
    val sun = remember { Animatable(if (ambient) -15f else -2.5f) }
    val intro = remember { Animatable(if (ambient) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (ambient) {
            kotlinx.coroutines.coroutineScope {
                launch { sun.animateTo(-2.5f, tween(3200, easing = NoorMotion.Ease)) }
                launch { intro.animateTo(1f, tween(1400, delayMillis = 300, easing = NoorMotion.Ease)) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        DawnSky(sun.value.toDouble(), Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(0.9f))
            // Wordmark: نور in Kufi is Noor's logo (shown in both languages, like the icon); the Latin name beneath only in English.
            Column(Modifier.graphicsLayer { alpha = intro.value; translationY = (1f - intro.value) * 24.dp.toPx() }) {
                Text("نور", style = MaterialTheme.typography.displayLarge.copy(fontFamily = Kufi, fontSize = 96.sp, fontWeight = FontWeight.Normal),
                    color = Sky.GoldOnSky, modifier = Modifier.semantics { heading() })
                if (!isArabicUi()) Text("NOOR", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 8.sp, fontSize = 13.sp), color = Sky.OnSkySecondary,
                    modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.onboarding_tagline), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 24.sp),
                    color = Sky.OnSkySecondary)
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(step, transitionSpec = {
                (fadeIn(tween(NoorMotion.Standard)) + slideInVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)) { it / 6 }) togetherWith fadeOut(tween(NoorMotion.Quick))
            }, label = "onboardingStep", modifier = Modifier.graphicsLayer { alpha = intro.value }) { s ->
                Column {
                    Text(stringResource(if (s == 0) R.string.perm_title else R.string.bg_perm_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = Sky.OnSky)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(if (s == 0) R.string.perm_body else R.string.bg_perm_body), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp), color = Sky.OnSkySecondary)
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = {
                            // Android shows its precise/approximate choice; Noor works with either.
                            if (s == 0) foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else { vm.setBackgroundUpdates(true); vm.completeOnboarding(); onFinished() }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF14204A)),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(if (s == 0) R.string.perm_allow else R.string.bg_perm_continue), style = MaterialTheme.typography.titleMedium) }
                    TextButton(onClick = {
                        vm.completeOnboarding()
                        if (s == 0) onChooseCity() else onFinished()
                    }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(stringResource(if (s == 0) R.string.perm_manual else R.string.bg_perm_not_now), color = Sky.OnSkySecondary, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** A sky at the given solar elevation, with stars fading as it brightens and the sun's glow on the horizon. */
@Composable
private fun DawnSky(elevation: Double, modifier: Modifier) {
    val sky = Sky.colors(elevation, evening = false)
    val stars = remember {
        val r = java.util.Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.55f, 0.4f + r.nextFloat() * 0.6f) }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(0f to sky.zenith, 0.45f to sky.mid, 0.82f to sky.horizon, 1f to Sky.mix(sky.horizon, Color.Black, 0.35f)))
        val glow = ((elevation + 12) / 12).coerceIn(0.0, 1.0).toFloat()
        drawCircle(Brush.radialGradient(listOf(Sky.SunGlow.copy(alpha = 0.55f * glow), Color.Transparent), Offset(w * 0.62f, h * 0.84f), w * 0.9f), w * 0.9f, Offset(w * 0.62f, h * 0.84f))
        stars.forEach { (x, y, b) -> drawCircle(Color.White.copy(alpha = sky.night * b * 0.85f), (0.6f + b) * density, Offset(x * w, y * h)) }
    }
}
