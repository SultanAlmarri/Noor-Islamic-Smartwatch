package com.noor.wear.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
    private val vm: WearViewModel by viewModels()

    /** A screen to open: from a tile, a complication, or the phone (noor://face/activate). */
    private var pending by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pending = intent.destination()
        val repo = (application as NoorWearApp).config
        lifecycleScope.launch {
            // Catch up with anything the phone published while this app was not running, then report back.
            repo.pullFromDataLayer()
            StatusPublisher.publish(this@MainActivity)
        }
        setContent { NoorWearNav(vm, pending) { pending = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pending = intent.destination()
    }

    private fun Intent?.destination(): String? = when {
        this == null -> null
        dataString == WatchReport.LINK_ACTIVATE_FACE -> Dest.FACE
        else -> getStringExtra(EXTRA_DEST)?.takeIf { it in Dest.external }
    }

    companion object {
        const val EXTRA_DEST = "com.noor.wear.DEST"

        /** Opens the app on [dest] (one of [Dest.external]). */
        fun intent(context: Context, dest: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_DEST, dest).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
