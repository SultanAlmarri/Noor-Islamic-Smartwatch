package com.noor.prayer

import com.noor.prayer.Prayer.DHUHR
import com.noor.prayer.Prayer.ISHA
import com.noor.prayer.Prayer.MAGHRIB
import com.noor.prayer.Prayer.SHURUQ
import com.noor.prayer.authority.Authorities
import com.noor.prayer.authority.SourceKind
import com.noor.prayer.geo.TimeZoneMap
import com.noor.prayer.geo.ZoneCountries
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 0.18: every country of the world resolves to an authority, the position alone (offline) gives the country, and each
 * new profile computes sane times. Agreement with published timetables is measured and stated, never tuned
 * (docs/AUTHORITIES.md, docs/PRAYER_ACCURACY.md).
 */
class WorldAuthoritiesTest {
    private val tz by lazy { File("../../app/src/main/assets/tzmap.bin").inputStream().use { TimeZoneMap.read(it) } }

    private val allCountries: Set<String> = Locale.getISOCountries().toSet() + "XK"

    // ---- the mapping ----

    @Test fun everyCountryResolvesToAProfileTheAppShips() {
        for (cc in allCountries) {
            val a = Authorities.forPlace(cc, null)
            assertTrue(a.profile in Profiles.ALL, "$cc → ${a.id}: profile ${a.profile.id} not shipped")
            assertSame(a, Authorities.forPlace(cc.lowercase(), null), cc)
        }
        for (cc in Authorities.mappedCountries) assertTrue(cc in allCountries, "$cc is not an ISO 3166-1 code")
        assertTrue(Authorities.ALL.all { it.sourceUrl.startsWith("https://") && it.nameEn.isNotBlank() && it.nameAr.isNotBlank() })
        assertEquals(Authorities.ALL.size, Authorities.ALL.map { it.id }.toSet().size, "authority ids are unique")
        assertEquals(Profiles.ALL.size, Profiles.ALL.map { it.id }.toSet().size, "profile ids are unique")
        assertSame(Authorities.MWL, Authorities.forPlace(null, null))
    }

    @Test fun nationalAuthoritiesAndRecognisedMethods() {
        fun check(cc: String, a: com.noor.prayer.authority.Authority) = assertSame(a, Authorities.forPlace(cc, null), cc)
        // unchanged
        assertSame(Authorities.IACAD, Authorities.forPlace("AE", Authorities.DUBAI_REGION))
        check("AE", Authorities.AWQAF); check("SA", Authorities.UMM_AL_QURA); check("SG", Authorities.MUIS); check("MY", Authorities.JAKIM)
        check("ID", Authorities.KEMENAG); check("EG", Authorities.EGYPT); check("KW", Authorities.KUWAIT); check("QA", Authorities.QATAR)
        check("TR", Authorities.DIYANET); check("IR", Authorities.TEHRAN); check("PK", Authorities.KARACHI); check("US", Authorities.ISNA)
        // 0.18
        check("MA", Authorities.MOROCCO); check("EH", Authorities.MOROCCO); check("DZ", Authorities.ALGERIA); check("TN", Authorities.TUNISIA)
        check("JO", Authorities.JORDAN); check("PT", Authorities.PORTUGAL); check("RU", Authorities.RUSSIA); check("BN", Authorities.BRUNEI)
        check("GB", Authorities.MOONSIGHTING); check("FR", Authorities.UOIF)
        check("NG", Authorities.EGYPT_REGION); check("SY", Authorities.EGYPT_REGION); check("SN", Authorities.EGYPT_REGION)
        check("OM", Authorities.UMM_AL_QURA_REGION); check("BH", Authorities.UMM_AL_QURA_REGION); check("YE", Authorities.UMM_AL_QURA_REGION)
        check("DE", Authorities.MWL); check("UZ", Authorities.MWL); check("JP", Authorities.MWL)
        // round 2: conventions inferred from the authorities' own tables
        check("KZ", Authorities.DUMK); check("BA", Authorities.IZ_BIH)
        // South Asia: the Karachi angles with the Hanafi Asr by default; the user's own choice of Asr still wins.
        for (cc in listOf("PK", "IN", "BD", "AF")) assertEquals(AsrSchool.HANAFI, Authorities.forPlace(cc, null).profile.asrSchool, cc)
        // Rule 9: Israel is shown as Palestine; both codes give the same times.
        assertSame(Authorities.forPlace("PS", null), Authorities.forPlace("IL", null))
        // A region of the same profile is labelled as a recognised method, the country's own authority is not.
        assertEquals(SourceKind.RECOGNISED_METHOD, Authorities.forPlace("NG", null).kind)
        assertEquals(SourceKind.AUTHORITY_PARAMETERS, Authorities.forPlace("EG", null).kind)
    }

