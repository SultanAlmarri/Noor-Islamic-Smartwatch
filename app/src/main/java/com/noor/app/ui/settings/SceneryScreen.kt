package com.noor.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.app.R
import com.noor.app.data.PrayerProvider
import com.noor.app.data.Scenery
import com.noor.app.data.SkyGraphics
import com.noor.app.sky.Biome
import com.noor.app.sky.SkyLevel
import com.noor.app.sky.SkyLevels
import androidx.compose.runtime.getValue
import com.noor.app.ui.adhan.BackHeader
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme

/**
 * The picture of each scenery as a graphics level draws it (0.20): the high graphics' rendered scenery, the medium
 * graphics' layered sky (tools/skybench/cards.py), the low graphics' simplest sky (SceneryCardPicturesTest).
 */
internal fun sceneryImage(b: Biome, level: SkyLevel = SkyLevel.HIGH): Int = when (level) {
    SkyLevel.HIGH -> when (b) {
        Biome.DUNES -> R.drawable.scenery_real_dunes
        Biome.RAINFOREST -> R.drawable.scenery_real_pnw
        Biome.ARCTIC -> R.drawable.scenery_real_arctic
        Biome.OCEAN -> R.drawable.scenery_real_ocean
        Biome.MEADOW -> R.drawable.scenery_real_meadow
        Biome.CANYON -> R.drawable.scenery_real_canyon
    }
    SkyLevel.MEDIUM -> when (b) {
        Biome.DUNES -> R.drawable.scenery_medium_dunes
        Biome.RAINFOREST -> R.drawable.scenery_medium_pnw
        Biome.ARCTIC -> R.drawable.scenery_medium_arctic
        Biome.OCEAN -> R.drawable.scenery_medium_ocean
        Biome.MEADOW -> R.drawable.scenery_medium_meadow
        Biome.CANYON -> R.drawable.scenery_medium_canyon
    }
    SkyLevel.LOW -> when (b) {
        Biome.DUNES -> R.drawable.scenery_low_dunes
        Biome.RAINFOREST -> R.drawable.scenery_low_pnw
        Biome.ARCTIC -> R.drawable.scenery_low_arctic
        Biome.OCEAN -> R.drawable.scenery_low_ocean
        Biome.MEADOW -> R.drawable.scenery_low_meadow
        Biome.CANYON -> R.drawable.scenery_low_canyon
    }
}

internal fun sceneryName(b: Biome): Int = when (b) {
    Biome.DUNES -> R.string.scenery_dunes
    Biome.RAINFOREST -> R.string.scenery_rainforest
    Biome.ARCTIC -> R.string.scenery_arctic
    Biome.OCEAN -> R.string.scenery_ocean
    Biome.MEADOW -> R.string.scenery_meadow
    Biome.CANYON -> R.string.scenery_canyon
}

internal fun graphicsName(g: SkyGraphics): Int = when (g) {
    SkyGraphics.AUTO -> R.string.sky_graphics_auto
    SkyGraphics.HIGH -> R.string.sky_graphics_high
    SkyGraphics.MEDIUM -> R.string.sky_graphics_medium
    SkyGraphics.LOW -> R.string.sky_graphics_low
}

/** What each level of graphics is (0.19). */
private fun levelDesc(l: SkyLevel): Int = when (l) {
    SkyLevel.HIGH -> R.string.sky_graphics_high_desc
    SkyLevel.MEDIUM -> R.string.sky_graphics_medium_desc
    SkyLevel.LOW -> R.string.sky_graphics_low_desc
}

private fun levelName(l: SkyLevel): Int = when (l) {
    SkyLevel.HIGH -> R.string.sky_graphics_high
    SkyLevel.MEDIUM -> R.string.sky_graphics_medium
    SkyLevel.LOW -> R.string.sky_graphics_low
}

