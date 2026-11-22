package com.noor.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * (0.25, second review) Every number a person reads follows the digits chosen in Minhad (Settings → Language and
 * region → Numerals), so no sentence may carry an Arabic-Indic digit written into it: with Western numerals, the
 * default, "١٠ ثوانٍ" stood among Western digits on the same page (the reviewer found "لمدة ٣٠ دقيقة"). Numbers are
 * passed as arguments and formatted with `Formatter.number`. The only exceptions are the samples that show what the
 * Arabic-Indic choice looks like, and search keywords (never shown).
 */
class NumeralsInStringsTest {
    private val samples = setOf("numerals_arabic", "w_digits_indic", "studio_digits_arabic", "kw_time")

    @Test fun noArabicStringCarriesAnArabicIndicDigit() {
        val res = File("src/main/res")
        val files = res.listFiles()!!.filter { it.name.startsWith("values-ar") }.flatMap { d -> d.listFiles()!!.filter { it.name.endsWith(".xml") } }
        assertTrue("no Arabic resources found", files.isNotEmpty())
        val entry = Regex("<(string|item)[^>]*?(?:name=\"([^\"]+)\")?[^>]*>(.*?)</(string|item)>", RegexOption.DOT_MATCHES_ALL)
        val indic = Regex("[٠-٩۰-۹]")
        val problems = mutableListOf<String>()
        for (f in files) {
            var plural: String? = null
            for (line in f.readLines()) {
                Regex("<plurals name=\"([^\"]+)\"").find(line)?.let { plural = it.groupValues[1] }
                if ("</plurals>" in line) plural = null
                for (m in entry.findAll(line)) {
                    val name = m.groupValues[2].ifEmpty { plural ?: "?" }
                    if (name in samples) continue
                    if (indic.containsMatchIn(m.groupValues[3])) problems += "${f.parentFile.name}/${f.name}: $name = ${m.groupValues[3].take(80)}"
                }
            }
        }
        assertTrue("Arabic-Indic digits written into strings (pass the number and format it with Formatter.number):\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
