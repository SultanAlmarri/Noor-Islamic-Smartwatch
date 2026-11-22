package com.noor.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noor.app.R
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorMotion
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Tabular
import com.noor.prayer.CalculationProfile
import com.noor.prayer.Profiles

/*
 * The accuracy page (0.25, second review): the result first, then everything else a tap away. At the top, the
 * method the user's prayer times are calculated with and, where this project has measured it against the authority's
 * own times, the two figures of that measurement; under it every method as one row (its name and, in a phrase, what
 * kind of evidence there is), each opening to the very statement the page gave before. Until then the page was a
 * dozen cards and lists of 13 sp paragraphs, a documentation dump.
 *
 * What the page claims has not changed by a word: the statements are the profiles' own (`Profiles`, in `core`), and
 * Dubai's is the paragraph the page has always given. The phrase of each row is derived from its statement
 * ([accuracyStatus]), never assigned by hand, and the two figures at the top are the statement's own figures
 * ([MEASURED], each checked against its statement by `AccuracyGroupsTest`). Where no figure was measured, none is shown.
 */

/** Dubai's statement on this page, in full (as since 0.9): IACAD's own figures are in `docs/PRAYER_ACCURACY.md`. */
internal const val IACAD_PARAGRAPH_EN = "Checked against 588 official IACAD times (August 2020 – November 2026): 527 match to the minute (89.6%) and every other one is within 1 minute. No day was adjusted individually to force a match. Testing continues with every new official month."
internal const val IACAD_PARAGRAPH_AR = "قورنت الأوقات بـ588 وقتًا رسميًا من الدائرة (أغسطس 2020 – نوفمبر 2026). تطابق 527 منها بالدقيقة (89.6%)، وكل الباقي بفارق دقيقة واحدة فقط. لم يُعدَّل أي يوم بعينه لفرض التطابق. يستمر الاختبار شهريًا مع كل جدول رسمي جديد."

/**
 * The two statements several methods share today, word for word as the profiles give them: the methods that carry them
 * are "Published parameters" and "Documented in references". Should a profile's statement ever change, it no longer
 * matches and the profile's phrase follows from its new words: `AccuracyGroupsTest` then says so.
 */
internal const val STATEMENT_PUBLISHED = "Calculated with the method's published parameters."
internal const val STATEMENT_DOCUMENTED = "Calculated with the parameters documented for this authority by widely used prayer-time references; not yet checked against its own timetable."

/** What kind of evidence stands behind a method, in a phrase. */
internal enum class AccuracyStatus(val label: Int) {
    /** Measured against the authority's own published times. */
    CHECKED(R.string.accuracy_status_checked),
    /** The authority's own timetable is used where it is published. */
    OFFICIAL(R.string.accuracy_status_official),
    /** A widely kept method, not an authority's timetable. */
    RECOGNISED(R.string.accuracy_status_recognised),
    /** The method's published parameters. */
    PUBLISHED(R.string.accuracy_group_published),
    /** Parameters as documented by secondary references, not yet checked. */
    DOCUMENTED(R.string.accuracy_group_documented),
}

/** The phrase a statement earns, from its own words (in this order: the first that applies). */
internal fun accuracyStatus(p: CalculationProfile): AccuracyStatus {
    val s = p.validationEn
    return when {
        s == STATEMENT_PUBLISHED -> AccuracyStatus.PUBLISHED
        s == STATEMENT_DOCUMENTED -> AccuracyStatus.DOCUMENTED
        s.startsWith("Official") -> AccuracyStatus.OFFICIAL
        '%' in s -> AccuracyStatus.CHECKED
        "recognised method" in s.lowercase() -> AccuracyStatus.RECOGNISED
        "not checked" in s -> AccuracyStatus.DOCUMENTED
        else -> AccuracyStatus.PUBLISHED
    }
}

/** The second figure of a measurement: the largest difference in minutes, or the share within one minute. */
internal sealed interface SecondFigure {
    data class LargestMinutes(val minutes: Int) : SecondFigure
    data class WithinOneMinute(val percent: String) : SecondFigure
}

/** What was measured for a method: the share of times to the minute, a second figure, and against what. */
internal data class Measured(val toTheMinute: String, val second: SecondFigure, val source: Int)

/**
 * The measured figures, as each profile's statement gives them (`AccuracyGroupsTest` checks that every figure here
 * stands in that statement). Only methods measured against the authority's own times; none is estimated.
 */
internal val MEASURED: Map<String, Measured> = mapOf(
    Profiles.DUBAI_IACAD.id to Measured("89.6%", SecondFigure.LargestMinutes(1), R.string.accuracy_source_iacad),
    Profiles.UAE_AWQAF.id to Measured("96.2%", SecondFigure.LargestMinutes(1), R.string.accuracy_source_awqaf),
    Profiles.TURKIYE.id to Measured("64%", SecondFigure.LargestMinutes(2), R.string.accuracy_source_diyanet),
    Profiles.MALAYSIA.id to Measured("58–67%", SecondFigure.WithinOneMinute("94%"), R.string.accuracy_source_jakim),
    Profiles.INDONESIA.id to Measured("93–94%", SecondFigure.LargestMinutes(1), R.string.accuracy_source_kemenag),
    Profiles.KAZAKHSTAN.id to Measured("90–94%", SecondFigure.LargestMinutes(1), R.string.accuracy_source_dumk),
    Profiles.BOSNIA.id to Measured("56%", SecondFigure.LargestMinutes(2), R.string.accuracy_source_bosnia),
)

