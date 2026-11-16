package com.noor.app.ui.watch.studio

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.noor.face.FaceFrame
import com.noor.face.FaceRenderer
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceGeometry
import com.noor.prayer.face.Slot
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The watch's battery is not known on the phone: the preview shows a typical level. */
private const val PREVIEW_BATTERY = 0.76f

/**
 * The face exactly as the watch draws it: the same [FaceRenderer] with the same [FaceData], built from the very
 * configuration the phone sends the watch. [clock] fixes the moment (tests, template cards); otherwise it runs live,
 * redrawing each second without recomposing.
 */
@Composable
fun FaceCanvas(
    design: FaceDesign, config: WatchConfig?, ambient: Boolean, modifier: Modifier = Modifier,
    clock: ZonedDateTime? = null, deviceArabic: Boolean = false, description: String? = null,
) {
    val context = LocalContext.current
    val renderer = remember { FaceRenderer(context, StudioRepository.fonts(context)) }
    val frame = remember { FaceFrame() }
    val bounds = remember { Rect() }
    val zone = config?.zone ?: ZoneId.systemDefault()
    val live by produceState(clock ?: ZonedDateTime.now(zone), clock, zone) {
        if (clock != null) { value = clock; return@produceState }
        while (true) { value = ZonedDateTime.now(zone); delay(1000 - System.currentTimeMillis() % 1000) }
    }
    val minute = live.toEpochSecond() / 60
    val data = remember(design, config, minute, deviceArabic) {
        FaceData.build(design, config, live.toInstant(), zone, PREVIEW_BATTERY, deviceArabic = deviceArabic)
    }
    Canvas(modifier.aspectRatio(1f).clip(CircleShape).semantics { if (description != null) contentDescription = description }) {
        val t = live
        bounds.set(0, 0, size.width.toInt(), size.height.toInt())
        frame.set(t.hour, t.minute, t.second, ambient)
        if (ambient && design.pixelShift) {
            val (x, y) = FaceFrame.shiftFor(t.minute, size.width * 0.012f); frame.shiftX = x; frame.shiftY = y
        } else { frame.shiftX = 0f; frame.shiftY = 0f }
        drawIntoCanvas { renderer.draw(it.nativeCanvas, bounds, design, data, frame) }
    }
}

/**
 * The editor's live preview: the face, with the places ([Slot]) drawn over it. Tap a place to choose what goes there;
 * touch and hold an element and drag it to another place (the two swap), with a tick at each place it passes.
 */
@Composable
fun StudioPreview(
    design: FaceDesign, config: WatchConfig?, ambient: Boolean, selected: Slot?, accent: Color,
    onTap: (Slot?) -> Unit, onMove: (Slot, Slot) -> Unit, modifier: Modifier = Modifier,
    deviceArabic: Boolean = false, description: String? = null, editing: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(design)
    var dragFrom by remember { mutableStateOf<Slot?>(null) }
    var dragTo by remember { mutableStateOf<Slot?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.aspectRatio(1f).clip(CircleShape).background(Color.Black)) {
        FaceCanvas(design, config, ambient, Modifier.fillMaxSize(), deviceArabic = deviceArabic, description = description)
        if (!editing) return@Box
        fun slotAt(o: Offset, w: Float): Slot? = FaceGeometry.slotAt((o.x - w / 2f) / (w / 2f), (o.y - w / 2f) / (w / 2f), current)
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { o -> onTap(slotAt(o, size.width.toFloat())) } }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o ->
                            val s = slotAt(o, size.width.toFloat())
                            if (s != null && current.at(s) != null) {
                                dragFrom = s; dragTo = s; finger = o
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, delta ->
                            if (dragFrom == null) return@detectDragGesturesAfterLongPress
                            change.consume()
                            finger += delta
                            val s = slotAt(finger, size.width.toFloat())
                            if (s != null && s != dragTo) { dragTo = s; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        },
                        onDragEnd = {
                            val a = dragFrom; val b = dragTo
                            dragFrom = null; dragTo = null
                            if (a != null && b != null && a != b) onMove(a, b)
                        },
                        onDragCancel = { dragFrom = null; dragTo = null },
                    )
                },
        ) {
            val w = size.width
            val from = dragFrom
            for (s in Slot.entries) {
                val has = design.at(s) != null
                when {
                    from != null && s == dragTo -> slotOutline(s, design, w, accent, 1f, filled = true)
                    from != null && s == from -> slotOutline(s, design, w, Color.White, 0.35f, dashed = true)
                    from != null -> slotOutline(s, design, w, Color.White, 0.22f, dashed = true)
                    s == selected -> slotOutline(s, design, w, accent, 1f)
                    // An empty place is shown where it is free; one that lies under another's element (left and right
                    // under a wide centre) is reached by its chip instead, so nothing is drawn over the time.
                    !has && Slot.entries.none { o -> o != s && design.at(o) != null && overlaps(FaceGeometry.box(s, design), FaceGeometry.box(o, design)) } ->
                        slotOutline(s, design, w, Color.White, 0.18f, dashed = true, plus = true)
                }
            }
            if (from != null) {
                // The element under the finger, as a small glowing disc.
                drawCircle(accent.copy(alpha = 0.35f), radius = w * 0.07f, center = finger)
                drawCircle(accent, radius = w * 0.07f, center = finger, style = Stroke(width = w * 0.006f))
            }
        }
    }
}

private fun overlaps(a: FaceGeometry.Box, b: FaceGeometry.Box) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

private fun DrawScope.slotOutline(s: Slot, d: FaceDesign, w: Float, color: Color, alpha: Float, dashed: Boolean = false, filled: Boolean = false, plus: Boolean = false) {
    val r = w / 2f
    val b = FaceGeometry.box(s, d)
    val left = r + b.left * r; val top = r + b.top * r
    val bw = b.hw * 2f * r; val bh = b.hh * 2f * r
    val corner = if (s.small) minOf(bw, bh) / 2f else bh * 0.35f
    val stroke = Stroke(width = w * 0.006f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(w * 0.02f, w * 0.014f)) else null)
    if (filled) drawRoundRect(color.copy(alpha = 0.18f), Offset(left, top), Size(bw, bh), CornerRadius(corner))
    drawRoundRect(color.copy(alpha = alpha), Offset(left, top), Size(bw, bh), CornerRadius(corner), style = stroke)
    if (plus) {
        val cx = r + b.cx * r; val cy = r + b.cy * r; val k = w * 0.018f
        drawLine(color.copy(alpha = alpha * 1.8f), Offset(cx - k, cy), Offset(cx + k, cy), strokeWidth = w * 0.006f)
        drawLine(color.copy(alpha = alpha * 1.8f), Offset(cx, cy - k), Offset(cx, cy + k), strokeWidth = w * 0.006f)
    }
}

/** The ARGB int of a Compose colour (for the design's colours). */
internal fun Color.argb(): Int = toArgb()

/** A fixed moment for pictures that must not move (template cards in tests). */
internal fun fixedMoment(zone: ZoneId): ZonedDateTime = Instant.parse("2026-11-16T10:09:30Z").atZone(zone)
