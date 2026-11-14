package com.noor.app.sky

import android.app.ActivityManager
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.noor.app.data.Scenery
import com.noor.app.data.SkyGraphics

/**
 * The six sceneries of the real sky, as different from each other as land can be: sand, forest and snow peak, ice,
 * water, green hills and red rock. Each is a path-traced picture of a place (assets/sky2/land/<id>), which also says
 * how high the camera stands and where its water lies. [signatureYear] is the time of year it is shown in when the
 * seasons are not followed (0 midwinter .. 0.5 midsummer).
 */
enum class Biome(val id: String, val eye: Float, val focal: Float, val waterRough: Float, val waterClear: Float, val signatureYear: Float) {
    /** The Arabian sand sea, from the crest of a high dune. */
    DUNES("dunes", 1.9f, 1.15f, 0.3f, 0.2f, 0.12f),
    /** The Pacific Northwest: tall firs, still water, a snow-capped volcano. */
    RAINFOREST("pnw", 2.0f, 1.15f, 0.10f, 0.45f, 0.42f),
    /** The far north in winter: snow peaks over a fjord, the aurora. */
    ARCTIC("arctic", 2.0f, 1.15f, 0.08f, 0.30f, 0.0f),
    /** The sea and the sky. */
    OCEAN("ocean", 3.0f, 1.15f, 0.55f, 0.25f, 0.40f),
    /** Green hills and oaks in spring. */
    MEADOW("meadow", 2.0f, 1.15f, 0.12f, 0.35f, 0.30f),
    /** Red rock: buttes and mesas over a desert floor. */
    CANYON("canyon", 2.0f, 1.15f, 0.3f, 0.2f, 0.35f);

    companion object {
        private val ARID = setOf("AE", "SA", "QA", "KW", "BH", "OM", "YE", "IQ", "JO", "EG", "LY", "DZ", "TN", "MA", "EH", "MR", "ML", "NE", "TD", "SD", "DJ")
        private val ISLANDS = setOf(
            "MV", "SG", "MT", "CY", "MU", "SC", "KM", "BS", "BB", "FJ", "TO", "WS", "KI", "MH", "PW", "FM", "NR", "TV", "CV", "ST",
            "AG", "DM", "GD", "KN", "LC", "VC", "TT", "BM", "KY", "VU", "SB",
        )
        private val GRASSLANDS = setOf(
            "KE", "TZ", "UG", "ET", "SO", "SS", "ZM", "ZW", "BW", "NA", "MZ", "MW", "AO", "NG", "GH", "BF", "SN", "GM", "GN", "TG", "BJ",
            "CI", "CM", "CF", "ZA", "RW", "BI", "ER", "SZ", "LS",
        )
        private val ROCK = setOf("AF", "IR", "TR", "TJ", "KG", "UZ", "TM", "AZ", "AM", "PK", "MN", "PE", "BO", "CL", "MX")

        /**
         * The scenery for a choice in Settings. "Automatic" takes the one that fits the place: the far north and south
         * the ice, the deserts the dunes, small islands the ocean, grasslands the green hills, high dry country the
         * red rock, everywhere else the forest.
         */
        fun of(scenery: Scenery, countryCode: String?, elevationM: Double?, latitude: Double?): Biome = when (scenery) {
            Scenery.DUNES -> DUNES
            Scenery.HILLS, Scenery.RAINFOREST -> RAINFOREST
            Scenery.ARCTIC -> ARCTIC
            Scenery.SEA -> OCEAN
            Scenery.SAVANNA -> MEADOW
            Scenery.MOUNTAINS, Scenery.CANYON -> CANYON
            Scenery.AUTO -> {
                val cc = countryCode?.uppercase()
                when {
                    latitude != null && kotlin.math.abs(latitude) >= 60.0 -> ARCTIC
                    cc in ARID -> DUNES
                    cc in ISLANDS -> OCEAN
                    cc in GRASSLANDS -> MEADOW
                    cc in ROCK || (elevationM != null && elevationM >= 1400.0) -> CANYON
                    else -> RAINFOREST
                }
            }
        }
    }
}