/**
 * Home scenery (0.9.8; six sceneries since the real sky, 0.10): the landscape under the home screen's sky. "Automatic"
 * chooses it from the place; or the user keeps the one they love, wherever they are. Below, how finely the sky is
 * drawn: automatic by default, or fixed, or the earlier sky.
 */
@Composable
fun SceneryScreen(contentPadding: PaddingValues, onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val c = NoorTheme.colors
    val ar = isArabicUi()
    val place = PrayerProvider.place(s)
    val auto = Biome.of(Scenery.AUTO, place?.countryCode, place?.point?.elevationM, place?.point?.latitude)
    val chosen = Biome.of(s.scenery, place?.countryCode, place?.point?.elevationM, place?.point?.latitude)
    val placeName = place?.let { if (ar) it.nameAr ?: it.nameEn else it.nameEn }
    // (0.20) The graphics level is chosen here, over the sceneries, and every picture shows the sceneries as that level
    // draws them, at once (until 0.19 the level was at the foot of the page and the pictures were always the high
    // graphics': the owner chose Low and saw nothing change).
    val context = androidx.compose.ui.platform.LocalContext.current
    val autoGuess = SkyLevels.autoLevel.value ?: androidx.compose.runtime.remember { SkyLevels.auto(context) }
    // (0.22) Where the real sky cannot run on this phone (no OpenGL ES 3, or it failed to start), the home screen draws
    // the medium graphics' sky in its place: automatic says so, rather than "High" (the logic bug hunt).
    val realUnusable = com.noor.app.sky.RealSky.unusable.intValue == 1 ||
        (com.noor.app.sky.RealSky.unusable.intValue < 0 && androidx.compose.runtime.remember { !com.noor.app.sky.RealSky.usable(context, s.skyGraphics) })
    val autoLevel = if (autoGuess == SkyLevel.HIGH && realUnusable) SkyLevel.MEDIUM else autoGuess
    val level = when (s.skyGraphics) {
        SkyGraphics.AUTO -> autoLevel
        SkyGraphics.HIGH -> SkyLevel.HIGH
        SkyGraphics.MEDIUM -> SkyLevel.MEDIUM
        SkyGraphics.LOW -> SkyLevel.LOW
    }
    com.noor.app.ui.components.NoorPage(stringResource(R.string.scenery_title), contentPadding, onBack = onBack, spacing = 0.dp) {
        Text(stringResource(R.string.scenery_intro), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 12.dp))
        GraphicsPicker(s.skyGraphics, level, autoLevel) { vm.setSkyGraphics(it) }
        Spacer(Modifier.height(16.dp))
        SceneryCard(
            image = sceneryImage(auto, level), title = stringResource(R.string.scenery_auto),
            subtitle = stringResource(R.string.scenery_auto_now, stringResource(sceneryName(auto)), placeName ?: "—"),
            selected = s.scenery == Scenery.AUTO, wide = true,
        ) { vm.setScenery(Scenery.AUTO) }
        Spacer(Modifier.height(12.dp))
        val choices = listOf(
            Scenery.DUNES to Biome.DUNES, Scenery.RAINFOREST to Biome.RAINFOREST, Scenery.ARCTIC to Biome.ARCTIC,
            Scenery.SEA to Biome.OCEAN, Scenery.SAVANNA to Biome.MEADOW, Scenery.CANYON to Biome.CANYON,
        )
        choices.chunked(2).forEach { row ->
            // (0.21) The two cards of a row are one height, also when a name takes two lines ("Pacific Northwest" with
            // large text, its Arabic name): they were uneven.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(bottom = 12.dp)) {
                row.forEach { (sc, b) ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        SceneryCard(sceneryImage(b, level), stringResource(sceneryName(b)), null, s.scenery != Scenery.AUTO && chosen == b, wide = false) { vm.setScenery(sc) }
                    }
                }
            }
        }
        Text(stringResource(R.string.scenery_note), style = FootnoteStyle, color = c.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 24.dp))

        // The seasons: followed or not (not, unless asked). The stars: where they truly are, or the finest night sky.
        SettingsSection("") {
            SettingsSwitch(Icons.Rounded.Park, stringResource(R.string.scenery_seasons_title), stringResource(R.string.scenery_seasons_note), s.seasons) { on -> vm.setSeasons(on) }
            SettingsDivider()
            SettingsSwitch(Icons.Rounded.AutoAwesome, stringResource(R.string.scenery_stars_title), stringResource(R.string.scenery_stars_note), s.trueStars) { on -> vm.setTrueStars(on) }
        }
        Spacer(Modifier.height(24.dp))

        // 0.15: the countdown's way after a prayer begins lives here, with the home screen it is seen on (the
        // notification bar follows it too).
        SettingsSection(stringResource(R.string.countdown_group)) {
            SettingsSwitch(Icons.Rounded.Timer, stringResource(R.string.elapsed_setting), stringResource(R.string.elapsed_setting_desc), s.elapsedAfterPrayer) { on -> vm.setElapsedAfterPrayer(on) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * (0.20) The graphics level, as four segments in one glass capsule: Automatic, High, Medium, Low. Under it, what the
 * level is (for Automatic, the level it chose for this phone). The scenery pictures below follow it.
 */
@Composable
private fun GraphicsPicker(choice: SkyGraphics, level: SkyLevel, autoLevel: SkyLevel, onChoose: (SkyGraphics) -> Unit) {
    val c = NoorTheme.colors
    Text(stringResource(R.string.sky_graphics_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
        modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 8.dp))
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.glass)
            .border(0.75.dp, c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SkyGraphics.entries.forEach { g ->
            val on = g == choice
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.gold else Color.Transparent, label = "segment")
            val fg by androidx.compose.animation.animateColorAsState(if (on) c.onGold else c.textPrimary, label = "segmentText")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                    .semantics { role = Role.RadioButton; selected = on }
                    .clickable(onClick = com.noor.app.ui.components.withTick { onChoose(g) }),
                contentAlignment = Alignment.Center,
            ) {
                // (0.21) The four names keep to one line in a quarter of the width: with large text "Medium" was cut to
                // "Mediu". Their size follows the phone's text size up to a fifth larger, no further.
                val grow = com.noor.app.ui.components.limitedGrowth(1.2f)
                val label = MaterialTheme.typography.labelLarge
                Text(stringResource(if (g == SkyGraphics.AUTO) R.string.sky_graphics_auto_short else graphicsName(g)),
                    style = label.copy(fontSize = label.fontSize * grow), color = fg, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
            }
        }
    }
    val what = if (choice == SkyGraphics.AUTO) stringResource(R.string.sky_graphics_auto_now, stringResource(levelName(autoLevel))) + " · " + stringResource(levelDesc(level))
        else stringResource(levelDesc(level))
    androidx.compose.animation.AnimatedContent(what, label = "graphicsWhat") { t ->
        Text(t, style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 8.dp))
    }
    Text(stringResource(R.string.sky_graphics_note), style = FootnoteStyle, color = c.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp).padding(top = 4.dp))
}

@Composable
private fun SceneryCard(image: Int, title: String, subtitle: String?, selected: Boolean, wide: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().then(if (wide) Modifier else Modifier.fillMaxHeight()).clip(shape)
        .border(if (selected) 2.dp else 0.75.dp, if (selected) c.gold else c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape)
        .background(c.glass)
        .semantics { role = Role.RadioButton; this.selected = selected }
        .clickable(onClick = onClick)) {
        Box {
            // (0.20) The picture follows the graphics level chosen above: one fades into the other.
            androidx.compose.animation.Crossfade(image, animationSpec = androidx.compose.animation.core.tween(380), label = "scenery") { img ->
                Image(painterResource(img), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 2.2f else 1.25f))
            }
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(c.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = c.onGold, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}
