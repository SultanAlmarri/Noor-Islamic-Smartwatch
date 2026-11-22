package com.noor.app.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.noor.app.NoorApplication
import com.noor.app.data.AppSettings
import com.noor.app.data.Numerals
import com.noor.app.data.PrayerProvider
import com.noor.app.data.ThemeMode
import com.noor.app.data.TimeFormat
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.timetable.Table
import com.noor.app.ui.timetable.TableMonth
import com.noor.app.ui.timetable.TableSource
import com.noor.app.ui.timetable.TimetableContent
import com.noor.app.ui.timetable.Timetables
import com.noor.app.ui.today.CalendarPicker
import com.noor.app.ui.today.DayCache
import com.noor.app.util.Formatter
import com.noor.prayer.HijriMonth
import com.noor.prayer.Prayer
import com.noor.prayer.Schedule
import com.noor.shared.geo.OfficialTimetables
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

private typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * The month's timetable page in a [RenderHarness], for the three sizes below: the page as it is in the app (the same
 * composable, the Today tab's bar under it), its months worked out by the page's own builder ([Timetables.build]).
 */
private class TimetableScene(val rule: ComposeRule) {
    val h = RenderHarness(rule)
    val tables: OfficialTimetables = (RuntimeEnvironment.getApplication() as NoorApplication).container.timetables
    var start by mutableStateOf<TableMonth?>(null)
    /** The months never arrive: the table's skeleton stays. */
    var loading by mutableStateOf(false)
    private var fresh by mutableIntStateOf(0)
    var shared: Pair<String, String>? = null

    fun settings(): AppSettings = h.settings.copy(theme = h.theme)
    fun formatter() = settings().let { Formatter(h.arabic, it.numerals, it.timeFormat) }
    /** The home screen's day: its own cache of days. */
    fun home(date: LocalDate) = settings().let { s -> PrayerProvider.place(s)!!.let { DayCache(tables).resolved(s, it, PrayerProvider.profile(s, it), date) } }

    fun begin() {
        h.tabRoute = com.noor.app.Routes.PRAYERS
        h.screen = { pad -> Page(pad) }
        h.start()
    }

    /** The page opened afresh (its own remembered month, calendar and scroll), as when it is navigated to. */
    fun open(month: TableMonth? = null) { start = month; fresh++ }

    @Composable private fun Page(pad: PaddingValues) {
        val s = settings()
        val place = PrayerProvider.place(s)!!
        val profile = PrayerProvider.profile(s, place)
        val state = remember(s, h.clock) { DayCache(tables).let { c -> Schedule.state(h.clock, place.zone) { c.get(s, place, profile, it) } } }
        val wait = loading
        val source = remember(s, wait) {
            object : TableSource {
                private val made = HashMap<TableMonth, Table>()
                override val basis: Any = Timetables.basis(place, profile, s, tables.version)
                override fun cached(month: TableMonth): Table? = if (wait) null else made.getOrPut(month) { Timetables.build(month, place, profile, s, tables) }
                override suspend fun load(month: TableMonth): Table = cached(month) ?: awaitCancellation()
            }
        }
        key(fresh) {
            TimetableContent(pad, s, place, profile, PrayerProvider.authority(s, place), h.clock.atZone(place.zone).toLocalDate(),
                state.next.date to state.next.prayer, source, start, onBack = {}, onShare = { subject, text -> shared = subject to text })
        }
    }

    fun shot(name: String, waitFor: String, check: Boolean = true) {
        h.shot("timetable_$name", waitFor)
        if (check) checkLanguage(name)
    }

