package com.noor.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.CalculationProfile
import com.noor.prayer.Profiles

/**
 * How the methods of [Profiles.ALL] after the first two are shown (0.25): those whose statement is theirs alone, each
 * with it; and those that share a statement word for word, as lists of names with the statement once under each list.
 * Nothing is classified here by hand: the groups come from the profiles' own statements, so a method added or checked
 * later lands where its statement puts it, and every method is shown exactly once.
 */
internal data class AccuracyGroups(val own: List<CalculationProfile>, val shared: List<Pair<String, List<CalculationProfile>>>)

internal fun accuracyGroups(profiles: List<CalculationProfile> = Profiles.ALL.drop(2)): AccuracyGroups {
    val byStatement = profiles.groupBy { it.validationEn }
    return AccuracyGroups(
        own = profiles.filter { byStatement.getValue(it.validationEn).size == 1 },
        shared = byStatement.filter { it.value.size > 1 }.map { it.key to it.value },
    )
}

/**
 * The two statements several methods share today, word for word as the profiles give them. Under a list of names each
 * is worded for several methods (`accuracy_note_published`, `accuracy_note_documented`: the same claim, in the plural).
 * Should a profile's statement ever change, it no longer matches and is shown as the profile gives it:
 * `AccuracyGroupsTest` then says so.
 */
internal const val STATEMENT_PUBLISHED = "Calculated with the method's published parameters."
internal const val STATEMENT_DOCUMENTED = "Calculated with the parameters documented for this authority by widely used prayer-time references; not yet checked against its own timetable."

/** Plain statement of how accurate each profile is, with the evidence behind it. */
@Composable
fun AccuracyScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val ar = isArabicUi()
    val c = NoorTheme.colors
    // (The user's digits for the figures below.)
    val st = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>().settings.collectAsStateWithLifecycle().value
    val f = com.noor.app.util.Formatter(ar, st?.numerals ?: com.noor.app.data.Numerals.WESTERN, st?.timeFormat ?: com.noor.app.data.TimeFormat.H12)
    // How a profile's words are set on this page, never what they say: the user's digits; in Arabic the Arabic per
    // cent sign, and each angle kept whole and left to right ("18°", as the Qibla page and the coordinates write
    // degrees: after Arabic letters the sign otherwise jumps to the other side of its number); in English the
    // typographic apostrophe.
    fun typeset(text: String) = f.digits(text).let {
        if (ar) it.replace('%', '٪').replace(Regex("[0-9٠-٩]+(?:[.٫][0-9٠-٩]+)?°")) { m -> "‎" + m.value + "‎" } else it.replace('\'', '’')
    }
    fun named(p: CalculationProfile) = typeset(if (ar) p.nameAr else p.nameEn)
    fun said(p: CalculationProfile) = typeset(if (ar) p.validationAr else p.validationEn)
    com.noor.app.ui.components.NoorPage(stringResource(R.string.about_accuracy), contentPadding, onBack = onBack) {
        // The two authorities of the Emirates, each with what it was checked against: the first in full.
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
                Text(if (ar) Profiles.DUBAI_IACAD.nameAr else Profiles.DUBAI_IACAD.nameEn, style = MaterialTheme.typography.titleMedium, color = c.goldText)
                Spacer(Modifier.height(8.dp))
                Text(
                    // (0.18: in the digits the user chose.)
                    f.digits(if (ar) "قورنت الأوقات بـ588 وقتًا رسميًا من الدائرة (أغسطس 2020 – نوفمبر 2026). تطابق 527 منها بالدقيقة (89.6%)، وكل الباقي بفارق دقيقة واحدة فقط. لم يُعدَّل أي يوم بعينه لفرض التطابق. يستمر الاختبار شهريًا مع كل جدول رسمي جديد."
                    else "Checked against 588 official IACAD times (August 2020 – November 2026): 527 match to the minute (89.6%) and every other one is within 1 minute. No day was adjusted individually to force a match. Testing continues with every new official month.").let { if (ar) it.replace('%', '٪') else it },
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            Profiles.ALL[1].let { p ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(named(p), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(said(p), style = FootnoteStyle, color = c.textSecondary)
                }
            }
        }
        // 0.25: the other methods in grouped lists, where there were two dozen cards, a dozen of them with the same
        // sentence. What each method claims has not changed by a word.
        val groups = accuracyGroups()
        if (groups.own.isNotEmpty()) SettingsSection(stringResource(R.string.accuracy_group_own)) {
            groups.own.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(named(p), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(said(p), style = FootnoteStyle, color = c.textSecondary)
                }
            }
        }
        for ((statement, members) in groups.shared) {
            // The statement once, under the names it is true of; the two that the profiles ship with have a heading
            // and a wording for several methods, any other is given as the profiles give it.
            val (title, note) = when (statement) {
                STATEMENT_PUBLISHED -> stringResource(R.string.accuracy_group_published) to stringResource(R.string.accuracy_note_published)
                STATEMENT_DOCUMENTED -> stringResource(R.string.accuracy_group_documented) to stringResource(R.string.accuracy_note_documented)
                else -> "" to said(members.first())
            }
            SettingsSection(title, footer = note) {
                members.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
                    Text(named(p), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
        Column {
            SectionHeader(stringResource(R.string.sources_title))
            GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, padding = 16.dp) {
                Text(stringResource(R.string.sources_body), style = FootnoteStyle, color = c.textSecondary)
            }
        }
    }
}
