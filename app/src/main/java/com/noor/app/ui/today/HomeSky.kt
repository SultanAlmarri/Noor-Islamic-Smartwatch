package com.noor.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noor.app.ui.components.prayerIcon
import com.noor.app.ui.theme.Sky
import com.noor.prayer.Prayer

/** A prayer's sky, top to bottom. */
@Immutable
internal data class PrayerSky(val top: Color, val middle: Color, val bottom: Color)

/**
 * The sky of each prayer's moment, from the same palette as the live sky, at the Sun's typical height for that prayer:
 * dawn indigo for Fajr, sunrise peach, noon azure, the paler afternoon, the coral of Maghrib, night for Isha.
 */
internal object PrayerSkies {
    private val skies: Map<Prayer, PrayerSky> = Prayer.entries.associateWith { p ->
        val (el, evening) = moment(p)
        val c = Sky.colors(el, evening)
        // Night discs would vanish on the night page: lift them a little towards their twilight.
        val lift = if (p == Prayer.ISHA || p == Prayer.FAJR) 0.10f else 0f
        PrayerSky(Sky.mix(c.zenith, c.mid, lift), Sky.mix(c.mid, c.horizon, lift), c.horizon)
    }

    fun of(p: Prayer): PrayerSky = skies.getValue(p)

    fun moment(p: Prayer): Pair<Double, Boolean> = when (p) {
        Prayer.FAJR -> -10.0 to false
        Prayer.SHURUQ -> 1.0 to false
        Prayer.DHUHR -> 60.0 to false
        Prayer.ASR -> 14.0 to true
        Prayer.MAGHRIB -> -2.0 to true
        Prayer.ISHA -> -12.0 to true
    }
}

/**
 * A prayer as a small window onto its sky, with its glyph in white. [passed] dims it; [lit] adds a halo for the prayer
 * whose time it is now. The timeline of the day thus reads as the sky passing.
 */
@Composable
internal fun SkyDisc(p: Prayer, size: Dp, modifier: Modifier = Modifier, passed: Boolean = false, lit: Boolean = false) {
    val sky = PrayerSkies.of(p)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            if (lit) drawCircle(Brush.radialGradient(listOf(sky.bottom.copy(alpha = 0.55f), Color.Transparent), center, r), r, center)
            val inner = if (lit) r * 0.84f else r
            drawCircle(
                Brush.verticalGradient(0f to sky.top, 0.62f to sky.middle, 1f to sky.bottom, startY = center.y - inner, endY = center.y + inner),
                inner, center, alpha = if (passed) 0.42f else 1f,
            )
            // A whisper of light on the rim, as on a glass bead.
            drawCircle(Color.White.copy(alpha = if (passed) 0.05f else 0.16f), inner, center, style = Stroke(0.8.dp.toPx()))
        }
        Icon(painterResource(prayerIcon(p)), null, tint = Color.White.copy(alpha = if (passed) 0.55f else 1f), modifier = Modifier.size(size * 0.5f))
    }
}
