package com.noor.wear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.noor.prayer.WatchReport
import com.noor.wear.NoorWearApp
import com.noor.wear.data.StatusPublisher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Opened from the phone with noor://face/activate: go straight to the watch-face setup. */
    private var faceSetup by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        faceSetup = intent.isFaceSetup()
        val repo = (application as NoorWearApp).config
        lifecycleScope.launch {
            // Catch up with anything the phone published while this app was not running, then report back.
            repo.pullFromDataLayer()
            StatusPublisher.publish(this@MainActivity)
        }
        setContent { NoorWearUi(repo, faceSetup) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        faceSetup = intent.isFaceSetup()
    }

    private fun Intent?.isFaceSetup() = this?.dataString == WatchReport.LINK_ACTIVATE_FACE
}
