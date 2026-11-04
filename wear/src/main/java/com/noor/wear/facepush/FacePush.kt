package com.noor.wear.facepush

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import java.io.File

/** State of the Noor watch face that this app carries and installs (Watch Face Push, Wear OS 6+). */
sealed interface FaceState {
    /** The watch runs Wear OS 5 or older. */
    data object Unsupported : FaceState
    /** This build does not contain the watch face (built without the face step). */
    data object NotBundled : FaceState
    data class Ready(val installedVersion: Long?, val bundledVersion: Long, val active: Boolean) : FaceState {
        val installed get() = installedVersion != null
        val updateAvailable get() = installedVersion != null && installedVersion < bundledVersion
    }
    data class Failed(val message: String) : FaceState
}

class FacePush(private val context: Context) {

    private val supported = Build.VERSION.SDK_INT >= 36 // Wear OS 6
    private val manager: WatchFacePushManager? by lazy {
        if (supported) runCatching { WatchFacePushManagerFactory.createWatchFacePushManager(context) }.getOrNull() else null
    }

    private val token: String? get() =
        context.resources.getIdentifier("default_wf_token", "string", context.packageName)
            .takeIf { it != 0 }?.let { context.getString(it) }?.takeIf { it.isNotBlank() }

    private val bundledVersion: Long get() =
        context.resources.getIdentifier("noor_face_version_code", "integer", context.packageName)
            .takeIf { it != 0 }?.let { context.resources.getInteger(it).toLong() } ?: 0L

    private val bundled: Boolean get() = token != null && runCatching { context.assets.open(ASSET).close() }.isSuccess

    suspend fun state(): FaceState {
        val m = manager ?: return FaceState.Unsupported
        if (!bundled) return FaceState.NotBundled
        return runCatching {
            val mine = m.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FACE_PACKAGE }
            FaceState.Ready(mine?.versionCode, bundledVersion, mine != null && m.isWatchFaceActive(FACE_PACKAGE))
        }.getOrElse { FaceState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /** Adds the face to the watch's picker, or replaces an older copy. Never touches other watch faces. */
    suspend fun installOrUpdate(): FaceState {
        val m = manager ?: return FaceState.Unsupported
        val t = token ?: return FaceState.NotBundled
        return runCatching {
            val file = File(context.cacheDir, ASSET)
            context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val slot = m.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FACE_PACKAGE }?.slotId
                if (slot == null) m.addWatchFace(pfd, t) else m.updateWatchFace(slot, pfd, t)
            }
            file.delete()
            state()
        }.getOrElse { e ->
            Log.e(TAG, "install failed", e)
            FaceState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Makes the Noor face the active one. The system allows an app to do this only once, with the user's permission. */
    suspend fun activate(): FaceState {
        val m = manager ?: return FaceState.Unsupported
        return runCatching {
            val slot = m.listWatchFaces().installedWatchFaceDetails.first { it.packageName == FACE_PACKAGE }.slotId
            m.setWatchFaceAsActive(slot)
            state()
        }.getOrElse { e -> FaceState.Failed(e.message ?: e.javaClass.simpleName) }
    }

    companion object {
        const val FACE_PACKAGE = "com.noor.smartwatch.watchfacepush.noor"
        const val ASSET = "default_watchface.apk"
        const val PERMISSION_ACTIVATE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
        private const val TAG = "NoorFacePush"
    }
}
