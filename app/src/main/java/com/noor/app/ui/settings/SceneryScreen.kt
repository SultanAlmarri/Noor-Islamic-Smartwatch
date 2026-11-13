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
import androidx.compose.material.icons.rounded.Check
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
    Biome.SAVANNA -> R.drawable.scenery_real_savanna
    Biome.CANYON -> R.drawable.scenery_real_canyon
}

internal fun sceneryName(b: Biome): Int = when (b) {
    Biome.DUNES -> R.string.scenery_dunes
    Biome.RAINFOREST -> R.string.scenery_rainforest
    Biome.ARCTIC -> R.string.scenery_arctic
    Biome.OCEAN -> R.string.scenery_ocean
    Biome.SAVANNA -> R.string.scenery_savanna
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
 * Home scenery (0.9.8; six sceneries since the real sky, 1.0): the landscape under the home screen's sky. "Automatic"
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
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        BackHeader(stringResource(R.string.scenery_title), onBack)
        Text(stringResource(R.string.scenery_intro), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        SceneryCard(
            image = sceneryImage(auto), title = stringResource(R.string.scenery_auto),
            subtitle = stringResource(R.string.scenery_auto_now, stringResource(sceneryName(auto)), placeName ?: "—"),
            selected = s.scenery == Scenery.AUTO, wide = true,
        ) { vm.setScenery(Scenery.AUTO) }
        Spacer(Modifier.height(12.dp))
        val choices = listOf(
            Scenery.DUNES to Biome.DUNES, Scenery.RAINFOREST to Biome.RAINFOREST, Scenery.ARCTIC to Biome.ARCTIC,
            Scenery.SEA to Biome.OCEAN, Scenery.SAVANNA to Biome.SAVANNA, Scenery.CANYON to Biome.CANYON,
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
        Text(stringResource(R.string.scenery_note), style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

        // How finely the sky is drawn.
        Text(stringResource(R.string.sky_graphics_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp))
        Text(stringResource(R.string.sky_graphics_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        val shape = RoundedCornerShape(18.dp)
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape).border(0.5.dp, c.separator, shape).background(c.surface)) {
            SkyGraphics.entries.forEach { g ->
                val on = s.skyGraphics == g
                Row(
                    Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = on }.clickable { vm.setSkyGraphics(g) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(graphicsName(g)), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Rounded.Check, null, tint = c.gold, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SceneryCard(image: Int, title: String, subtitle: String?, selected: Boolean, wide: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .border(if (selected) 2.dp else 0.5.dp, if (selected) c.gold else c.separator, shape)
        .background(c.surface)
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