    fun click(description: String) { rule.onAllNodes(hasContentDescription(description)).onFirst().performClick(); settle() }
    fun clickText(text: String) { rule.onAllNodes(hasText(text)).onFirst().performClick(); settle() }
    fun settle() { rule.mainClock.advanceTimeBy(1500); rule.waitForIdle() }
    /** Scrolls the month's days to their foot (the line that names the place). */
    fun foot(month: String, place: String) { rule.onNodeWithTag("timetable/$month").performScrollToNode(hasText(place, substring = true)); settle() }
    fun described(part: String): List<String> = rule.onAllNodes(hasContentDescription(part, substring = true)).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
    fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The six names stand in their columns, in order, inside the screen, none over another. */
    fun assertHeadsFit() {
        val f = formatter()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val heads = Prayer.entries.map { p -> rule.onAllNodes(hasText(f.prayerName(p)), useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot }
        heads.forEach { assertTrue("a column's name is outside the screen: $it", it.left >= 0f && it.right <= root.right) }
        val inOrder = if (h.arabic) heads.reversed() else heads
        inOrder.zipWithNext().forEach { (a, b) -> assertTrue("column names overlap: $a, $b", a.right <= b.left + 0.5f) }
    }

    // The language check of ScreenshotTest: an English screen shows no Arabic letters, an Arabic one no Latin ones.
    val languageProblems = mutableListOf<String>()
    private val arabicLetters = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val latinLetters = Regex("[A-Za-z]")
    private val arabicDigits = Regex("[٠-٩]+")
    private fun checkLanguage(name: String) {
        val texts = rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }.distinct()
        assertTrue("$name: nothing on the screen", texts.size > 20)
        texts.forEach { t ->
            if (h.arabic) { if (latinLetters.containsMatchIn(t)) languageProblems += "$name (Arabic): \"$t\"" }
            else if (arabicLetters.containsMatchIn(t.replace(arabicDigits, ""))) languageProblems += "$name (English): \"$t\""
        }
    }
    fun assertLanguages() = assertTrue("Text in the wrong language:\n" + languageProblems.joinToString("\n"), languageProblems.isEmpty())
}

