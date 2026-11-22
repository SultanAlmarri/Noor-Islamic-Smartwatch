package com.noor.wear.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.face.FaceFrame
import com.noor.face.FaceRenderer
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.wear.R
import com.noor.wear.studio.StudioStore
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/** What the watch knows about Minhad Studio, for the face page and the settings row. */
data class StudioUi(val supported: Boolean, val active: Boolean, val fromPhone: Boolean)

/**
 * The watch face page (0.18): Minhad Studio first (a live picture of the design the phone sent, whether it is the face
 * in use, and how to make it so), then the Minhad faces installed with Watch Face Push.
 */
@Composable
fun FacesScreen(config: WatchConfig?, studio: StudioUi, design: FaceDesign, onShowStudio: () -> Boolean, showClassic: Boolean = true) {
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) { padding ->
        ScalingLazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(stringResource(R.string.settings_face)) }
            item { Text(stringResource(R.string.studio_face_name), style = Type.row, color = Gold) }
            if (!studio.supported) {
                item { Footnote(stringResource(R.string.studio_unsupported)) }
            } else {
                // The face itself, with a rim like a watch's own.
                item { StudioPreview(config, design, Modifier.size(Screen.dp(112f, 128f)).border(2.dp, PlatterHigh, CircleShape).padding(3.dp)) }
                item {
                    Text(stringResource(if (studio.active) R.string.studio_in_use else R.string.studio_not_in_use),
                        style = Type.row, color = if (studio.active) Success else Ink, textAlign = TextAlign.Center)
                }
                item { Footnote(stringResource(if (studio.fromPhone) R.string.studio_from_phone else R.string.studio_default_design)) }
                if (!studio.active) {
                    item { PrimaryButton(stringResource(R.string.studio_show), { onShowStudio() }) }
                    item { Footnote(stringResource(R.string.studio_pick_hint)) }
                }
                item { Footnote(stringResource(R.string.studio_design_on_phone)) }
            }
            if (showClassic) item { FaceSection(autoStart = false) }
        }
    }
}

/** The studio face as it is on the watch, drawn by the same renderer, live to the second. */
@Composable
fun StudioPreview(config: WatchConfig?, design: FaceDesign, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember { FaceRenderer(context, StudioStore.fonts(context)) }
    val frame = remember { FaceFrame() }
    val bounds = remember { Rect() }
    val now by produceState(ZonedDateTime.now()) { while (true) { delay(1000 - System.currentTimeMillis() % 1000); value = ZonedDateTime.now() } }
    val data = remember(design, config, now.minute, now.hour) {
        FaceData.build(design, config, now.toInstant(), now.zone, deviceArabic = java.util.Locale.getDefault().language == "ar")
    }
    val label = stringResource(R.string.studio_face_name)
    Box(modifier.clip(CircleShape).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            bounds.set(0, 0, size.width.toInt(), size.height.toInt())
            frame.set(now.hour, now.minute, now.second, ambient = false)
            drawIntoCanvas { renderer.draw(it.nativeCanvas, bounds, design, data, frame) }
        }
    }
}

@Composable
internal fun rememberStudio(): StudioUi {
    val context = LocalContext.current
    var ui by remember { mutableStateOf(StudioUi(StudioStore.supported(), StudioStore.active(context), StudioStore.stamp(context) != null)) }
    LifecycleResumeEffect(Unit) {
        ui = StudioUi(StudioStore.supported(), StudioStore.active(context), StudioStore.stamp(context) != null)
        onPauseOrDispose { }
    }
    return ui
}

@Composable
internal fun rememberStudioDesign(): FaceDesign {
    val context = LocalContext.current
    val flow = remember { StudioStore.designs(context) }
    return flow.collectAsState(StudioStore.design(context)).value
}
