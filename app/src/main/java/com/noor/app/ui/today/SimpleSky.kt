package com.noor.app.ui.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box

/**
 * The low graphics (0.19; Settings → Home scenery → Graphics → Low): the simplest home sky, for any phone. The sky's
 * colours of the moment, the Sun and the moon, the stars at night, and the land of the scenery as flat silhouettes; no
 * clouds, nothing that moves by itself, and nothing drawn again unless the moment shown changes (a minute passes, the
 * Sun is dragged). The Sun's path and the prayers are drawn over it by [LivingSky], as on every level.
 */
@Composable
internal fun SimpleSkyLayer(
    scene: SkyScene,
    layout: SceneLayout,
    display: Double,
    page: Color,
    rtl: Boolean,
    parallax: () -> Float,
    modifier: Modifier = Modifier,
) {
    val light = lightAt(scene.elevationAt(display), scene.eveningAt(display))
    Box(modifier.fillMaxSize().graphicsLayer { translationY = parallax() * 0.5f }.drawWithCache {
        val sky = Brush.verticalGradient(0f to light.zenith, layout.horizon * 0.58f to light.mid, layout.horizon to light.horizon, 1f to light.horizon)
        onDrawBehind {
            drawRect(sky)
            val hy = size.height * layout.horizon
            drawRect(page, topLeft = androidx.compose.ui.geometry.Offset(0f, hy), size = androidx.compose.ui.geometry.Size(size.width, size.height - hy))
        }
    })
}
