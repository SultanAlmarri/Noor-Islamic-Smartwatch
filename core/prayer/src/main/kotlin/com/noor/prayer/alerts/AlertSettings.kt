package com.noor.prayer.alerts

import com.noor.prayer.Prayer

/** What happens at a prayer time. */
enum class AlertMode {
    /** The whole Adhan recording. */
    ADHAN,
    /** The opening of the Adhan (the first takbir phrases), about 15–30 seconds. */
    ADHAN_SHORT,
    /** One of Noor's own alert sounds (ticks, chime, bell, birds…). */
    SOUND,
    /** A sound file the user imported. */
    CUSTOM,
    /** A standard notification with the system's notification sound; follows Do Not Disturb like any notification. */
    NOTIFICATION,
    /** A notification on the lock screen and in the shade, with no sound and no vibration. */
    SILENT;

    val playsAudio: Boolean get() = this == ADHAN || this == ADHAN_SHORT || this == SOUND || this == CUSTOM
}

/** Which device alerts. The other device stays quiet, so the user never gets the same alert twice. */
enum class AlertDevice {
    PHONE, WATCH, BOTH;

    fun includes(d: AlertDevice): Boolean = this == BOTH || this == d
}

data class PrayerAlert(
    val enabled: Boolean,
    val mode: AlertMode,
    /** Library id ("adhan.makkah_2013", "alt.soft_chime") or custom id ("custom.<uuid>"); null = the default for [mode]. */
    val soundId: String? = null,
    /** Percent of the device's alarm volume. */
    val volumePercent: Int = 80,
    val vibrate: Boolean = true,
    /** Stop after this many seconds (with a short fade). 0 = play to the end. */
    val maxSeconds: Int = 0,
) {
    fun normalized(p: Prayer): PrayerAlert {
        // Sunrise is not a prayer: the Adhan is never called for it.
        val m = if (p == Prayer.SHURUQ && (mode == AlertMode.ADHAN || mode == AlertMode.ADHAN_SHORT)) AlertMode.SOUND else mode
        return copy(mode = m, volumePercent = volumePercent.coerceIn(5, 100), maxSeconds = maxSeconds.coerceIn(0, 600),
            soundId = soundId?.takeIf { SoundIds.isValid(it) })
    }
}

/**
 * The user's Adhan and notification choices. Shared by phone and watch (the phone sends them inside the watch config),
 * so both devices plan exactly the same alerts.
 */
data class AlertSettings(
    /** Master switch. Off until the user turns alerts on (and grants the notification permission). */
    val enabled: Boolean = false,
    val device: AlertDevice = AlertDevice.PHONE,
    val perPrayer: Map<Prayer, PrayerAlert> = DEFAULTS,
    /** Seconds to rise from quiet to the chosen volume. 0 = start at full volume. */
    val gradualSeconds: Int = 10,
    /** When false, the Adhan stays silent while Do Not Disturb is on or the ringer is silent (a silent notification is shown). */
    val playInDnd: Boolean = false,
) {
    fun alert(p: Prayer): PrayerAlert = (perPrayer[p] ?: DEFAULTS.getValue(p)).normalized(p)

    /** True when [p] should produce any alert on [onDevice]. */
    fun alertsOn(p: Prayer, onDevice: AlertDevice): Boolean = enabled && device.includes(onDevice) && alert(p).enabled

    fun with(p: Prayer, a: PrayerAlert) = copy(perPrayer = perPrayer + (p to a.normalized(p)))

    /** Compact single-line form, used in DataStore and inside the watch config. */
    fun encode(): String = buildList {
        add("v=$VERSION"); add("on=${if (enabled) 1 else 0}"); add("dev=${device.name}")
        add("grad=$gradualSeconds"); add("dnd=${if (playInDnd) 1 else 0}")
        Prayer.entries.forEach { p ->
            val a = alert(p)
            add("${p.name}=${if (a.enabled) 1 else 0},${a.mode.name},${a.soundId ?: ""},${a.volumePercent},${if (a.vibrate) 1 else 0},${a.maxSeconds}")
        }
    }.joinToString(";")

    companion object {
        const val VERSION = 1

        val DEFAULTS: Map<Prayer, PrayerAlert> = Prayer.entries.associateWith { p ->
            when (p) {
                Prayer.FAJR -> PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_FAJR_ADHAN)
                Prayer.SHURUQ -> PrayerAlert(false, AlertMode.NOTIFICATION, null, vibrate = false)
                else -> PrayerAlert(true, AlertMode.ADHAN, SoundIds.DEFAULT_ADHAN)
            }
        }

        /** Unknown or damaged fields fall back to defaults field by field; never throws. */
        fun decode(text: String?): AlertSettings {
            if (text.isNullOrBlank()) return AlertSettings()
            val m = text.split(';').mapNotNull { f -> f.indexOf('=').takeIf { it > 0 }?.let { f.substring(0, it) to f.substring(it + 1) } }.toMap()
            val per = Prayer.entries.associateWith { p ->
                val d = DEFAULTS.getValue(p)
                val f = m[p.name]?.split(',') ?: return@associateWith d
                PrayerAlert(
                    enabled = f.getOrNull(0)?.let { it == "1" } ?: d.enabled,
                    mode = f.getOrNull(1)?.let { n -> AlertMode.entries.firstOrNull { it.name == n } } ?: d.mode,
                    soundId = f.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    volumePercent = f.getOrNull(3)?.toIntOrNull() ?: d.volumePercent,
                    vibrate = f.getOrNull(4)?.let { it == "1" } ?: d.vibrate,
                    maxSeconds = f.getOrNull(5)?.toIntOrNull() ?: d.maxSeconds,
                ).normalized(p)
            }
            return AlertSettings(
                enabled = m["on"] == "1",
                device = m["dev"]?.let { n -> AlertDevice.entries.firstOrNull { it.name == n } } ?: AlertDevice.PHONE,
                perPrayer = per,
                gradualSeconds = (m["grad"]?.toIntOrNull() ?: 10).coerceIn(0, 60),
                playInDnd = m["dnd"] == "1",
            )
        }
    }
}

/** Ids of the bundled sounds the defaults refer to, and the id format shared with custom sounds. */
object SoundIds {
    const val DEFAULT_ADHAN = "adhan.makkah_maghrib_2012"
    const val DEFAULT_FAJR_ADHAN = "adhan.malmo_fajr_2012"
    const val DEFAULT_SOUND = "alt.soft_chime"
    const val CUSTOM_PREFIX = "custom."

    private val pattern = Regex("^[a-z0-9_]+\\.[a-z0-9_-]{1,64}$")
    fun isValid(id: String): Boolean = pattern.matches(id)
    fun isCustom(id: String?): Boolean = id != null && id.startsWith(CUSTOM_PREFIX)
}
