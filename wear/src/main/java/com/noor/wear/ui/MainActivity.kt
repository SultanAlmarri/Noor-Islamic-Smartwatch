package com.noor.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.noor.wear.NoorWearApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val repo = (application as NoorWearApp).config
        // Catch up with anything the phone published while this app was not running.
        lifecycleScope.launch { repo.pullFromDataLayer() }
        setContent { NoorWearUi(repo) }
    }
}
