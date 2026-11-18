package com.noor.widgethost

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView

/**
 * Minhad's home-screen widgets as a launcher shows them, on a real Android system (tools/devicetest/widgets.sh).
 *
 * Like a launcher it is another app: it allocates widget ids on its own [AppWidgetHost], binds them to the widget
 * providers with [AppWidgetManager.bindAppWidgetIdIfAllowed] (allowed by `adb shell appwidget grantbind`), tells each
 * widget its size in the options bundle and, on Android 12+, with [AppWidgetHostView.updateAppWidgetSize]'s list of sizes,
 * and shows the [AppWidgetHostView]s that Android fills with the providers' own updates (binding makes Android send the
 * provider its APPWIDGET_UPDATE: the app's real `onUpdate`, nothing simulated). The widgets are inflated by Android's
 * RemoteViews in the restricted context a launcher gets.
 *
 * Extras of the launch intent (all optional):
 * - `pkg`: the widgets' app (default com.noor.smartwatch).
 * - `spec`: `auto` (default: every widget the app declares, each at a few sizes worked out from its provider info), or
 *   pages of `Provider=WxH,WxH;Provider=WxH` in dp, a provider by its class's simple or full name; a size may carry
 *   `+key=value` pairs, put as strings in the widget's options bundle (e.g. `TodayWidget=384x228+style=ring`).
 * - `page`: which page to show (0, 1, ...). Each provider starts a page; a provider whose sizes do not fit one screen
 *   goes on over several. Past the last page the host logs NOPAGE and closes.
 * - `bg`: `wallpaper` (default: the system's wallpaper behind the widgets, as on a home screen) or `neutral` (a plain grey
 *   of the system theme).
 * - `run`: a token repeated in every log line, so a script finds the lines of this launch.
 * - `hold`: at least this many ms after binding before the page is called ready (time for a script to set per-widget
 *   options after the BOUND lines); `quiet`: ms without a widget update before it is ready (default 4000);
 *   `timeout`: ms before it gives up (default 120000).
 *
 * Log lines (tag NoorWidgetHost): PROVIDERS, BOUND (id, provider, size), UPDATE (each RemoteViews received), ERROR (the
 * host had to show its error view: the widget could not be inflated), READY or TIMEOUT for the page, then for each widget
 * BOUNDS (its place on the screen, px) and VIEW (each visible text and picture in it), for the script's crops and records.
 */
class HostActivity : Activity() {

    private class Item(val info: AppWidgetProviderInfo, val wDp: Int, val hDp: Int, val extras: Map<String, String>) {
        val name: String get() = info.provider.className.substringAfterLast('.')
    }
    private class Cell(val item: Item, val id: Int, val view: HostView)

    private lateinit var host: AppWidgetHost
    private val cells = mutableListOf<Cell>()
    private val handler = Handler(Looper.getMainLooper())
    private var run = "run"
    private var started = 0L
    @Volatile private var lastUpdate = 0L
    private var reported = false
    private var hold = 0L
    private var quiet = 4_000L
    private var timeout = 120_000L
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        run = intent.getStringExtra("run") ?: "run"
        hold = intent.getLongExtra("hold", intent.getIntExtra("hold", 0).toLong())
        quiet = intent.getLongExtra("quiet", intent.getIntExtra("quiet", 4000).toLong())
        timeout = intent.getLongExtra("timeout", intent.getIntExtra("timeout", 120000).toLong())
        val pkg = intent.getStringExtra("pkg") ?: "com.noor.smartwatch"
        val spec = intent.getStringExtra("spec")?.takeIf { it.isNotBlank() } ?: "auto"
        val pageNo = intent.getIntExtra("page", 0)
        val wallpaper = (intent.getStringExtra("bg") ?: "wallpaper") != "neutral"
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fullScreen(wallpaper)
        host = object : AppWidgetHost(this, HOST_ID) {
            override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = HostView(context)
        }
        // Whatever an earlier launch left bound goes: the app updates every widget it has, and old ones would slow it.
        runCatching { host.deleteHost() }

