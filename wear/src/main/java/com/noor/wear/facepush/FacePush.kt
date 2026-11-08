package com.noor.wear.facepush

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import com.noor.prayer.WatchFaces
import java.io.File

/** State of the Noor watch face that this app carries and installs (Watch Face Push, Wear OS 6+). */
sealed interface FaceState {
    /** The watch runs Wear OS 5 or older. */
    data object Unsupported : FaceState
    /** This build does not contain the watch faces (built without the face step). */
    data object NotBundled : FaceState
    data class Ready(
        /** The Noor face installed in its slot ([WatchFaces] id), or null. */
        val installedVariant: String?,
        /** Its build number (an older build is replaced by this app's), or null when not installed. */
        val installedBuild: Long?,
        val bundledBuild: Long,
        val active: Boolean,
        /** The face the user asked for last (on the phone or on the watch). */
        val chosen: String,
        /** This app has used its one chance to make the face active (Wear OS allows one). */
        val activationUsed: Boolean,
    ) : FaceState {
        val installed get() = installedBuild != null
        /** The face in the slot is not the one asked for, or is from an older build. */
        val updateAvailable get() = installed && (installedVariant != chosen || (installedBuild ?: 0) < bundledBuild)
        /** Installed but not in use, and only the user can change that now (touch and hold the watch face). */
        val needsPick get() = installed && !active && activationUsed
    }
    data class Failed(val message: String) : FaceState
}

/** The system's watch face slots for this app, as [FacePush] uses them (a fake stands in for tests). */
interface FaceSlots {
    /** The slot holding Noor's face, its build number and (when the system reports it) the variant inside. */
    data class Slot(val id: String, val build: Long, val variant: String?)

    suspend fun find(): Slot?
    suspend fun add(apk: File, token: String): String
    suspend fun update(slot: String, apk: File, token: String)
    suspend fun isActive(): Boolean
    suspend fun setActive(slot: String)
}

/** Raised by [FaceSlots.setActive] when the system refuses because the one attempt was already used. */
class AttemptsUsedException(cause: Throwable?) : Exception(cause)

/**
 * Installs, switches and (once) activates the Noor watch face.
 *
 * - Switching between designs and colours replaces the face in its slot in place ([FaceSlots.update]): the slot,
 *   its active state and the user's settings for it are kept, so a change from the phone shows at once.
 * - Wear OS lets an app make its pushed face the active one only once. This app tries at most once (remembered),
 *   and if the system says that chance was used, it says so: from then on the user chooses Noor on the watch
 *   (touch and hold the watch face). It is never shown as an error (0.9.7 showed "maximum number of attempts").
 */
