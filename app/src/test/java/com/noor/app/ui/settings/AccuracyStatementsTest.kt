package com.noor.app.ui.settings

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import com.noor.app.NoorApplication
import com.noor.app.R
import com.noor.app.ui.theme.NoorTheme
import com.noor.prayer.Profiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * 0.25, second review: the accuracy page puts the result first and every statement behind a tap. Nothing it said
 * before may be lost: in English and in Arabic, every method has a row, and a tap on it shows the very statement the
 * page gave before (Dubai's paragraph, each profile's own statement; the shared ones as each profile words them). At
 * the top, for a user in Dubai, IACAD's two measured figures and the source they were measured against, whose every
 * number stands in IACAD's statement. One test method: DataStore is a per-process singleton.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class AccuracyStatementsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyStatementIsStillOnThePage() {
        val app = RuntimeEnvironment.getApplication() as NoorApplication
        runBlocking { app.container.settings.setManualCity("dxb"); app.container.settings.setOnboardingDone() }
        var arabic by mutableStateOf(false)
        var fresh by mutableIntStateOf(0)
        rule.setContent {
            val base = LocalContext.current
            val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
            val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
            CompositionLocalProvider(
                LocalContext provides base.createConfigurationContext(config), LocalConfiguration provides config,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NoorTheme(com.noor.app.data.ThemeMode.DARK) { key(fresh) { AccuracyScreen(PaddingValues(), {}) } }
            }
        }
        fun settle() {
            for (i in 0 until 10) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle() }
            rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        }
        // The texts on the page, as drawn, with the marks and signs that only set them (left-to-right marks, the
        // Arabic per cent sign, the typographic apostrophe) taken out, so a statement is compared by its words.
        fun plain(s: String) = s.replace("‎", "").replace('٪', '%').replace('’', '\'')
        fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { plain(it.text) } }

        for (ar in listOf(false, true)) {
            arabic = ar; fresh++; settle()
            val res = RuntimeEnvironment.getApplication().createConfigurationContext(
                Configuration().apply { setLocale(if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH) }).resources
            // The result first: IACAD's figures for a user in Dubai, and against what they were measured.
            val top = texts()
            assertTrue("89.6% at the top ($ar)", top.any { it == "89.6%" })
            val source = res.getString(R.string.accuracy_source_iacad)
            assertTrue("the source at the top ($ar)", top.any { it == plain(source) })
            for (n in Regex("[0-9][0-9,]*").findAll(source).map { it.value.replace(",", "") })
                assertTrue("$n of «$source» is not in IACAD's statement", n in accuracyStatement(Profiles.DUBAI_IACAD, ar))
            // Every row, opened.
            for (p in Profiles.ALL) {
                val name = if (ar) p.nameAr else p.nameEn
                val statement = accuracyStatement(p, ar)
                // (Methods that share a statement show it once each: one more of it must appear.)
                val before = texts().count { it == statement }
                val row = rule.onAllNodes(SemanticsMatcher("the row of ${p.id}") { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { plain(it.text) == name } }
                    and hasClickAction()).onFirst()
                row.performScrollTo(); row.performClick(); settle()
                assertTrue("${p.id} ($ar): its statement is not shown once its row is opened: «$statement»", texts().count { it == statement } == before + 1)
            }
        }
    }
}
