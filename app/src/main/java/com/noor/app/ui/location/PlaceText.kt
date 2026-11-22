package com.noor.app.ui.location

import com.noor.prayer.geo.WorldPlaces
import java.text.Normalizer

/**
 * How the offline list's place names are shown (0.25, second review). Display only: the list, its search and what is
 * saved are untouched.
 *
 * GeoNames writes the places of the Arab world, Iran and Afghanistan in a scholarly romanisation of the Arabic script
 * (BGN/PCGN): "Abū Hayl", "Aḑ Ḑaghāyah", "Al ‘Usbij", "Ra’s al Khaymah", with marks no road sign and no English
 * speaker uses, next to names written plainly ("Al Barsha"). In English those names are shown with plain letters:
 * "Abu Hayl", "Ad Daghayah", "Al Usbij", "Ras al Khaymah". Elsewhere a name's marks are its own language's letters
 * (São Paulo, Zürich, İstanbul, Kraków) and the ’ is a soft sign or a syllable mark (Mar’yanovka, Yong’an): those are
 * kept as they are.
 */
internal object PlaceText {
    /** Where GeoNames' names are romanised from the Arabic script: the Arab League's members, Iran and Afghanistan. */
    private val ROMANISED = setOf(
        "AE", "SA", "QA", "BH", "KW", "OM", "YE", "IQ", "SY", "JO", "LB", "PS", "IL", "EG", "LY", "TN", "DZ", "MA", "MR", "SD",
        "SO", "DJ", "KM", "IR", "AF",
    )

    /** The marks of the romanisation that stand for letters English does not write: ‘ (ayn) and ’ (hamza), and kin. */
    private val TRANSLITERATION_MARKS = charArrayOf('‘', '’', 'ʿ', 'ʾ', 'ʼ', 'ʻ', '`', '´')

    private val COMBINING = Regex("\\p{M}+")

    /** [name] as it is shown in English: in plain letters where it is a romanisation of Arabic script, else as it is. */
    fun en(name: String, countryCode: String?): String {
        if (countryCode?.uppercase() !in ROMANISED || name.all { it.code < 128 }) return name
        val plain = COMBINING.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "")
            .filterNot { it in TRANSLITERATION_MARKS }
            .replace(Regex(" {2,}"), " ").trim()
        return Normalizer.normalize(plain, Normalizer.Form.NFC).ifBlank { name }
    }

    /** A place's name as shown: its Arabic name on the Arabic page (its Latin one where it has none), else [en]. */
    fun of(p: WorldPlaces.Place, ar: Boolean): String = if (ar) p.nameAr ?: en(p.name, p.countryCode) else en(p.name, p.countryCode)

    /** A region's name as shown. */
    fun of(r: WorldPlaces.Region, ar: Boolean): String = if (ar) r.nameAr ?: en(r.name, r.countryCode) else en(r.name, r.countryCode)

    /**
     * What a typed filter is compared on: [WorldPlaces.fold] (case and accents), and without apostrophes, so a name
     * matches both as the list writes it ("Al ‘Usbij") and as it is shown ("Al Usbij").
     */
    fun key(s: String): String = WorldPlaces.fold(s).replace("'", "").replace(Regex(" {2,}"), " ")

    /** True if [p] matches the typed [filter] in any of its forms (as written, as shown, in Arabic). */
    fun matches(p: WorldPlaces.Place, filter: String): Boolean {
        val f = key(filter.trim())
        if (f.isEmpty()) return true
        return key(p.name).contains(f) || key(en(p.name, p.countryCode)).contains(f) || p.nameAr?.let { key(it).contains(f) } == true
    }

    /**
     * The offline search, also for a name typed as it is shown: [WorldPlaces.search] compares the list's own spelling,
     * in which "Al Usbij" is "Al ‘Usbij" (one word more for the search: the apostrophe sits inside the word). When a
     * query of several words finds little, its longest word is searched for as well and the places whose name, in any
     * of its forms, contains the whole query are added after the first results.
     */
    fun search(w: WorldPlaces, query: String, limit: Int, countryCode: String? = null): List<WorldPlaces.Place> {
        val first = w.search(query, limit, countryCode)
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2 || first.size >= limit) return first
        val longest = words.maxBy { it.length }.takeIf { it.length >= 3 } ?: return first
        val seen = first.mapTo(HashSet()) { it.row }
        val more = w.search(longest, 400, countryCode).filter { it.row !in seen && matches(it, query) }
        return (first + more).take(limit)
    }
}
