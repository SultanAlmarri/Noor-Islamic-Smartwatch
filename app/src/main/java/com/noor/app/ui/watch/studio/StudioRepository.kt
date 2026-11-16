package com.noor.app.ui.watch.studio

import android.content.Context
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.noor.app.R
import com.noor.face.FaceFonts
import com.noor.prayer.face.FaceDesign
import com.noor.prayer.face.FaceTemplates
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Minhad Studio on the phone (0.18): the design being edited (kept between visits), the user's saved designs, and
 * sending one to the watch over the Data Layer ([FaceDesign.PATH]), where the studio face redraws with it at once.
 */
class StudioRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("minhad_studio", Context.MODE_PRIVATE)

    var draft: FaceDesign
        get() = FaceDesign.decode(prefs.getString(KEY_DRAFT, null)) ?: FaceTemplates.DEFAULT
        set(v) { prefs.edit().putString(KEY_DRAFT, v.encode()).apply() }

    /** The user's own designs, newest first. */
    fun saved(): List<FaceDesign> = prefs.getString(KEY_IDS, "").orEmpty().split(',').filter { it.isNotBlank() }
        .mapNotNull { id -> FaceDesign.decode(prefs.getString("d.$id", null)) }

    /** Saves [d] under its id (a new id when it is a template or unsaved), and returns it as saved. */
    fun save(d: FaceDesign, name: String): FaceDesign {
        val id = d.id.takeIf { it.startsWith("my_") } ?: ("my_" + System.currentTimeMillis().toString(36))
        val out = d.copy(id = id, name = name.take(40))
        val ids = listOf(id) + prefs.getString(KEY_IDS, "").orEmpty().split(',').filter { it.isNotBlank() && it != id }
        prefs.edit().putString("d.$id", out.encode()).putString(KEY_IDS, ids.take(MAX_SAVED).joinToString(",")).apply()
        return out
    }

    fun delete(id: String) {
        val ids = prefs.getString(KEY_IDS, "").orEmpty().split(',').filter { it.isNotBlank() && it != id }
        prefs.edit().remove("d.$id").putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    /** When the design now on the watch was sent (the watch reports the same stamp back), or null. */
    val lastSentAt: Long? get() = prefs.getLong(KEY_SENT, 0L).takeIf { it > 0L }

    /**
     * Publishes [d] to the watches: delivered at once to a watch in reach, otherwise when it next connects. The stamp
     * makes sending the same design again a change the watch receives.
     */
    suspend fun send(d: FaceDesign): Long? = runCatching {
        val at = System.currentTimeMillis()
        val req = PutDataMapRequest.create(FaceDesign.PATH).apply {
            dataMap.putString(FaceDesign.KEY, d.encode()); dataMap.putLong(FaceDesign.KEY_AT, at)
        }.asPutDataRequest().setUrgent()
        withTimeoutOrNull(10_000) { Wearable.getDataClient(app).putDataItem(req).await() } ?: return null
        prefs.edit().putLong(KEY_SENT, at).apply()
        at
    }.onFailure { Log.w("MinhadStudio", "could not send the design", it) }.getOrNull()

    companion object {
        private const val KEY_DRAFT = "draft"
        private const val KEY_IDS = "ids"
        private const val KEY_SENT = "sent_at"
        const val MAX_SAVED = 24

        /** The phone's bundled typefaces: the same files the watch draws with. */
        fun fonts(context: Context): FaceFonts = runCatching {
            FaceFonts(
                ResourcesCompat.getFont(context, R.font.plex_arabic_light)!!, ResourcesCompat.getFont(context, R.font.plex_arabic_regular)!!,
                ResourcesCompat.getFont(context, R.font.plex_arabic_medium)!!, ResourcesCompat.getFont(context, R.font.reem_kufi)!!,
            )
        }.getOrDefault(FaceFonts.SYSTEM)
    }
}