    @Test fun aMethodChosenByHandShowsItsOwnAuthority() {
        for (p in Profiles.ALL) Authorities.forProfile(p.id)?.let { assertEquals(p.id, it.profile.id) }
        assertSame(Authorities.EGYPT, Authorities.forProfile(Profiles.EGYPT.id))
        assertSame(Authorities.UMM_AL_QURA, Authorities.forProfile(Profiles.UMM_AL_QURA.id))
        assertSame(Authorities.KARACHI_STANDARD, Authorities.forProfile(Profiles.KARACHI.id))
        assertSame(Authorities.KARACHI, Authorities.forProfile(Profiles.KARACHI_HANAFI.id))
        for (p in listOf(Profiles.MOONSIGHTING, Profiles.JAFARI, Profiles.MOROCCO, Profiles.ALGERIA, Profiles.TUNISIA, Profiles.JORDAN,
            Profiles.RUSSIA, Profiles.FRANCE_UOIF, Profiles.PORTUGAL, Profiles.BRUNEI)) assertNotNull(Authorities.forProfile(p.id), p.id)
    }

    // ---- the position gives the country, offline ----

    @Test fun everyZoneOfTheOfflineMapHasACountry() {
        for (z in tz.zones) assertNotNull(ZoneCountries.of(z), z)
        assertEquals("AE", ZoneCountries.of("Asia/Dubai")); assertEquals("PS", ZoneCountries.of("Asia/Hebron"))
        assertEquals("PS", ZoneCountries.of("Asia/Gaza")); assertEquals("HR", ZoneCountries.of("Europe/Zagreb"))
        assertEquals("MA", ZoneCountries.of("Africa/Casablanca")); assertEquals("EH", ZoneCountries.of("Africa/El_Aaiun"))
        assertNull(ZoneCountries.of("Etc/GMT+3")); assertNull(ZoneCountries.of(null))
    }

    @Test fun anyPositionFindsItsAuthorityWithoutNetwork() {
        fun at(lat: Double, lon: Double) = Authorities.forPlace(ZoneCountries.of(tz.zoneAt(lat, lon)), null)
        assertSame(Authorities.MOROCCO, at(34.0209, -6.8416))        // Rabat
        assertSame(Authorities.ALGERIA, at(26.0, 3.0))                // the Algerian Sahara, far from any town
        assertSame(Authorities.UMM_AL_QURA, at(20.0, 45.0))           // the Empty Quarter
        assertSame(Authorities.RUSSIA, at(55.7963, 49.1088))          // Kazan
        assertSame(Authorities.MOONSIGHTING, at(53.4808, -2.2426))    // Manchester
        assertSame(Authorities.UOIF, at(43.2965, 5.3698))             // Marseille
        assertSame(Authorities.BRUNEI, at(4.9031, 114.9398))          // Bandar Seri Begawan
        assertSame(Authorities.JORDAN, at(31.9454, 35.9284))          // Amman
        assertSame(Authorities.EGYPT_REGION, at(9.0765, 7.3986))      // Abuja
        assertSame(Authorities.DIYANET, at(39.9334, 32.8597))         // Ankara
        assertSame(Authorities.MWL, at(31.5326, 35.0998))             // Hebron (PS)
        assertSame(Authorities.AWQAF, at(24.4539, 54.3773))           // Abu Dhabi
        assertSame(Authorities.MWL, at(30.0, -40.0))                  // the open Atlantic: no country
    }

    // ---- each new profile: sane times, and its rules ----

    private fun day(p: CalculationProfile, lat: Double, lon: Double, zone: String, date: LocalDate, user: UserAdjustments = UserAdjustments(), ramadan: Boolean = false) =
        PrayerEngine.compute(date, GeoPoint(lat, lon), ZoneId.of(zone), p, user, ramadan)

