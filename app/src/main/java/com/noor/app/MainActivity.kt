package com.noor.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.noor.app.data.AppSettings
import com.noor.app.ui.components.NoorBackground
import com.noor.app.ui.onboarding.OnboardingScreen
import com.noor.app.ui.prayers.PrayersScreen
import com.noor.app.ui.settings.AccuracyScreen
import com.noor.app.ui.settings.CityPickerScreen
import com.noor.app.ui.settings.SettingsScreen
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.watch.WatchScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as NoorApplication).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val s = settings ?: AppSettings()
            NoorTheme(s.theme) {
                NoorBackground {
                    if (settings != null) NoorRoot(s)
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val PRAYERS = "prayers"
    const val WATCH = "watch"
    const val SETTINGS = "settings"
    const val CITIES = "cities"
    const val ACCURACY = "accuracy"
}

@Composable
private fun NoorRoot(s: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = route in setOf(Routes.PRAYERS, Routes.WATCH, Routes.SETTINGS)
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (topLevel) NoorBottomBar(nav, route) },
    ) { padding ->
        val content = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = if (s.onboardingDone) Routes.PRAYERS else Routes.ONBOARDING,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(content,
                    onChooseCity = { nav.navigate(Routes.CITIES) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onFinished = { nav.navigate(Routes.PRAYERS) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
            }
            composable(Routes.PRAYERS) {
                PrayersScreen(content, onOpenLocation = { nav.navigate(Routes.SETTINGS) }, onOpenAccuracy = { nav.navigate(Routes.ACCURACY) })
            }
            composable(Routes.WATCH) { WatchScreen(content) }
            composable(Routes.SETTINGS) {
                SettingsScreen(content, onOpenCities = { nav.navigate(Routes.CITIES) }, onOpenAccuracy = { nav.navigate(Routes.ACCURACY) })
            }
            composable(Routes.CITIES) {
                CityPickerScreen(content, onDone = {
                    if (!nav.popBackStack()) nav.navigate(Routes.PRAYERS)
                })
            }
            composable(Routes.ACCURACY) { AccuracyScreen(content, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun NoorBottomBar(nav: NavHostController, route: String?) {
    val c = NoorTheme.colors
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.gold, selectedTextColor = c.gold, indicatorColor = c.gold.copy(alpha = 0.14f),
        unselectedIconColor = c.textMuted, unselectedTextColor = c.textMuted,
    )
    fun go(r: String) = nav.navigate(r) {
        popUpTo(Routes.PRAYERS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    NavigationBar(containerColor = c.backgroundBottom.copy(alpha = 0.92f), tonalElevation = 0.dp) {
        NavigationBarItem(route == Routes.PRAYERS, { go(Routes.PRAYERS) },
            icon = { Icon(painterResource(R.drawable.ic_prayer_isha), null, modifier = Modifier.padding(1.dp)) },
            label = { Text(stringResource(R.string.tab_prayers)) }, colors = colors)
        NavigationBarItem(route == Routes.WATCH, { go(Routes.WATCH) },
            icon = { Icon(Icons.Rounded.Watch, null) }, label = { Text(stringResource(R.string.tab_watch)) }, colors = colors)
        NavigationBarItem(route == Routes.SETTINGS, { go(Routes.SETTINGS) },
            icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text(stringResource(R.string.tab_settings)) }, colors = colors)
    }
}