/**
 * Whether the real sky can be used on this phone, at which quality, and what to do when it cannot.
 *
 * - It needs OpenGL ES 3.0 with float render targets (nearly every phone since about 2016). Where that is missing, or
 *   a shader is refused, [fail] is called and the 0.9 sky is used instead, for good until the app is updated.
 * - A native crash cannot be caught, so each start is counted before the renderer is built ([beginAttempt]) and
 *   cleared once a picture is on screen ([succeeded]): after three starts of this version that never got that far,
 *   the 0.9 sky is used.
 * - The quality tier is chosen from the phone's GPU and memory, and steps down by itself if pictures take too long
 *   ([stepDown]); Settings can fix it, or choose the 0.9 sky ("Classic").
 */
object RealSky {
    /**
     * How much is drawn: the size of the picture relative to the screen, how often it is redrawn (ms: at most, when
     * nothing but the air moves, when there is water), and by how much the clouds' and the land's pictures are
     * coarsened when read (1 = as shipped, 2 = half).
     */
    enum class Tier(val sceneScale: Float, val frameMs: Long, val stillMs: Long, val waterMs: Long, val cloudSample: Int, val landSample: Int) {
        ULTRA(1.00f, 33, 66, 33, 1, 1),
        HIGH(0.85f, 33, 100, 40, 1, 1),
        MEDIUM(0.72f, 40, 150, 55, 2, 1),
        LOW(0.58f, 50, 220, 80, 2, 2),
    }

    private const val PREFS = "noor_ui"
    private const val K_ATTEMPTS = "real_sky_attempts"
    private const val K_ATTEMPTS_FOR = "real_sky_attempts_for"
    private const val K_FAILED = "real_sky_failed_for"
    private const val K_TIER = "real_sky_tier"
    private const val TAG = "NoorRealSky"

    /** 0 = may be used, 1 = cannot be used on this phone (observed by the home screen). */
    val unusable = mutableIntStateOf(-1)
    /** The quality chosen in Settings. */
    @Volatile var graphics: SkyGraphics = SkyGraphics.AUTO

    /** The sky that holds the scene's place until its first picture; `final.frag` fades in from the same colour. */
    val WAITING_SKY = androidx.compose.ui.graphics.Color(0xFF26355C)
    @Volatile private var engine: RealSkyEngine? = null
    /** Tests and previews have no GPU. */
    @Volatile var forceOff = false
    /** When the sky was last touched (uptime, ms) and how many times: a touch sends a breath of wind through it. */
    @Volatile internal var gustAt = 0L
    @Volatile internal var gusts = 0

    /** A touch on the sky: a breath of wind. The clouds slide on, the grass bends, the water ruffles; then it settles. */
    fun gust() {
        gustAt = android.os.SystemClock.uptimeMillis()
        gusts++
        engine?.wake()
    }

