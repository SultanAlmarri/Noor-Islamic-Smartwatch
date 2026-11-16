package com.noor.wear.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.noor.face.FaceFrame
import com.noor.face.FaceRenderer
import com.noor.prayer.WatchConfig
import com.noor.prayer.face.FaceData
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceGeometry
import com.noor.prayer.face.Hands
import com.noor.prayer.face.TapAction
import com.noor.wear.NoorWearApp
import com.noor.wear.ui.Dest
import com.noor.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Minhad Studio (0.18): the watch face the user designs on the phone, drawn by this app with the same renderer as the
 * phone's editor ([FaceRenderer]). It reads the design the phone sent ([StudioStore]) and the same settings the rest
 * of the app uses, redraws at once when either changes, and works on its own with the default design.
 *
 * Frames: once a second when it shows seconds, every 15 s otherwise (the minute and the countdown stay current to the
 * second they change, since frames line up with the clock); once a minute always-on, which the system decides.
 */
class StudioFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        val renderer = StudioRenderer(this, surfaceHolder, watchState, currentUserStyleRepository)
        return WatchFace(WatchFaceType.DIGITAL, renderer).setTapListener(renderer)
    }
}

private class StudioRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    styles: CurrentUserStyleRepository,
) : Renderer.CanvasRenderer2<StudioRenderer.Assets>(surfaceHolder, styles, watchState, CanvasType.HARDWARE, 15_000L, false),
    WatchFace.TapListener {

    class Assets : Renderer.SharedAssets { override fun onDestroy() {} }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val face = FaceRenderer(context, StudioStore.fonts(context))
    private val frame = FaceFrame()
    private val lastBounds = Rect()

    @Volatile private var design: FaceDesign = StudioStore.design(context)
    @Volatile private var config: WatchConfig? = null
    private var battery: Float? = null
    private var data: FaceData? = null
    private var dirty = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val b = if (level >= 0 && scale > 0) level / scale.toFloat() else null
            if (b != battery) { battery = b; dirty = true; invalidate() }
        }
    }

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch {
            StudioStore.designs(context).collectLatest { d ->
                design = d; dirty = true
                interactiveDrawModeUpdateDelayMillis = if (d.seconds) 1_000L else 15_000L
                invalidate()
            }
        }
        scope.launch {
            (context.applicationContext as NoorWearApp).config.config.collectLatest { c -> config = c; dirty = true; invalidate() }
        }
    }

    override suspend fun createSharedAssets() = Assets()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT
        val now = zonedDateTime.toInstant()
        val d = data
        if (dirty || d == null || !now.isBefore(d.validUntil)) {
            data = FaceData.build(design, config, now, zonedDateTime.zone, battery,
                deviceArabic = Locale.getDefault().language == "ar", device24h = DateFormat.is24HourFormat(context))
            dirty = false
        }
        frame.set(zonedDateTime.hour, zonedDateTime.minute, zonedDateTime.second, ambient)
        if (ambient) {
            val (x, y) = FaceFrame.shiftFor(zonedDateTime.minute, bounds.width() * 0.012f)
            frame.shiftX = x; frame.shiftY = y
        } else { frame.shiftX = 0f; frame.shiftY = 0f }
        lastBounds.set(bounds)
        face.draw(canvas, bounds, design, data!!, frame)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(renderParameters.highlightLayer?.backgroundTint ?: 0)
    }

    /** A touch on an element does what the design says (open Minhad, the Qibla, the tasbih, the calendar). */
    override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        if (tapType != TapType.UP || lastBounds.isEmpty) return
        val r = lastBounds.width() / 2f
        val slot = FaceGeometry.slotAt((tapEvent.xPos - lastBounds.exactCenterX()) / r, (tapEvent.yPos - lastBounds.exactCenterY()) / r, design) ?: return
        val action = design.at(slot)?.tapAction ?: (if (design.hands != Hands.NONE) TapAction.NONE else return)
        val intent = when (action) {
            TapAction.NONE -> return
            TapAction.APP -> Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            TapAction.QIBLA -> MainActivity.intent(context, Dest.QIBLA)
            TapAction.TASBIH -> MainActivity.intent(context, Dest.TASBIH)
            TapAction.CALENDAR -> MainActivity.intent(context, Dest.CALENDAR)
        }
        runCatching { context.startActivity(intent) }
    }

    override fun onDestroy() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        scope.cancel()
        super.onDestroy()
    }
}