/**
 * The month's timetable on a common phone (412 × 915 dp), in a moment of the home screen's renders (Wednesday 4 November
 * 2026, 15:29 in Dubai: Asr under way, Maghrib next): English and Arabic at night, English by day, large text, Ramadan,
 * by Hijri month, the skeleton while a month is worked out, the head folded away, and the date sheet's button. What the
 * rows say (to TalkBack) is compared with the home screen's times. Software renders (build/design), not photos of a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class TimetableDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun timetable() {
        val t = TimetableScene(rule)
        val h = t.h
        t.begin()
        t.shot("en_night", "November 2026")
        t.assertHeadsFit()

        // Today's row says today's times as the home screen has them, and which prayer is next.
        val en = t.formatter()
        val today = LocalDate.of(2026, 11, 4)
        val row = t.described("Today, Wednesday 4 November 2026").single()
        val home = t.home(today)
        assertTrue(home.official)
        for (p in Prayer.entries) {
            val said = "${en.prayerName(p)} ${en.timeText(home.day.minutes[p]!!)}" + if (p == Prayer.MAGHRIB) " (Next prayer)" else ""
            assertTrue("\"$said\" in \"$row\"", said in row)
        }
        assertEquals(1, row.split("(Next prayer)").size - 1)
        // A Friday names its noon prayer Jumu'ah, with the home screen's time.
        val friday = today.plusDays(2)
        val fridayRow = t.described("Friday 6 November 2026").single()
        assertTrue(fridayRow, "Jumu’ah ${en.timeText(t.home(friday).day.minutes[Prayer.DHUHR]!!)}" in fridayRow)
        assertTrue(fridayRow, "Dhuhr" !in fridayRow)
        assertTrue("no way back is offered on this month", !t.shows("This month"))

        // Share: the month as text, with the same times.
        t.click("Share this month")
        val (subject, text) = t.shared!!
        assertEquals("Prayer times · November 2026", subject)
        assertTrue(text, text.lines()[2].startsWith("Dubai · IACAD"))
        java.io.File("build/design/timetable_share_en.txt").writeText(text)
        assertTrue(text, Prayer.entries.all { en.time(home.day.minutes[it]!!).first in text.lines().first { l -> l.startsWith("\u20074  ") } })

        // Month to month and back.
        t.click("Next month")
        assertTrue(t.shows("December 2026")); assertTrue(t.shows("This month"))
        t.shot("en_next_month", "December 2026")
        t.clickText("This month")
        assertTrue(t.shows("November 2026")); assertTrue(!t.shows("This month"))
        t.click("Previous month")
        assertTrue(t.shows("October 2026"))
        t.clickText("This month")

        // By Hijri month: the month that holds today.
        t.clickText("Hijri")
        t.shot("en_hijri", "Jumada al-Ula 1448 AH")
        assertTrue(t.described("Today, Wednesday 4 November 2026").single().contains("Maghrib ${en.timeText(home.day.minutes[Prayer.MAGHRIB]!!)} (Next prayer)"))
        t.assertHeadsFit()

        // A swipe sideways turns the month (and back); a swipe up scrolls the days under the six names, which stay.
        t.open()
        t.settle()
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.85f, height * 0.5f), Offset(width * 0.1f, height * 0.5f), 200) }
        t.settle()
        assertTrue(t.shows("December 2026"))
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.1f, height * 0.5f), Offset(width * 0.85f, height * 0.5f), 200) }
        t.settle()
        assertTrue(t.shows("November 2026"))
        rule.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.75f), Offset(centerX, height * 0.30f), 400) }
        t.settle()
        t.shot("en_scrolled", "November 2026", check = false)
        t.assertHeadsFit()
        // The foot of the month: the place, what the times follow, on what grounds.
        t.foot("2026-11", "Dubai")
        t.shot("en_foot", "Its own published times, for each day.")

        // Arabic.
        h.arabic = true; t.open()
        t.shot("ar_night", "نوفمبر 2026")
        t.assertHeadsFit()
        val ar = t.formatter()
        val arRow = t.described("اليوم، الأربعاء 4 نوفمبر 2026").single()
        for (p in Prayer.entries) assertTrue(arRow, "${ar.prayerName(p)} ${ar.timeText(home.day.minutes[p]!!)}" in arRow)
        t.click("مشاركة هذا الشهر")
        java.io.File("build/design/timetable_share_ar.txt").writeText(t.shared!!.second)
        assertTrue(t.shared!!.second.lines().first().startsWith("أوقات الصلاة"))
        t.foot("2026-11", "دبي")
        t.shot("ar_foot", "أوقاتها المنشورة نفسها")
        t.clickText("هجري")
        t.shot("ar_hijri", "1448")
        // Arabic-Indic numerals and the 24-hour clock.
        h.settings = RenderHarness.DUBAI.copy(numerals = Numerals.ARABIC_INDIC, timeFormat = TimeFormat.H24); t.open()
        t.shot("ar_indic_24h", "نوفمبر ٢٠٢٦")
        t.assertHeadsFit()
        h.settings = RenderHarness.DUBAI

        // By day.
        h.arabic = false; h.theme = ThemeMode.LIGHT; t.open()
        t.shot("en_day", "November 2026")
        h.arabic = true; t.open()
        t.shot("ar_day", "نوفمبر 2026")
        h.arabic = false; h.theme = ThemeMode.DARK

        // Large text (1.3).
        h.fontScale = 1.3f; t.open()
        t.shot("en_large", "November 2026", check = false)
        t.assertHeadsFit()
        h.fontScale = 1f

        // Ramadan: a Gregorian month it begins in, and the month itself by the Hijri calendar; another city (calculated).
        val ramadan = HijriMonth(1448, 9)
        t.open(TableMonth.Gregorian(YearMonth.from(ramadan.date(1))))
        t.shot("en_ramadan_begins", "Ramadan 1448 AH")
        t.open(TableMonth.Hijri(ramadan))
        t.shot("en_ramadan", "Ramadan 1448 AH")
        t.foot("h1448-9", "Dubai")
        t.shot("en_ramadan_foot", "Ramadan: Suhoor ends at Fajr")
        h.settings = RenderHarness.DUBAI.copy(manualCityId = "lon", timeFormat = TimeFormat.H24)
        t.open(TableMonth.Gregorian(YearMonth.of(2027, 3)))
        t.shot("en_london_24h", "March 2027")
        h.settings = RenderHarness.DUBAI

        // While a month is being worked out: the table's skeleton.
        t.loading = true; t.open()
        t.shot("en_skeleton", "November 2026", check = false)
        t.loading = false

        // The way in: the date sheet's button, by either calendar.
        var asked: TableMonth? = null
        var hijri by mutableStateOf(false)
        h.screen = {
            key(hijri, h.arabic) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(NoorTheme.colors.surface)) {
                        CalendarPicker(LocalDate.of(2026, 12, 18), today, 0, Formatter(h.arabic, Numerals.WESTERN, TimeFormat.H12), {},
                            Modifier.padding(horizontal = 12.dp, vertical = 16.dp), startHijri = hijri, onTimetable = { asked = it })
                    }
                }
            }
        }
        h.tabRoute = null
        h.shot("timetable_entry_en", "Month’s timetable")
        t.clickText("Month’s timetable")
        assertEquals(TableMonth.Gregorian(YearMonth.of(2026, 12)), asked)
        t.click("Next")
        t.clickText("Month’s timetable")
        assertEquals(TableMonth.Gregorian(YearMonth.of(2027, 1)), asked)
        hijri = true
        t.settle()
        t.clickText("Month’s timetable")
        assertEquals(TableMonth.Hijri(HijriMonth.of(LocalDate.of(2026, 12, 18))), asked)
        h.arabic = true
        h.shot("timetable_entry_ar", "جدول الشهر")

        t.assertLanguages()
    }
}

/** The month's timetable on a small phone (360 × 640 dp): no sideways scrolling, every time whole; also with large text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class TimetableSmallDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun small() {
        val t = TimetableScene(rule)
        val h = t.h
        t.begin()
        t.shot("small_en", "November 2026"); t.assertHeadsFit()
        t.clickText("Hijri")
        t.shot("small_en_hijri", "Jumada al-Ula 1448 AH"); t.assertHeadsFit()
        h.settings = RenderHarness.DUBAI.copy(timeFormat = TimeFormat.H24); t.open()
        t.shot("small_en_24h", "November 2026"); t.assertHeadsFit()
        h.settings = RenderHarness.DUBAI
        h.arabic = true; t.open()
        t.shot("small_ar", "نوفمبر 2026"); t.assertHeadsFit()
        h.settings = RenderHarness.DUBAI.copy(numerals = Numerals.ARABIC_INDIC, timeFormat = TimeFormat.H24); t.open()
        t.shot("small_ar_indic_24h", "نوفمبر ٢٠٢٦"); t.assertHeadsFit()
        h.settings = RenderHarness.DUBAI
        h.arabic = false; h.fontScale = 1.3f; t.open()
        t.shot("small_en_large", "November 2026", check = false); t.assertHeadsFit()
        h.arabic = true; t.open()
        t.shot("small_ar_large", "نوفمبر 2026", check = false); t.assertHeadsFit()
        h.arabic = false; h.fontScale = 1f; h.theme = ThemeMode.LIGHT; t.open()
        t.shot("small_en_day", "November 2026")
        t.assertLanguages()
    }
}

/** The month's timetable on a wide screen (a foldable's inner screen, 673 × 841 dp): the page keeps its width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w673dp-h841dp-xhdpi")
class TimetableWideDesignTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun wide() {
        val t = TimetableScene(rule)
        t.begin()
        t.shot("wide_en", "November 2026"); t.assertHeadsFit()
        t.h.arabic = true; t.open()
        t.shot("wide_ar", "نوفمبر 2026"); t.assertHeadsFit()
        t.assertLanguages()
    }
}
