package com.noor.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.Executor

/** Software renders of watch screens and tiles (not photos of a watch), masked to the round display. */
object Shots {
    val dir = File("build/screenshots").apply { mkdirs() }

    fun save(bmp: Bitmap, name: String): File {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.TRANSPARENT)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(bmp.width / 2f, bmp.height / 2f, bmp.width / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(bmp, 0f, 0f, p)
        return File(dir, "$name.png").also { f -> f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    fun drawView(v: View): Bitmap {
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.BLACK); v.draw(c)
        return bmp
    }

    fun device(dp: Int, density: Float = 2f): DeviceParametersBuilders.DeviceParameters = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(dp).setScreenHeightDp(dp).setScreenDensity(density)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND).build()

    /** Renders a tile layout with the same renderer library the watch uses. */
    fun tile(activity: ComponentActivity, root: LayoutElementBuilders.LayoutElement, resources: ResourceBuilders.Resources, px: Int): Bitmap {
        val parent = FrameLayout(activity)
        activity.runOnUiThread { activity.setContentView(parent, ViewGroup.LayoutParams(px, px)) }
        shadowOf(Looper.getMainLooper()).idle()
        val renderer = TileRenderer(activity, Executor { it.run() }) { }
        renderer.inflateAsync(LayoutElementBuilders.Layout.Builder().setRoot(root).build(), resources, parent)
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
        parent.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, px, px)
        return drawView(parent)
    }

    /** Every static text in a tile layout, for checks that do not depend on how the test renderer draws fonts. */
    fun texts(e: LayoutElementBuilders.LayoutElement): List<String> = when (e) {
        is LayoutElementBuilders.Text -> listOfNotNull(e.text?.value)
        is LayoutElementBuilders.Box -> e.contents.flatMap(::texts)
        is LayoutElementBuilders.Column -> e.contents.flatMap(::texts)
        is LayoutElementBuilders.Row -> e.contents.flatMap(::texts)
        else -> emptyList()
    }
}
