package com.noor.app.sky

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.noor.app.data.SkyGraphics

/** (0.19) How the home sky is drawn, once chosen: the real sky, the 0.9 sky, or the simplest sky. */
enum class SkyLevel { HIGH, MEDIUM, LOW }

/**
 * The graphics level of the home sky (0.19; the owner: "three graphic settings: low, medium, high ... and an automatic
 * one, on by default, that sees your phone and chooses the one that suits it").
 *
 * - HIGH: the real sky (rendered sceneries, [RealSky]); it chooses its own quality steps ([RealSky.Tier]).
 * - MEDIUM: the 0.9 sky, the layered sky drawn on the phone (its GPU shaders on Android 13 and later, Canvas below).
 * - LOW: the simplest sky ([com.noor.app.ui.today.SimpleSkyLayer]), for any phone.
 *
 * Automatic guesses from what Android says about the phone ([guess]: memory, version, a low-memory device, OpenGL ES 3),
 * without measuring anything at start. Then the frames correct it: when the real sky is still too slow at its lowest
 * step, or the 0.9 sky at its lower resolution, automatic goes one level down ([lower]) and keeps it until the app is
 * updated (a new version may run better). A level chosen by hand is never changed. Where the real sky cannot run at
 * all (no OpenGL ES 3 or float targets, a failed start), the home screen draws the 0.9 sky, as before.
 */
object SkyLevels {
    private const val TAG = "NoorSkyLevel"
    private const val PREFS = "noor_ui"
    private const val K_AUTO = "sky_auto_level"
    private const val K_AUTO_FOR = "sky_auto_level_for"

    /** The level automatic stands at (null until worked out): read by the home screen, so a step down shows at once. */
    val autoLevel = androidx.compose.runtime.mutableStateOf<SkyLevel?>(null)
    /** The choice in Settings, as the home screen last read it. */
    @Volatile var choice: SkyGraphics = SkyGraphics.AUTO
        private set
    private val main = Handler(Looper.getMainLooper())

    /** The level to draw for the choice in Settings. */
    fun resolve(context: Context, choice: SkyGraphics): SkyLevel {
        this.choice = choice
        RealSky.graphics = choice
        return when (choice) {
            SkyGraphics.HIGH -> SkyLevel.HIGH
            SkyGraphics.MEDIUM -> SkyLevel.MEDIUM
            SkyGraphics.LOW -> SkyLevel.LOW
            SkyGraphics.AUTO -> auto(context)
        }
    }

    /** Automatic's level for this phone: kept from an earlier step down (this version), or guessed. */
    fun auto(context: Context): SkyLevel {
        autoLevel.value?.let { return it }
        val p = runCatching { context.getSharedPreferences(PREFS, 0) }.getOrNull()
        val kept = p?.takeIf { it.getLong(K_AUTO_FOR, -1L) == version(context) }?.getString(K_AUTO, null)
            ?.let { n -> SkyLevel.entries.firstOrNull { it.name == n } }
        val level = kept ?: run {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mem = runCatching { ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem / (1024.0 * 1024 * 1024) }.getOrDefault(0.0)
            val es3 = (am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
            guess(Build.VERSION.SDK_INT, mem, am?.isLowRamDevice == true, es3)
                .also { Log.i(TAG, "Automatic: $it (Android ${Build.VERSION.SDK_INT}, ${"%.1f".format(mem)} GB, low-RAM ${am?.isLowRamDevice}, ES3 $es3)") }
        }
        autoLevel.value = level
        return level
    }

    /**
     * The first guess, from what Android says about the phone. The real sky wants OpenGL ES 3 and some memory for its
     * pictures (about 3.5 GB of RAM on the phone); the 0.9 sky's GPU shaders need Android 13; a low-memory device, or
     * an old phone without them, gets the simplest sky.
     */
    internal fun guess(sdk: Int, memGb: Double, lowRam: Boolean, es3: Boolean): SkyLevel = when {
        lowRam || memGb in 0.01..2.6 || sdk < 26 -> SkyLevel.LOW
        es3 && sdk >= 28 && (memGb >= 3.5 || memGb <= 0.0) -> SkyLevel.HIGH
        sdk >= 33 && memGb >= 2.6 -> SkyLevel.MEDIUM
        else -> SkyLevel.LOW
    }

    /**
     * The sky at [from] keeps the phone too slow, even at its lowest quality: automatic goes one level down, now and
     * until the app is updated. Nothing changes when a level was chosen by hand, or at the lowest level.
     */
    fun lower(context: Context, from: SkyLevel) {
        if (choice != SkyGraphics.AUTO || from == SkyLevel.LOW) return
        val next = SkyLevel.entries[from.ordinal + 1]
        if ((autoLevel.value?.ordinal ?: 0) >= next.ordinal) return
        runCatching { context.getSharedPreferences(PREFS, 0).edit().putString(K_AUTO, next.name).putLong(K_AUTO_FOR, version(context)).apply() }
        Log.i(TAG, "Automatic: $from is too slow on this phone, stepping down to $next")
        main.post { if (autoLevel.value == null || autoLevel.value!!.ordinal < next.ordinal) autoLevel.value = next }
    }

    private fun version(context: Context): Long = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
    }.getOrDefault(0L)
}
