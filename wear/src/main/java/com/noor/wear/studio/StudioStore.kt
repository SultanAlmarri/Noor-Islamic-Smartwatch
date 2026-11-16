package com.noor.wear.studio

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.noor.face.FaceFonts
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceTemplates
import com.noor.wear.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Minhad Studio on the watch (0.18): the design the phone sent (or the default), kept in a small preferences file so
 * the face service reads it at once, and whether the studio face can run here and is the one in use.
 */
object StudioStore {
    private const val FILE = "minhad_studio"
    private const val KEY_DESIGN = "design"
    private const val KEY_STAMP = "stamp"
    private const val KEY_GUIDED = "guided"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun design(context: Context): FaceDesign = FaceDesign.decode(prefs(context).getString(KEY_DESIGN, null)) ?: FaceTemplates.DEFAULT

    /** When the phone sent the design in use (null: the default, nothing sent yet). */
    fun stamp(context: Context): Long? = prefs(context).getLong(KEY_STAMP, 0L).takeIf { it > 0L }

    /** Stores a design from the phone; returns false when it cannot be read (a newer format is still read in part). */
    fun save(context: Context, encoded: String, stamp: Long): Boolean {
        if (FaceDesign.decode(encoded) == null) return false
        prefs(context).edit().putString(KEY_DESIGN, encoded).putLong(KEY_STAMP, stamp).apply()
        return true
    }

    /** The design, now and whenever it changes. */
    fun designs(context: Context): Flow<FaceDesign> = callbackFlow {
        val p = prefs(context)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY_DESIGN) trySend(design(context)) }
        p.registerOnSharedPreferenceChangeListener(l)
        trySend(design(context))
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }

    /** The user has been shown, once, how to choose the studio face on the watch. */
    var Context.studioGuided: Boolean
        get() = prefs(this).getBoolean(KEY_GUIDED, false)
        set(v) { prefs(this).edit().putBoolean(KEY_GUIDED, v).apply() }

    fun component(context: Context) = ComponentName(context, StudioFaceService::class.java)

    /**
     * Watches that launched with Wear OS 6 run Watch Face Format faces only (Google: "watches that launch with Wear OS
     * 6 only support watch faces that use the Watch Face Format"); watches updated to it from an earlier version still
     * run app-drawn faces like this one.
     */
    fun supported(): Boolean = Build.VERSION.SDK_INT < 36 || (firstApiLevel ?: 0) < 36

    /**
     * The Android level the watch launched with: the system property `ro.product.first_api_level` (the SDK's
     * DEVICE_INITIAL_SDK_INT is not public). Read once, off the main thread ([enableForDevice]); unknown counts as
     * an updated watch, so the face stays offered.
     */
    @Volatile private var firstApiLevel: Int? = null

    private fun readFirstApiLevel(): Int? = runCatching {
        val p = ProcessBuilder("getprop", "ro.product.first_api_level").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor(); out.toIntOrNull()?.takeIf { it > 0 }
    }.getOrNull()

    /** The studio face is the watch face in use (the watch face is the system's live wallpaper). */
    fun active(context: Context): Boolean = runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)
    }.getOrDefault(false)

    /**
     * Asks the system to show the studio face with its own confirmation. Wear OS lets the user, not an app, choose the
     * watch face; where the system has no such screen this returns false and the watch explains how instead.
     */
    fun requestActive(context: Context): Boolean = runCatching {
        val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) == null) return false
        context.startActivity(i); true
    }.getOrDefault(false)

    /** On a watch that cannot run it, the studio face is taken out of the list of faces rather than failing there. */
    fun enableForDevice(context: Context) = Thread {
        firstApiLevel = readFirstApiLevel()
        val want = if (supported()) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            if (context.packageManager.getComponentEnabledSetting(component(context)) != want)
                context.packageManager.setComponentEnabledSetting(component(context), want, PackageManager.DONT_KILL_APP)
        }
    }.start()

    /** The app's bundled typefaces, the same files as the phone's. */
    fun fonts(context: Context): FaceFonts = runCatching {
        FaceFonts(
            ResourcesCompat.getFont(context, R.font.plex_arabic_light)!!, ResourcesCompat.getFont(context, R.font.plex_arabic_regular)!!,
            ResourcesCompat.getFont(context, R.font.plex_arabic_medium)!!, ResourcesCompat.getFont(context, R.font.reem_kufi)!!,
        )
    }.getOrDefault(FaceFonts.SYSTEM)
}