    private fun version(context: Context): Long = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
    }.getOrDefault(0L)

    /** Whether to show the real sky now. */
    fun usable(context: Context, choice: SkyGraphics): Boolean {
        graphics = choice
        if (forceOff || choice == SkyGraphics.CLASSIC) return false
        if (unusable.intValue < 0) {
            val p = runCatching { context.getSharedPreferences(PREFS, 0) }.getOrNull()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val es3 = (am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
            val failed = p?.getLong(K_FAILED, -1L) == version(context)
            val crashed = (p?.getInt(K_ATTEMPTS, 0) ?: 0) >= 3 && p?.getLong(K_ATTEMPTS_FOR, -1L) == version(context)
            if (crashed) Log.w(TAG, "The real sky did not start three times: using the 0.9 sky")
            unusable.intValue = if (!es3 || failed || crashed || p == null) 1 else 0
        }
        return unusable.intValue == 0
    }

    internal fun engine(context: Context): RealSkyEngine = engine ?: synchronized(this) {
        engine ?: RealSkyEngine(context.applicationContext).also { engine = it }
    }

    internal fun beginAttempt(context: Context) {
        runCatching {
            val p = context.getSharedPreferences(PREFS, 0)
            val v = version(context)
            val n = if (p.getLong(K_ATTEMPTS_FOR, -1L) == v) p.getInt(K_ATTEMPTS, 0) else 0
            p.edit().putInt(K_ATTEMPTS, n + 1).putLong(K_ATTEMPTS_FOR, v).commit()
        }
    }

    /** The home screen was left before the first picture: that start does not count against the renderer. */
    internal fun cancelAttempt(context: Context) {
        runCatching { context.getSharedPreferences(PREFS, 0).let { it.edit().putInt(K_ATTEMPTS, (it.getInt(K_ATTEMPTS, 0) - 1).coerceAtLeast(0)).apply() } }
    }

    internal fun succeeded(context: Context) {
        runCatching { context.getSharedPreferences(PREFS, 0).edit().putInt(K_ATTEMPTS, 0).apply() }
    }

    internal fun fail(context: Context, why: String) {
        Log.w(TAG, "Real sky unavailable: $why")
        runCatching { context.getSharedPreferences(PREFS, 0).edit().putLong(K_FAILED, version(context)).putInt(K_ATTEMPTS, 0).apply() }
        android.os.Handler(android.os.Looper.getMainLooper()).post { unusable.intValue = 1 }
    }

    private val lifts = HashMap<String, FloatArray>()

    /**
     * How high a scenery's skyline stands where the Sun rises and where it sets: tangents of the angle over the
     * horizon, read once from the land's own picture (where its alpha begins, 30.24 % of the width to either side of
     * the middle; the lowest point within the Sun's own width, so the Sun is seen the moment it clears the land).
     * Zeros when there is no picture or it cannot be read: the Sun then rises and sets on the level horizon.
     */
    fun sunLift(context: Context, biome: Biome): FloatArray = synchronized(lifts) {
        lifts.getOrPut(biome.id) {
            runCatching {
                val dir = "sky2/land/${biome.id}"
                val meta = org.json.JSONObject(context.assets.open("$dir/land.json").bufferedReader().use { it.readText() })
                val bmp = context.assets.open("$dir/a.webp").use { android.graphics.BitmapFactory.decodeStream(it) } ?: error("unreadable")
                val width = meta.getDouble("width"); val top = meta.getDouble("top"); val horizon = meta.getDouble("horizon")
                val rows = meta.getDouble("height")
                val focal = meta.optDouble("focal", 1.15) * width
                fun at(u: Double): Float {
                    var low = Double.MAX_VALUE
                    var c = u - 0.0125
                    while (c <= u + 0.0125) {
                        val x = (c * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                        var y = 0
                        while (y < bmp.height && (bmp.getPixel(x, y) ushr 24) < 128) y++
                        val row = top + y * rows / bmp.height
                        low = minOf(low, (horizon - row) / focal)
                        c += 0.0025
                    }
                    return low.coerceIn(0.0, 0.5).toFloat()
                }
                floatArrayOf(at(0.5 - 0.3024), at(0.5 + 0.3024)).also { bmp.recycle(); Log.i(TAG, "Skyline of ${biome.id}: ${it[0]} where the Sun rises, ${it[1]} where it sets") }
            }.getOrElse { floatArrayOf(0f, 0f) }
        }
    }

    /** The tier to draw at: the one fixed in Settings, or the one that suits this phone (and has not proved too slow). */
    internal fun tierFor(context: Context, renderer: String): Tier {
        when (graphics) {
            SkyGraphics.ULTRA -> return Tier.ULTRA
            SkyGraphics.HIGH -> return Tier.HIGH
            SkyGraphics.MEDIUM -> return Tier.MEDIUM
            SkyGraphics.LOW -> return Tier.LOW
            else -> {}
        }
        val p = context.getSharedPreferences(PREFS, 0)
        val saved = p.getInt(K_TIER, -1)
        if (saved in Tier.entries.indices) return Tier.entries[saved]
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem / (1024.0 * 1024 * 1024)
        return guess(renderer, mem)
    }

    /** A first guess from the GPU's name and the phone's memory (GB); the frame timer corrects it. */
    internal fun guess(renderer: String, mem: Double): Tier {
        val r = renderer.lowercase()
        fun num(prefix: String): Int = Regex("$prefix\\s*(?:\\(tm\\)\\s*)?(\\d{3,4})").find(r)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val adreno = num("adreno")
        val mali = Regex("mali-g(\\d{2,3})").find(r)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val xclipse = num("xclipse")
        val immortalis = r.contains("immortalis")
        val top = adreno >= 730 || xclipse >= 920 || immortalis || mali in intArrayOf(710, 715, 720, 725) || r.contains("apple")
        val good = adreno in 640..729 || xclipse in 500..919 || mali in intArrayOf(76, 77, 78, 610, 615, 620) || r.contains("swiftshader") || r.contains("angle")
        val fair = adreno in 610..639 || mali in intArrayOf(52, 57, 68, 72, 310, 510)
        return when {
            top && mem >= 7.0 -> Tier.ULTRA
            top || (good && mem >= 5.5) -> Tier.HIGH
            good || fair -> Tier.MEDIUM
            else -> if (mem >= 7.0) Tier.MEDIUM else Tier.LOW
        }
    }

    /** Frames keep taking too long: draw less. False when already at the lowest tier or the tier is fixed in Settings. */
    internal fun stepDown(context: Context, from: Tier): Boolean {
        if (graphics != SkyGraphics.AUTO || from == Tier.LOW) return false
        val next = Tier.entries[from.ordinal + 1]
        context.getSharedPreferences(PREFS, 0).edit().putInt(K_TIER, next.ordinal).apply()
        Log.i(TAG, "Pictures take too long at ${from.name}: stepping down to ${next.name}")
        return true
    }
}

/** What the home screen's layout gives the real sky: which scenery, the size of the layer it fills (px), where the
 *  frosted glass under the day's times begins and how it is tinted. */
internal class RealSkySetup(
    val bridge: RealSkyBridge, val biome: Biome, val w: Float, val h: Float, val panelTop: Float, val radius: Float,
    val lightGlass: Boolean, val tint: FloatArray,
    /** The scenery follows the real season of the place (Settings; off unless asked). */
    val seasons: Boolean = false,
    /** The stars stand where they truly are for the place and time (Settings; off unless asked). */
    val trueStars: Boolean = false,
)

/** How the home screen hands the scene of the moment to the renderer behind it. */
internal class RealSkyBridge {
    /** Makes the frame for the current state; replaced whenever the home screen's state changes. */
    val frame = mutableStateOf<(() -> RealSkyFrame?)?>(null)
}

/**
 * The real sky as a layer of the screen: a view the renderer draws into. [frame] is read in a snapshot, so the renderer
 * is told whenever anything it shows has changed, and only then.
 */
@Composable
internal fun RealSkyLayer(modifier: Modifier, frame: () -> RealSkyFrame?) {
    val context = LocalContext.current
    val engine = remember { RealSky.engine(context) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = engine.attach(st, w, h)
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = engine.attach(st, w, h)
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { engine.detach(st); return true }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
    )
    LaunchedEffect(engine) { snapshotFlow { frame() }.collect { f -> if (f != null) engine.submit(f) } }
    LifecycleResumeEffect(engine) {
        engine.setVisible(true)
        onPauseOrDispose { engine.setVisible(false) }
    }
}