        val manager = AppWidgetManager.getInstance(this)
        val installed = manager.getInstalledProvidersForPackage(pkg, null)
        log("PROVIDERS ${installed.size}: ${installed.joinToString { it.provider.className.substringAfterLast('.') }} " +
            "density=$density night=$night locale=${resources.configuration.locales[0].toLanguageTag()} sdk=${Build.VERSION.SDK_INT}")
        val pages = paginate(items(spec, installed))
        if (pageNo !in pages.indices) {
            log("NOPAGE page=$pageNo pages=${pages.size}")
            finish(); return
        }
        val page = pages[pageNo]
        log("PAGE page=$pageNo of ${pages.size} provider=${page.firstOrNull()?.firstOrNull()?.name} bg=${if (wallpaper) "wallpaper" else "neutral"}")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(PAD); setPadding(p, p, p, p)
        }
        val labelColor = if (wallpaper) Color.WHITE else if (night) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        for (row in page) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
            for ((i, item) in row.withIndex()) {
                val cell = bind(manager, item) ?: continue
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                box.addView(TextView(this).apply {
                    text = "${item.name}  ${item.wDp}×${item.hDp} dp"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(labelColor)
                    if (wallpaper) setShadowLayer(3f, 0f, 1f, 0xAA000000.toInt())
                    textDirection = View.TEXT_DIRECTION_LTR
                }, LinearLayout.LayoutParams(dp(item.wDp), dp(LABEL)))
                box.addView(cell.view, LinearLayout.LayoutParams(dp(item.wDp), dp(item.hDp)))
                line.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = dp(GAP)
                })
            }
            root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(GAP) })
        }
        // The page is laid out left to right whatever the language; each widget lays itself out as its app says.
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        setContentView(FrameLayout(this).apply {
            if (!wallpaper) setBackgroundColor(if (night) 0xFF1F1F1F.toInt() else 0xFFE6E6E6.toInt())
            addView(root)
        })
        started = SystemClock.uptimeMillis()
        handler.postDelayed(::check, 500)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); recreate()
    }

    override fun onStart() { super.onStart(); runCatching { host.startListening() } }
    override fun onStop() { super.onStop(); runCatching { host.stopListening() } }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    /** Allocates an id, binds it to the provider with the widget's size, and makes its host view. */
    private fun bind(manager: AppWidgetManager, item: Item): Cell? {
        val id = host.allocateAppWidgetId()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, item.wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, item.wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, item.hDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, item.hDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            if (Build.VERSION.SDK_INT >= 31) putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(item.wDp.toFloat(), item.hDp.toFloat())))
            for ((k, v) in item.extras) putString(k, v)
        }
        val ok = runCatching { manager.bindAppWidgetIdIfAllowed(id, item.info.provider, options) }.getOrElse { log("BIND failed ${item.name}: $it"); false }
        if (!ok) {
            log("BIND refused id=$id provider=${item.name}: run 'adb shell appwidget grantbind --package $packageName'")
            runCatching { host.deleteAppWidgetId(id) }
            return null
        }
        if (item.info.configure != null) log("NOTE ${item.name} declares a configuration activity (${item.info.configure.className}); the host does not open it")
        val view = host.createView(this, id, item.info) as HostView
        view.setPadding(0, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(options, listOf(SizeF(item.wDp.toFloat(), item.hDp.toFloat())))
        else @Suppress("DEPRECATION") view.updateAppWidgetSize(options, item.wDp, item.hDp, item.wDp, item.hDp)
        val extras = if (item.extras.isEmpty()) "" else " options=" + item.extras.entries.joinToString(",") { "${it.key}=${it.value}" }
        log("BOUND id=$id provider=${item.name} size=${item.wDp}x${item.hDp}$extras")
        return Cell(item, id, view).also { cells += it }
    }

    /** Ready when every widget has had an update and none came for [quiet] ms (and [hold] has passed). */
    private fun check() {
        if (reported || isFinishing) return
        val now = SystemClock.uptimeMillis()
        val updated = cells.count { it.view.updates > 0 }
        val ready = cells.isNotEmpty() && updated == cells.size && now - lastUpdate >= quiet && now - started >= hold
        if (ready || now - started > timeout) {
            reported = true
            log("${if (ready) "READY" else "TIMEOUT"} widgets=${cells.size} updated=$updated errors=${cells.sumOf { it.view.errors }} after=${now - started}ms")
            describe()
            return
        }
        handler.postDelayed(::check, 500)
    }

    /** Where each widget is on the screen, and what it shows, for the record. */
    private fun describe() {
        val res = runCatching { createPackageContext(cells.first().item.info.provider.packageName, 0).resources }.getOrNull()
        for (c in cells) {
            val xy = IntArray(2); c.view.getLocationOnScreen(xy)
            log("BOUNDS id=${c.id} provider=${c.item.name} size=${c.item.wDp}x${c.item.hDp} x=${xy[0]} y=${xy[1]} w=${c.view.width} h=${c.view.height}")
            walk(c.view) { v ->
                val name = if (v.id != View.NO_ID) runCatching { res?.getResourceEntryName(v.id) }.getOrNull() ?: "0x%x".format(v.id) else "-"
                when (v) {
                    is TextView -> if (v.isShown) {
                        val color = "#%08X".format(v.currentTextColor)
                        val size = "%.1f".format(java.util.Locale.ROOT, v.textSize / density)
                        val cut = v.layout?.let { l -> (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 } }
                        log("VIEW id=${c.id} ${v.javaClass.simpleName} $name text=\"${v.text}\" size=${size}dp color=$color lines=${v.lineCount} ellipsized=$cut shown=${v.width}x${v.height}")
                    }
                    is ImageView -> if (v.isShown) log("VIEW id=${c.id} ImageView $name picture=${(v.drawable as? BitmapDrawable)?.bitmap?.let { "${it.width}x${it.height}" } ?: v.drawable?.javaClass?.simpleName} shown=${v.width}x${v.height}")
                    else -> if (v.isShown && v !is ViewGroup) log("VIEW id=${c.id} ${v.javaClass.simpleName} $name shown=${v.width}x${v.height}")
                }
            }
        }
        log("DONE")
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

    // ---- What to show

    private fun items(spec: String, installed: List<AppWidgetProviderInfo>): List<List<Item>> {
        if (spec == "auto") return installed.map { info -> autoSizes(info).map { (w, h) -> Item(info, w, h, emptyMap()) } }
        return spec.split(';').filter { it.isNotBlank() }.mapNotNull { group ->
            val name = group.substringBefore('=').trim()
            val info = installed.firstOrNull { it.provider.className == name || it.provider.className.substringAfterLast('.') == name }
            if (info == null) { log("MISSING provider $name (installed: ${installed.joinToString { it.provider.className }})"); return@mapNotNull null }
            val sizes = group.substringAfter('=', "").split(',').filter { it.isNotBlank() }.ifEmpty { null }
            sizes?.mapNotNull { s ->
                val parts = s.trim().split('+')
                val wh = parts[0].split('x', 'X')
                val w = wh.getOrNull(0)?.toIntOrNull(); val h = wh.getOrNull(1)?.toIntOrNull()
                if (w == null || h == null) { log("BADSIZE $s"); null }
                else Item(info, w, h, parts.drop(1).associate { it.substringBefore('=') to it.substringAfter('=', "") })
            } ?: autoSizes(info).map { (w, h) -> Item(info, w, h, emptyMap()) }
        }
    }

    /**
     * Sizes in dp a launcher could give the widget: its target cells (Android 12+, else the cells its minimum size
     * needs), its smallest resize, the whole width at the target height and, if it grows down, one row more. A launcher
     * cell is taken as [CELL_W]×[CELL_H] dp with [CELL_GAP] between cells (about a 4×5 home screen on a 400 dp phone;
     * launchers differ: give exact sizes in the spec where it matters).
     */
    private fun autoSizes(info: AppWidgetProviderInfo): List<Pair<Int, Int>> {
        fun dp(px: Int) = (px / density).toInt()
        val minW = dp(info.minWidth); val minH = dp(info.minHeight)
        val rw = info.minResizeWidth.takeIf { it > 0 }?.let(::dp) ?: minW
        val rh = info.minResizeHeight.takeIf { it > 0 }?.let(::dp) ?: minH
        fun cellsFor(size: Int, cell: Int) = maxOf(1, (size + CELL_GAP + cell - 1) / (cell + CELL_GAP))
        val tw = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth else cellsFor(minW, CELL_W)
        val th = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight else cellsFor(minH, CELL_H)
        fun cells(cw: Int, ch: Int) = (cw * CELL_W + (cw - 1) * CELL_GAP) to (ch * CELL_H + (ch - 1) * CELL_GAP)
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val out = mutableListOf(cells(tw, th))
        if (horizontal || vertical) out += (if (horizontal) rw else out[0].first) to (if (vertical) rh else out[0].second)
        if (horizontal && tw < 4) out += cells(4, th)
        if (vertical) out += cells(tw, th + 1)
        val maxW = if (Build.VERSION.SDK_INT >= 31) info.maxResizeWidth.takeIf { it > 0 }?.let(::dp) else null
        val maxH = if (Build.VERSION.SDK_INT >= 31) info.maxResizeHeight.takeIf { it > 0 }?.let(::dp) else null
        return out.map { (w, h) -> minOf(w, maxW ?: w) to minOf(h, maxH ?: h) }.distinct()
    }

    /** Rows that fit the screen's width, and pages of rows that fit its height; each provider starts a new page. */
    private fun paginate(groups: List<List<Item>>): List<List<List<Item>>> {
        val screen = screenDp()
        val width = screen.first - 2 * PAD; val height = screen.second - 2 * PAD
        val pages = mutableListOf<List<List<Item>>>()
        for (group in groups) {
            val rows = mutableListOf<MutableList<Item>>()
            for (item in group) {
                val row = rows.lastOrNull()
                if (row != null && row.sumOf { it.wDp } + GAP * row.size + item.wDp <= width) row += item else rows += mutableListOf(item)
            }
            var page = mutableListOf<List<Item>>(); var used = 0
            for (row in rows) {
                val h = row.maxOf { it.hDp } + LABEL + GAP
                if (page.isNotEmpty() && used + h > height) { pages += page; page = mutableListOf(); used = 0 }
                page += row; used += h
            }
            if (page.isNotEmpty()) pages += page
        }
        return pages
    }

    private fun screenDp(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            (b.width() / density).toInt() to (b.height() / density).toInt()
        } else {
            val m = android.util.DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            (m.widthPixels / density).toInt() to (m.heightPixels / density).toInt()
        }
    }

    private fun fullScreen(wallpaper: Boolean) {
        if (wallpaper) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private fun log(s: String) { Log.i(TAG, "$run $s") }

    /** A launcher's host view that says when it receives an update, and when it cannot show one. */
    private inner class HostView(context: Context) : AppWidgetHostView(context) {
        var updates = 0
        var errors = 0
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) {
                updates++; lastUpdate = SystemClock.uptimeMillis()
                val layout = runCatching { createPackageContext(remoteViews.`package`, 0).resources.getResourceEntryName(remoteViews.layoutId) }.getOrNull()
                log("UPDATE id=$appWidgetId provider=${appWidgetInfo?.provider?.className?.substringAfterLast('.')} layout=$layout n=$updates")
            }
        }
        override fun getErrorView(): View {
            errors++
            log("ERROR id=$appWidgetId provider=${appWidgetInfo?.provider?.className?.substringAfterLast('.')}: the host shows its error view (the widget could not be inflated)")
            return super.getErrorView()
        }
    }

    private companion object {
        const val TAG = "NoorWidgetHost"
        const val HOST_ID = 0x4E57
        const val PAD = 12
        const val GAP = 12
        const val LABEL = 16
        const val CELL_W = 90
        const val CELL_H = 110
        const val CELL_GAP = 8
    }
}