/** The rows of the page: every method, once, in the order of the method picker. */
internal fun accuracyRows(): List<CalculationProfile> = Profiles.ALL

/** What a row opens to, as the profile gives it (Dubai: the page's own paragraph). Untypeset. */
internal fun accuracyStatement(p: CalculationProfile, ar: Boolean): String = when {
    p.id == Profiles.DUBAI_IACAD.id -> if (ar) IACAD_PARAGRAPH_AR else IACAD_PARAGRAPH_EN
    else -> if (ar) p.validationAr else p.validationEn
}

/** The one title style of the page: 17 sp SemiBold (gold only for the method in use). */
private val TitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

/** Plain statement of how accurate each profile is, with the evidence behind it. */
@Composable
fun AccuracyScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val ar = isArabicUi()
    val c = NoorTheme.colors
    // (The user's digits for the figures below.)
    val st = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>().settings.collectAsStateWithLifecycle().value
    val f = com.noor.app.util.Formatter(ar, st?.numerals ?: com.noor.app.data.Numerals.WESTERN, st?.timeFormat ?: com.noor.app.data.TimeFormat.H12)
    // The method the user's times are calculated with: the one chosen, or the authority of the place.
    val current = remember(st) {
        st?.let { s -> com.noor.app.data.PrayerProvider.place(s)?.let { com.noor.app.data.PrayerProvider.profile(s, it) } ?: s.profileId?.let { Profiles.byId(it) } }
    }
    // How a profile's words are set on this page, never what they say: the user's digits; in Arabic the Arabic per
    // cent sign, and each angle kept whole and left to right ("18°", as the Qibla page and the coordinates write
    // degrees: after Arabic letters the sign otherwise jumps to the other side of its number); in English the
    // typographic apostrophe.
    fun typeset(text: String) = f.digits(text).let {
        if (ar) it.replace('%', '٪').replace(Regex("[0-9٠-٩]+(?:[.٫][0-9٠-٩]+)?°")) { m -> "‎" + m.value + "‎" } else it.replace('\'', '’')
    }
    fun named(p: CalculationProfile) = typeset(if (ar) p.nameAr else p.nameEn)
    var open by rememberSaveable { mutableStateOf(emptyList<String>()) }
    com.noor.app.ui.components.NoorPage(stringResource(R.string.about_accuracy), contentPadding, onBack = onBack) {
        // The result first: the method in use (Dubai's until a place is known), and what was measured for it.
        val top = current ?: Profiles.DUBAI_IACAD
        GlassCard(Modifier.fillMaxWidth(), highlighted = true) {
            Text(named(top), style = TitleStyle, color = if (current != null) c.goldText else c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(if (current != null) stringResource(R.string.accuracy_yours) else stringResource(accuracyStatus(top).label),
                style = FootnoteStyle, color = c.textSecondary)
            val m = MEASURED[top.id]
            if (m != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Figure(typeset(m.toTheMinute), stringResource(R.string.accuracy_to_the_minute), Modifier.weight(1f))
                    when (val s = m.second) {
                        is SecondFigure.LargestMinutes -> Figure(stringResource(R.string.accuracy_minutes, f.number(s.minutes)), stringResource(R.string.accuracy_largest_difference), Modifier.weight(1f))
                        is SecondFigure.WithinOneMinute -> Figure(typeset(s.percent), stringResource(R.string.accuracy_within_one_minute), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(typeset(stringResource(m.source)), style = FootnoteStyle, color = c.textMuted)
            } else {
                // No figure where nothing was measured: what there is, in the profile's own words.
                Spacer(Modifier.height(8.dp))
                Text(typeset(accuracyStatement(top, ar)), style = FootnoteStyle, color = c.textSecondary)
            }
        }
        // Then every method, each a row that opens to its statement.
        SettingsSection(stringResource(R.string.accuracy_group_own)) {
            accuracyRows().forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = c.separator)
                val isOpen = p.id in open
                AccuracyRow(named(p), stringResource(accuracyStatus(p).label), typeset(accuracyStatement(p, ar)), current?.id == p.id, isOpen) {
                    open = if (isOpen) open - p.id else open + p.id
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

/** A large figure with its caption under it. */
@Composable
private fun Figure(value: String, caption: String, modifier: Modifier) {
    val c = NoorTheme.colors
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.displaySmall.merge(Tabular).copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
            color = c.textPrimary, maxLines = 1)
        Text(caption, style = FootnoteStyle, color = c.textSecondary)
    }
}

/** One method: its name and its phrase; a tap opens the statement under them (and the chevron turns down). */
@Composable
private fun AccuracyRow(name: String, status: String, statement: String, inUse: Boolean, isOpen: Boolean, onToggle: () -> Unit) {
    val c = NoorTheme.colors
    val turn by animateFloatAsState(if (isOpen) 180f else 0f, tween(NoorMotion.Standard), label = "rowChevron")
    Column(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(if (isOpen) R.string.accuracy_hide else R.string.accuracy_show), onClick = onToggle)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = TitleStyle, color = if (inUse) c.goldText else c.textPrimary)
                Text(status, style = FootnoteStyle, color = c.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textMuted, modifier = Modifier.size(22.dp).rotate(turn))
        }
        AnimatedVisibility(isOpen,
            enter = fadeIn(tween(NoorMotion.Standard)) + expandVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
            exit = fadeOut(tween(NoorMotion.Standard)) + shrinkVertically(tween(NoorMotion.Standard, easing = NoorMotion.Ease)),
        ) {
            Text(statement, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
