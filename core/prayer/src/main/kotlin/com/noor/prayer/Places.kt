package com.noor.prayer

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A bundled place for manual selection and offline region detection. */
data class City(
    val id: String, val nameEn: String, val nameAr: String,
    val point: GeoPoint, val zoneId: String, val countryCode: String, val region: String? = null,
)

object Places {
    private fun c(id: String, en: String, ar: String, lat: Double, lon: Double, elev: Double, zone: String, cc: String, region: String? = null) =
        City(id, en, ar, GeoPoint(lat, lon, elev), zone, cc, region)

    /** Region code for Dubai emirate (IACAD jurisdiction). */
    const val DUBAI = "AE-DU"

    val ALL: List<City> = listOf(
        c("dxb", "Dubai", "دبي", 25.2048, 55.2708, 5.0, "Asia/Dubai", "AE", DUBAI),
        c("dxbd", "Dubai - Deira", "دبي - ديرة", 25.2697, 55.3095, 5.0, "Asia/Dubai", "AE", DUBAI),
        c("dxbc", "Dubai - Downtown", "دبي - وسط المدينة", 25.1972, 55.2744, 5.0, "Asia/Dubai", "AE", DUBAI),
        c("dxbm", "Dubai - Marina", "دبي - المرسى", 25.0805, 55.1403, 5.0, "Asia/Dubai", "AE", DUBAI),
        c("dxbs", "Dubai - Silicon Oasis", "دبي - واحة السيليكون", 25.1185, 55.3895, 20.0, "Asia/Dubai", "AE", DUBAI),
        c("dxbj", "Dubai - Jebel Ali", "دبي - جبل علي", 25.0118, 55.1100, 10.0, "Asia/Dubai", "AE", DUBAI),
        c("hat", "Hatta", "حتا", 24.7969, 56.1180, 380.0, "Asia/Dubai", "AE", DUBAI),
        c("auh", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773, 5.0, "Asia/Dubai", "AE", "AE-AZ"),
        c("ain", "Al Ain", "العين", 24.2075, 55.7447, 290.0, "Asia/Dubai", "AE", "AE-AZ"),
        c("shj", "Sharjah", "الشارقة", 25.3463, 55.4209, 5.0, "Asia/Dubai", "AE", "AE-SH"),
        c("ajm", "Ajman", "عجمان", 25.4052, 55.5136, 5.0, "Asia/Dubai", "AE", "AE-AJ"),
        c("uaq", "Umm Al Quwain", "أم القيوين", 25.5647, 55.5552, 5.0, "Asia/Dubai", "AE", "AE-UQ"),
        c("rak", "Ras Al Khaimah", "رأس الخيمة", 25.8007, 55.9762, 5.0, "Asia/Dubai", "AE", "AE-RK"),
        c("fuj", "Fujairah", "الفجيرة", 25.1288, 56.3265, 5.0, "Asia/Dubai", "AE", "AE-FU"),
        c("ruh", "Riyadh", "الرياض", 24.7136, 46.6753, 612.0, "Asia/Riyadh", "SA"),
        c("jed", "Jeddah", "جدة", 21.4858, 39.1925, 12.0, "Asia/Riyadh", "SA"),
        c("mak", "Makkah", "مكة المكرمة", 21.4225, 39.8262, 277.0, "Asia/Riyadh", "SA"),
        c("med", "Madinah", "المدينة المنورة", 24.4686, 39.6142, 608.0, "Asia/Riyadh", "SA"),
        c("dmm", "Dammam", "الدمام", 26.4207, 50.0888, 10.0, "Asia/Riyadh", "SA"),
        c("doh", "Doha", "الدوحة", 25.2854, 51.5310, 10.0, "Asia/Qatar", "QA"),
        c("kwi", "Kuwait City", "الكويت", 29.3759, 47.9774, 10.0, "Asia/Kuwait", "KW"),
        c("bah", "Manama", "المنامة", 26.2235, 50.5876, 5.0, "Asia/Bahrain", "BH"),
        c("mct", "Muscat", "مسقط", 23.5880, 58.3829, 15.0, "Asia/Muscat", "OM"),
        c("cai", "Cairo", "القاهرة", 30.0444, 31.2357, 23.0, "Africa/Cairo", "EG"),
        c("amm", "Amman", "عمّان", 31.9454, 35.9284, 800.0, "Asia/Amman", "JO"),
        c("ist", "Istanbul", "إسطنبول", 41.0082, 28.9784, 40.0, "Europe/Istanbul", "TR"),
        c("thr", "Tehran", "طهران", 35.6892, 51.3890, 1190.0, "Asia/Tehran", "IR"),
        c("khi", "Karachi", "كراتشي", 24.8607, 67.0011, 10.0, "Asia/Karachi", "PK"),
        c("lhe", "Lahore", "لاهور", 31.5204, 74.3587, 217.0, "Asia/Karachi", "PK"),
        c("del", "New Delhi", "نيودلهي", 28.6139, 77.2090, 216.0, "Asia/Kolkata", "IN"),
        c("bom", "Mumbai", "مومباي", 19.0760, 72.8777, 14.0, "Asia/Kolkata", "IN"),
        c("dac", "Dhaka", "دكا", 23.8103, 90.4125, 4.0, "Asia/Dhaka", "BD"),
        c("kul", "Kuala Lumpur", "كوالالمبور", 3.1390, 101.6869, 60.0, "Asia/Kuala_Lumpur", "MY"),
        c("sin", "Singapore", "سنغافورة", 1.3521, 103.8198, 15.0, "Asia/Singapore", "SG"),
        c("jkt", "Jakarta", "جاكرتا", -6.2088, 106.8456, 8.0, "Asia/Jakarta", "ID"),
        c("lon", "London", "لندن", 51.5074, -0.1278, 11.0, "Europe/London", "GB"),
        c("par", "Paris", "باريس", 48.8566, 2.3522, 35.0, "Europe/Paris", "FR"),
        c("nyc", "New York", "نيويورك", 40.7128, -74.0060, 10.0, "America/New_York", "US"),
        c("tor", "Toronto", "تورونتو", 43.6532, -79.3832, 76.0, "America/Toronto", "CA"),
    )

    fun byId(id: String): City? = ALL.firstOrNull { it.id == id }

    /** Great-circle distance in km. */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    fun nearest(p: GeoPoint): City = ALL.minBy { distanceKm(it.point, p) }

    /**
     * Default authority for a location. Region and country come from the device geocoder when available
     * (accurate); otherwise from the nearest bundled place (offline approximation near borders).
     */
    fun defaultProfileFor(point: GeoPoint, countryCode: String? = null, regionCode: String? = null): CalculationProfile {
        val near = nearest(point)
        val cc = countryCode ?: near.countryCode
        val region = regionCode ?: near.region
        return when {
            cc == "AE" && region == DUBAI -> Profiles.DUBAI_IACAD
            cc == "AE" -> Profiles.UAE_AWQAF
            cc == "SA" -> Profiles.UMM_AL_QURA
            cc == "QA" || cc == "BH" -> Profiles.QATAR
            cc == "KW" -> Profiles.KUWAIT
            cc == "EG" || cc == "SD" || cc == "LY" -> Profiles.EGYPT
            cc in setOf("PK", "IN", "BD", "AF") -> Profiles.KARACHI
            cc == "TR" -> Profiles.TURKIYE
            cc == "IR" -> Profiles.TEHRAN
            cc in setOf("SG", "MY", "ID", "BN") -> Profiles.SINGAPORE
            cc in setOf("US", "CA") -> Profiles.ISNA
            else -> Profiles.MWL
        }
    }
}