    private fun assertSane(d: PrayerDay) {
        val m = Prayer.entries.map { assertNotNull(d.minutes[it], "${d.profileId} ${d.date} $it") }
        for (i in 1 until m.size) assertTrue(m[i] > m[i - 1], "${d.profileId} ${d.date}: ${Prayer.entries[i]} not after ${Prayer.entries[i - 1]}: $m")
        assertTrue(m.first() > 0 && m.last() < 24 * 60, "${d.profileId}: $m")
    }

    @Test fun newProfilesGiveSaneTimesInTheirReferenceCities() {
        val cases = listOf(
            Triple(Profiles.MOROCCO, Triple(34.0209, -6.8416, "Africa/Casablanca"), "Rabat"),
            Triple(Profiles.ALGERIA, Triple(36.7538, 3.0588, "Africa/Algiers"), "Algiers"),
            Triple(Profiles.TUNISIA, Triple(36.8065, 10.1815, "Africa/Tunis"), "Tunis"),
            Triple(Profiles.JORDAN, Triple(31.9454, 35.9284, "Asia/Amman"), "Amman"),
            Triple(Profiles.PORTUGAL, Triple(38.7223, -9.1393, "Europe/Lisbon"), "Lisbon"),
            Triple(Profiles.RUSSIA, Triple(54.7348, 55.9579, "Asia/Yekaterinburg"), "Ufa"),
            Triple(Profiles.BRUNEI, Triple(4.9031, 114.9398, "Asia/Brunei"), "Bandar Seri Begawan"),
            Triple(Profiles.MOONSIGHTING, Triple(51.5072, -0.1276, "Europe/London"), "London"),
            Triple(Profiles.FRANCE_UOIF, Triple(48.8566, 2.3522, "Europe/Paris"), "Paris"),
            Triple(Profiles.JAFARI, Triple(34.6416, 50.8746, "Asia/Tehran"), "Qum"),
        )
        for ((p, at, _) in cases) for (date in listOf(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21)))
            assertSane(day(p, at.first, at.second, at.third, date))
    }

    @Test fun eachNewProfileFollowsItsOwnRules() {
        val date = LocalDate.of(2026, 11, 16)
        fun mwl(lat: Double, lon: Double, zone: String) = day(Profiles.MWL, lat, lon, zone, date)
        // Jordan: Maghrib 5 minutes after sunset (MWL's Maghrib is sunset itself), Isha at 18 degrees.
        val amman = day(Profiles.JORDAN, 31.9454, 35.9284, "Asia/Amman", date)
        assertEquals(mwl(31.9454, 35.9284, "Asia/Amman").minutes[MAGHRIB]!! + 5, amman.minutes[MAGHRIB])
        // Portugal: Isha 77 minutes after its Maghrib (which is 3 minutes after sunset).
        val lisbon = day(Profiles.PORTUGAL, 38.7223, -9.1393, "Europe/Lisbon", date)
        assertTrue(abs(lisbon.minutes[ISHA]!! - lisbon.minutes[MAGHRIB]!! - 77) <= 1, "${lisbon.minutes}")
        // Morocco: Shuruq -3, Dhuhr +5, Maghrib +5 around the unrounded events.
        val rabat = day(Profiles.MOROCCO, 34.0209, -6.8416, "Africa/Casablanca", date)
        for ((p, off) in listOf(SHURUQ to -3, DHUHR to 5, MAGHRIB to 5))
            assertTrue(abs(rabat.minutes[p]!! - off - rabat.seconds[p]!! / 60.0) <= 0.5 + 1e-9, "$p ${rabat.minutes}")
        // Jafari: Maghrib when the Sun is 4 degrees down, some 15-20 minutes after sunset.
        val qum = day(Profiles.JAFARI, 34.6416, 50.8746, "Asia/Tehran", date)
        assertTrue((qum.minutes[MAGHRIB]!! - mwl(34.6416, 50.8746, "Asia/Tehran").minutes[MAGHRIB]!!) in 14..22)
        // Umm al-Qura's Isha: 90 minutes after Maghrib, 120 in Ramadan (applies in the region as in Saudi Arabia).
        val muscat = day(Profiles.UMM_AL_QURA, 23.588, 58.3829, "Asia/Muscat", date)
        val muscatR = day(Profiles.UMM_AL_QURA, 23.588, 58.3829, "Asia/Muscat", date, ramadan = true)
        assertEquals(90, muscat.minutes[ISHA]!! - muscat.minutes[MAGHRIB]!!)
        assertEquals(120, muscatR.minutes[ISHA]!! - muscatR.minutes[MAGHRIB]!!)
    }

    @Test fun southAsiaKeepsTheHanafiAsrUnlessTheUserChoosesOtherwise() {
        val date = LocalDate.of(2026, 11, 16)
        val profile = Authorities.forPlace("PK", null).profile
        val hanafi = day(profile, 24.8607, 67.0011, "Asia/Karachi", date)
        val standard = day(profile, 24.8607, 67.0011, "Asia/Karachi", date, UserAdjustments(asrSchool = AsrSchool.STANDARD))
        val asr = Prayer.ASR
        assertTrue(hanafi.minutes[asr]!! - standard.minutes[asr]!! in 40..70, "${hanafi.minutes[asr]} vs ${standard.minutes[asr]}")
        assertEquals(standard.minutes, day(Profiles.KARACHI, 24.8607, 67.0011, "Asia/Karachi", date).minutes)
        for (p in Prayer.entries) if (p != asr) assertEquals(standard.minutes[p], hanafi.minutes[p], "$p")
    }

    @Test fun kazakhstanTemkinGrowsInTheNorth() {
        val date = LocalDate.of(2026, 3, 1)
        val south = day(Profiles.KAZAKHSTAN, 43.238293, 76.945465, "+05:00", date)
        val north = day(Profiles.KAZAKHSTAN, 51.133333, 71.433333, "+05:00", date)
        assertEquals(3, south.minutes[DHUHR]!! - kotlin.math.round(south.seconds[DHUHR]!! / 60).toInt())
        assertEquals(5, north.minutes[DHUHR]!! - kotlin.math.round(north.seconds[DHUHR]!! / 60).toInt())
        assertEquals(Profiles.KAZAKHSTAN.offset(SHURUQ, 48.69), -3); assertEquals(Profiles.KAZAKHSTAN.offset(SHURUQ, 48.7), -5)
    }

    // ---- Moonsighting Committee: the seasonal rule ----

    @Test fun moonsightingSeasonalLimitsAsPublished() {
        // dyy (days since the winter solstice), as in Adhan: 1 January = 11 in the north, 1 July = 10 in the south.
        assertEquals(11, MoonsightingSeason.daysSinceSolstice(LocalDate.of(2026, 1, 1), 51.0))
        assertEquals(10, MoonsightingSeason.daysSinceSolstice(LocalDate.of(2026, 7, 1), -33.0))
        // On the solstice-ish day 0 at the equator, both are the base 75 minutes.
        assertEquals(75 * 60.0, MoonsightingSeason.secondsBeforeSunrise(0.0, LocalDate.of(2026, 12, 21)))
        // London midsummer: Fajr never more than about 1 h 50 before sunrise, Isha not more than about 1 h 21 after sunset.
        val june = LocalDate.of(2026, 6, 21)
        val f = MoonsightingSeason.secondsBeforeSunrise(51.5, june) / 60; val i = MoonsightingSeason.secondsAfterSunset(51.5, june, Shafaq.GENERAL) / 60
        assertTrue(f in 115.0..121.0, "$f"); assertTrue(i in 79.0..82.0, "$i")
    }

    /**
     * Reference times of the Batoul Apps Adhan libraries' own test fixture (Shared/Times/London-MoonsightingCommittee.json,
     * Hanafi Asr; 2016): a cross-check of the implementation, not an official timetable.
     */
    @Test fun moonsightingMatchesTheAdhanReferenceForLondon() {
        val ref = listOf(
            LocalDate.of(2016, 1, 1) to "06:25 08:06 12:09 14:15 16:05 17:38",
            LocalDate.of(2016, 2, 1) to "06:02 07:40 12:19 15:00 16:52 18:18",
            LocalDate.of(2016, 3, 1) to "05:10 06:45 12:18 15:49 17:44 19:03",
            LocalDate.of(2016, 6, 1) to "02:55 04:49 13:03 18:31 21:12 22:23",
            LocalDate.of(2016, 12, 1) to "06:04 07:44 11:55 14:08 15:58 17:29",
        )
        val diffs = compare(ref) { d -> day(Profiles.MOONSIGHTING, 51.507194, -0.116711, "Europe/London", d, UserAdjustments(asrSchool = AsrSchool.HANAFI)) }
        report("Moonsighting London vs Adhan", diffs)
        assertTrue(diffs.all { abs(it) <= 1 }, "$diffs")
        assertTrue(diffs.count { it == 0 } >= 27, "$diffs")
    }

    // ---- published timetables: measured agreement, stated in docs/AUTHORITIES.md ----

    /** Diyanet's published times for Istanbul (namazvakitleri.diyanet.gov.tr, district 9541), 16–22 November 2026. */
    @Test fun diyanetIstanbulAgreementIsAsDocumented() {
        val ref = listOf(
            LocalDate.of(2026, 11, 16) to "06:17 07:45 12:54 15:28 17:52 19:15",
            LocalDate.of(2026, 11, 17) to "06:18 07:47 12:54 15:28 17:51 19:15",
            LocalDate.of(2026, 11, 18) to "06:19 07:48 12:54 15:27 17:51 19:14",
            LocalDate.of(2026, 11, 19) to "06:20 07:49 12:54 15:27 17:50 19:13",
            LocalDate.of(2026, 11, 20) to "06:21 07:50 12:55 15:26 17:49 19:13",
            LocalDate.of(2026, 11, 21) to "06:22 07:51 12:55 15:26 17:49 19:12",
            LocalDate.of(2026, 11, 22) to "06:23 07:52 12:55 15:25 17:48 19:12",
        )
        val diffs = compare(ref) { d -> day(Profiles.TURKIYE, 41.0082, 28.9784, "Europe/Istanbul", d) }
        report("Diyanet Istanbul", diffs)
        // 31 of 42 exact, every other one 1 minute (Istanbul's centre, 41.0082 N 28.9784 E; Diyanet's own point is not published).
        assertEquals(31, diffs.count { it == 0 }); assertTrue(diffs.all { abs(it) <= 1 }, "$diffs")
    }

    /** JAKIM's e-Solat times for zone WLY01 (Kuala Lumpur, Putrajaya), 17–23 November 2026. */
    @Test fun jakimKualaLumpurAgreementIsAsDocumented() {
        val ref = listOf(
            LocalDate.of(2026, 11, 17) to "05:49 06:58 13:01 16:23 18:59 20:12",
            LocalDate.of(2026, 11, 18) to "05:49 06:58 13:01 16:23 18:59 20:12",
            LocalDate.of(2026, 11, 19) to "05:49 06:59 13:01 16:23 18:59 20:12",
            LocalDate.of(2026, 11, 20) to "05:49 06:59 13:02 16:24 18:59 20:12",
            LocalDate.of(2026, 11, 21) to "05:50 06:59 13:02 16:24 19:00 20:13",
            LocalDate.of(2026, 11, 22) to "05:50 07:00 13:02 16:24 19:00 20:13",
            LocalDate.of(2026, 11, 23) to "05:50 07:00 13:02 16:25 19:00 20:13",
        )
        val diffs = compare(ref) { d -> day(Profiles.MALAYSIA, 3.139, 101.6869, "Asia/Kuala_Lumpur", d) }
        report("JAKIM WLY01", diffs)
        // Before round 2 (no precautionary minutes): 5 of 42 exact, up to 3 minutes early. With the minutes inferred from
        // JAKIM's 2026 tables of 4 zones (not from this week alone): 33 of 42 exact, all within 2 minutes.
        assertEquals(33, diffs.count { it == 0 }); assertTrue(diffs.all { abs(it) <= 2 }, "$diffs")
    }

    /** Computed minus published, in minutes, for every event of every day. */
    private fun compare(ref: List<Pair<LocalDate, String>>, calc: (LocalDate) -> PrayerDay): List<Int> = ref.flatMap { (date, times) ->
        val published = times.split(' ').map { val (h, m) = it.split(':').map(String::toInt); h * 60 + m }
        val d = calc(date)
        Prayer.entries.mapIndexed { i, p -> d.minutes[p]!! - published[i] }
    }

    private fun report(name: String, diffs: List<Int>) {
        val byPrayer = Prayer.entries.mapIndexed { i, p -> p.en + " " + diffs.filterIndexed { j, _ -> j % 6 == i }.joinToString(",") }
        println("$name: ${diffs.count { it == 0 }}/${diffs.size} exact, max |diff| ${diffs.maxOf { abs(it) }}; " + byPrayer.joinToString("; "))
    }
}
