package com.noor.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isArabicUi(): Boolean = LocalConfiguration.current.locales[0].language == "ar"