class FacePush internal constructor(
    private val context: Context,
    private val slots: FaceSlots?,
    private val catalogue: Catalogue,
) {
    /**
     * Always on the application context: Watch Face Push binds to a system service, which a broadcast receiver's
     * context may not do, and the manager keeps the first context it was made with (a Wear OS 6 emulator showed every
     * later call failing, the face screen included, after one from a receiver).
     */
    constructor(context: Context) : this(context.applicationContext, systemSlots(context.applicationContext), Catalogue.of(context.applicationContext))

    /** The faces this build carries: ids, validation tokens, the build number, and how to read each APK. */
    class Catalogue(val ids: List<String>, val tokens: List<String>, val build: Long, val open: (String) -> java.io.InputStream?) {
        fun token(id: String) = ids.indexOf(id).takeIf { it >= 0 }?.let { tokens.getOrNull(it) }

        companion object {
            fun of(context: Context): Catalogue {
                val r = context.resources
                fun id(name: String, type: String) = r.getIdentifier(name, type, context.packageName)
                val ids = id("noor_face_ids", "array").takeIf { it != 0 }?.let { r.getStringArray(it).toList() }.orEmpty()
                val tokens = id("noor_face_tokens", "array").takeIf { it != 0 }?.let { r.getStringArray(it).toList() }.orEmpty()
                val build = id("noor_face_version_code", "integer").takeIf { it != 0 }?.let { r.getInteger(it).toLong() } ?: 0L
                return Catalogue(ids, tokens, build) { v -> runCatching { context.assets.open("faces/$v.apk") }.getOrNull() }
            }
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val bundled get() = catalogue.ids.isNotEmpty() && catalogue.tokens.size == catalogue.ids.size

    /** The face asked for last; the Horizon design in gold until then. */
    val chosen: String get() = prefs.getString(KEY_CHOSEN, null)?.takeIf { it in catalogue.ids } ?: WatchFaces.DEFAULT.id

    private var activationUsed: Boolean
        get() = prefs.getBoolean(KEY_ACTIVATION_USED, false)
        set(v) { prefs.edit().putBoolean(KEY_ACTIVATION_USED, v).commit() }

    suspend fun state(): FaceState {
        val s = slots ?: return FaceState.Unsupported
        if (!bundled) return FaceState.NotBundled
        return runCatching {
            val slot = s.find()
            // The variant inside: what the system reports, else what this app installed there last; a face from
            // 0.9.6-0.9.7 (build 1) is the original design.
            val variant = slot?.let { it.variant ?: if (it.build <= 1) WatchFaces.ORIGINAL_ID else prefs.getString(KEY_INSTALLED, null) }
            FaceState.Ready(variant, slot?.build, catalogue.build, slot != null && s.isActive(), chosen, activationUsed)
        }.getOrElse { FaceState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /** Remembers [variant] as the face asked for and puts it on the watch. */
    suspend fun choose(variant: String): FaceState {
        if (variant in catalogue.ids) prefs.edit().putString(KEY_CHOSEN, variant).commit()
        return installOrUpdate()
    }

    /** Adds the chosen face to the watch's faces, or replaces the one in Noor's slot. Never touches other faces. */
    suspend fun installOrUpdate(): FaceState {
        val s = slots ?: return FaceState.Unsupported
        if (!bundled) return FaceState.NotBundled
        val variant = chosen
        val token = catalogue.token(variant) ?: return FaceState.NotBundled
        return runCatching {
            val file = File(context.cacheDir, "noor_face.apk")
            (catalogue.open(variant) ?: error("face $variant missing")).use { input -> file.outputStream().use { input.copyTo(it) } }
            try {
                val slot = s.find()
                if (slot == null) s.add(file, token) else s.update(slot.id, file, token)
            } finally { file.delete() }
            prefs.edit().putString(KEY_INSTALLED, variant).commit()
            state()
        }.getOrElse { e ->
            Log.e(TAG, "install failed", e)
            FaceState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Makes the Noor face the active one, if this app may still do so (once, with the user's permission). After that,
     * or if the system says the chance was used, the state reports [FaceState.Ready.needsPick] instead of an error.
     */
    suspend fun activate(): FaceState {
        val s = slots ?: return FaceState.Unsupported
        val current = state()
        if (current !is FaceState.Ready || !current.installed || current.active || current.activationUsed) return current
        return runCatching {
            val slot = s.find() ?: return current
            activationUsed = true
            s.setActive(slot.id)
            state()
        }.getOrElse { e ->
            if (e is AttemptsUsedException) state()
            else { Log.w(TAG, "activation failed", e); FaceState.Failed(e.message ?: e.javaClass.simpleName) }
        }
    }

    companion object {
        const val FACE_PACKAGE = "com.noor.smartwatch.watchfacepush.noor"
        const val PERMISSION_ACTIVATE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
        private const val PREFS = "noor_face"
        private const val KEY_CHOSEN = "chosen"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_ACTIVATION_USED = "activation_used"
        private const val TAG = "NoorFacePush"

        private fun systemSlots(context: Context): FaceSlots? {
            if (Build.VERSION.SDK_INT < 36) return null // Wear OS 6
            val m = runCatching { WatchFacePushManagerFactory.createWatchFacePushManager(context) }.getOrNull() ?: return null
            return SystemSlots(m)
        }
    }
}

/** The real slots: Wear OS's Watch Face Push. */
private class SystemSlots(private val m: WatchFacePushManager) : FaceSlots {
    override suspend fun find(): FaceSlots.Slot? =
        m.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FacePush.FACE_PACKAGE }?.let { d ->
            val variant = runCatching { d.getMetaData("noor.face.variant").invoke().firstOrNull() }.getOrNull()
            FaceSlots.Slot(d.slotId, d.versionCode, variant?.takeIf { WatchFaces.parse(it) != null })
        }

    override suspend fun add(apk: File, token: String): String =
        ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { m.addWatchFace(it, token).slotId }

    override suspend fun update(slot: String, apk: File, token: String) {
        ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { m.updateWatchFace(slot, it, token) }
    }

    override suspend fun isActive(): Boolean = m.isWatchFaceActive(FacePush.FACE_PACKAGE)

    override suspend fun setActive(slot: String) {
        try {
            m.setWatchFaceAsActive(slot)
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            if (e.errorCode == WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MAXIMUM_ATTEMPTS_REACHED) throw AttemptsUsedException(e)
            throw e
        }
    }
}
