package com.noor.prayer

/**
 * What the watch app reports back to the phone (watch → phone Data Layer item), so the phone can show the real
 * state of the watch: app version, Wear OS level, and the Noor watch face. Same tolerant key=value format as
 * [WatchConfig].
 */
data class WatchReport(
    val appVersionCode: Long,
    val appVersionName: String,
    /** Android API level of the watch (Wear OS 6 = 36). */
    val sdkInt: Int,
    /** Watch Face Push is available (Wear OS 6+). */
    val facePushSupported: Boolean,
    /** Version of the face carried by this watch app, 0 if none. */
    val faceBundledVersion: Long,
    /** Version of the face installed on the watch, null if not installed. */
    val faceInstalledVersion: Long?,
    val faceActive: Boolean,
    /** When the watch last stored settings from the phone. */
    val configReceivedAtMs: Long?,
    val updatedAtMs: Long,
    /** Whether the watch may show notifications (null = older watch app that does not report it). */
    val notificationsAllowed: Boolean? = null,
    /** Whether the watch may set exact alarms (null = not reported). */
    val exactAlarms: Boolean? = null,
    /** Alerts armed on the watch right now. */
    val alertsArmed: Int? = null,
    /** Sounds the phone sent to the watch (full Adhans, imported sounds), by id. */
    val sounds: Set<String> = emptySet(),
    /** The user chose the watch's own settings on the watch, so it does not follow the phone's (null = not reported). */
    val usingWatchSettings: Boolean? = null,
    /** Which Noor face is installed ([WatchFaces] id; null = none, or an older watch app). */
    val faceVariant: String? = null,
    /** Which face the watch was last asked for (from the phone or on the watch). */
    val faceChosen: String? = null,
    /**
     * The face is installed but not in use, and the app can no longer make it the active one (Wear OS lets an app do
     * that once): the user chooses it on the watch (touch and hold the watch face).
     */
    val faceNeedsPick: Boolean? = null,
) {
    fun encode(): String = buildString {
        fun put(k: String, v: Any?) { if (v != null) append(k).append('=').append(v).append('\n') }
        put("v", 1); put("code", appVersionCode); put("name", appVersionName.replace("\n", " ")); put("sdk", sdkInt)
        put("push", facePushSupported); put("faceBundled", faceBundledVersion); put("faceInstalled", faceInstalledVersion)
        put("faceActive", faceActive); put("configAt", configReceivedAtMs); put("updated", updatedAtMs)
        put("notif", notificationsAllowed); put("exact", exactAlarms); put("armed", alertsArmed)
        if (sounds.isNotEmpty()) put("sounds", sounds.sorted().joinToString(","))
        put("own", usingWatchSettings)
        put("faceVar", faceVariant); put("faceChosen", faceChosen); put("facePick", faceNeedsPick)
    }

    companion object {
        const val PATH = "/noor/watch/status"
        const val KEY = "report"
        /** Deep links the phone opens on the watch with RemoteActivityHelper. */
        const val LINK_OPEN = "noor://open"
        const val LINK_ACTIVATE_FACE = "noor://face/activate"

        fun decode(text: String): WatchReport? = runCatching {
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            WatchReport(
                appVersionCode = m.getValue("code").toLong(),
                appVersionName = m["name"] ?: "",
                sdkInt = m["sdk"]?.toIntOrNull() ?: 0,
                facePushSupported = m["push"] == "true",
                faceBundledVersion = m["faceBundled"]?.toLongOrNull() ?: 0L,
                faceInstalledVersion = m["faceInstalled"]?.toLongOrNull(),
                faceActive = m["faceActive"] == "true",
                configReceivedAtMs = m["configAt"]?.toLongOrNull(),
                updatedAtMs = m["updated"]?.toLongOrNull() ?: 0L,
                notificationsAllowed = m["notif"]?.let { it == "true" },
                exactAlarms = m["exact"]?.let { it == "true" },
                alertsArmed = m["armed"]?.toIntOrNull(),
                sounds = m["sounds"]?.split(',')?.filter { com.noor.prayer.alerts.SoundIds.isValid(it) }?.toSet().orEmpty(),
                usingWatchSettings = m["own"]?.let { it == "true" },
                faceVariant = m["faceVar"]?.takeIf { WatchFaces.parse(it) != null },
                faceChosen = m["faceChosen"]?.takeIf { WatchFaces.parse(it) != null },
                faceNeedsPick = m["facePick"]?.let { it == "true" },
            )
        }.getOrNull()
    }
}
