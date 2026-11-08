package com.noor.app.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.noor.app.R
import com.noor.app.widget.NextPrayerWidget
import com.noor.app.widget.SkyWidget
import com.noor.app.widget.TodayWidget
import com.noor.app.widget.Widgets

/**
 * Debug builds only (0.9.12): a home screen of Noor's widgets for tools/devicetest/widgets.sh. The widgets are bound and
 * shown exactly as a launcher shows them — through Android's widget service and its AppWidgetHostView, which loads the
 * widget's layout in the restricted mode where an app's own fonts are refused (the cause of 0.9.11's wrong digits on
 * phones, which the JVM tests could not see). Needs `adb shell appwidget grantbind --package <this app>`.
 *
 * Logs (tag NoorWidgetHost) the place on screen of each widget's countdown, its picture and its seconds window, so the
 * test can photograph them second after second.
 */
class WidgetHostActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID)
        host.deleteHost()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(28, 30, 36))
            setPadding(dp(12), dp(36), dp(12), dp(12))
        }
        // The sizes a launcher gives on a phone: 2 x 1, 4 x 2, 4 x 2 and a 4 x 3 sky.
        val widgets = listOf(
            Triple(NextPrayerWidget::class.java, 180, 84), Triple(TodayWidget::class.java, 340, 170),
            Triple(SkyWidget::class.java, 340, 170), Triple(SkyWidget::class.java, 340, 260),
        )
        for ((i, w) in widgets.withIndex()) {
            val (cls, wDp, hDp) = w
            val id = host.allocateAppWidgetId()
            val ok = manager.bindAppWidgetIdIfAllowed(id, ComponentName(this, cls))
            Log.i(TAG, "BIND ${cls.simpleName} id=$id ok=$ok")
            if (!ok) continue
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
            })
            val view = host.createView(applicationContext, id, manager.getAppWidgetInfo(id))
            view.tag = "$i ${cls.simpleName}"
            column.addView(view, LinearLayout.LayoutParams(dp(wDp), dp(hDp)).apply { topMargin = dp(10) })
        }
        setContentView(column)
        Widgets.refreshAsync(this)
        // Where each countdown is, once drawn (and again later, should the layout have changed).
        for (delay in listOf(8000L, 20000L)) main.postDelayed({ report(column) }, delay)
    }

    private fun report(column: ViewGroup) {
        for (k in 0 until column.childCount) {
            val v = column.getChildAt(k)
            fun place(id: Int): String {
                val c = v.findViewById<View>(id) ?: return "none"
                if (c.visibility != View.VISIBLE || c.width == 0) return "hidden"
                val xy = IntArray(2); c.getLocationOnScreen(xy)
                return "${xy[0]},${xy[1]},${c.width},${c.height}"
            }
            Log.i(TAG, "WIDGET ${v.tag} main=${place(R.id.widget_count_main)} window=${place(R.id.widget_count_window)} plain=${place(R.id.widget_count_plain)}")
        }
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "NoorWidgetHost"
        const val HOST_ID = 4711
    }
}
