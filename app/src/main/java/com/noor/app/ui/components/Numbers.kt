package com.noor.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.noor.app.NoorApplication
import com.noor.app.data.Numerals
import com.noor.app.data.TimeFormat
import com.noor.app.util.Formatter

/**
 * (0.25, second review) The app's [Formatter] for a page that has no settings of its own at hand: numbers on it follow
 * the digits chosen in Minhad (Settings → Language and region → Numerals). Until 0.25 a few sentences carried their
 * numbers written in ("١٠ ثوانٍ", "Android 16") and so ignored the choice; every number a person reads now goes through
 * [Formatter.number]. (`NumeralsInStringsTest` fails if an Arabic string resource holds an Arabic-Indic digit again.)
 */
@Composable
fun rememberAppFormatter(): Formatter {
    val app = LocalContext.current.applicationContext as? NoorApplication
    val s by (app?.container?.settings?.settings ?: kotlinx.coroutines.flow.emptyFlow()).collectAsState(initial = null)
    val ar = isArabicUi()
    return remember(ar, s?.numerals, s?.timeFormat) { Formatter(ar, s?.numerals ?: Numerals.WESTERN, s?.timeFormat ?: TimeFormat.H12) }
}
