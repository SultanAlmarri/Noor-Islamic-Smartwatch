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
import com.noor.app.ui.adhan.BackHeader
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme

/** The picture and the name of each scenery. */
internal fun sceneryImage(b: Biome): Int = when (b) {
    Biome.DUNES -> R.drawable.scenery_real_dunes
    Biome.RAINFOREST -> R.drawable.scenery_real_pnw
    Biome.ARCTIC -> R.drawable.scenery_real_arctic
    Biome.OCEAN -> R.drawable.scenery_real_ocean
    Biome.MEADOW -> R.drawable.scenery_real_meadow
    Biome.CANYON -> R.drawable.scenery_real_canyon
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
    SkyGraphics.ULTRA -> R.string.sky_graphics_ultra
    SkyGraphics.HIGH -> R.string.sky_graphics_high
    SkyGraphics.MEDIUM -> R.string.sky_graphics_medium
    SkyGraphics.LOW -> R.string.sky_graphics_low
    SkyGraphics.CLASSIC -> R.string.sky_graphics_classic
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
    com.noor.app.ui.components.NoorPage(stringResource(R.string.scenery_title), contentPadding, onBack = onBack, spacing = 0.dp) {
        Text(stringResource(R.string.scenery_intro), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 12.dp))
        SceneryCard(
            image = sceneryImage(auto), title = stringResource(R.string.scenery_auto),
            subtitle = stringResource(R.string.scenery_auto_now, stringResource(sceneryName(auto)), placeName ?: "—"),
            selected = s.scenery == Scenery.AUTO, wide = true,
        ) { vm.setScenery(Scenery.AUTO) }
        Spacer(Modifier.height(12.dp))
        val choices = listOf(
            Scenery.DUNES to Biome.DUNES, Scenery.RAINFOREST to Biome.RAINFOREST, Scenery.ARCTIC to Biome.ARCTIC,
            Scenery.SEA to Biome.OCEAN, Scenery.SAVANNA to Biome.MEADOW, Scenery.CANYON to Biome.CANYON,
        )
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                row.forEach { (sc, b) ->
                    Box(Modifier.weight(1f)) {
                        SceneryCard(sceneryImage(b), stringResource(sceneryName(b)), null, s.scenery != Scenery.AUTO && chosen == b, wide = false) { vm.setScenery(sc) }
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

        // How finely the sky is drawn.
        SettingsSection(stringResource(R.string.sky_graphics_title), footer = stringResource(R.string.sky_graphics_note)) {
            SkyGraphics.entries.forEachIndexed { i, g ->
                val on = s.skyGraphics == g
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { role = Role.RadioButton; selected = on }.clickable { vm.setSkyGraphics(g) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(graphicsName(g)), style = MaterialTheme.typography.bodyLarge, color = if (on) c.goldText else c.textPrimary, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SceneryCard(image: Int, title: String, subtitle: String?, selected: Boolean, wide: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .border(if (selected) 2.dp else 0.75.dp, if (selected) c.gold else c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape)
        .background(c.glass)
        .semantics { role = Role.RadioButton; this.selected = selected }
        .clickable(onClick = onClick)) {
        Box {
            Image(painterResource(image), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(if (wide) 2.2f else 1.25f))
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
