package com.noor.app.adhan

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.alerts.AdhanService
import com.noor.app.R
import com.noor.app.data.ThemeMode
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.ui.theme.Sky

/**
 * The Adhan screen (0.9.7): shown over the lock screen while the Adhan plays, as an alarm clock shows its ringing
 * screen, with the prayer and a large Stop. It closes by itself when the Adhan ends. Being on screen also satisfies
 * Android 17's rule that only apps the user can see (or that are properly in the foreground) may play sound.
 */
class AdhanAlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val now by AdhanService.nowPlaying.collectAsState()
            LaunchedEffect(now) { if (now == null) finish() }
            NoorTheme(ThemeMode.DARK) {
                AdhanScreen(now, onStop = { AdhanService.stop(this); finish() }, onClose = { finish() })
            }
        }
    }
}

@Composable
internal fun AdhanScreen(now: AdhanService.Playing?, onStop: () -> Unit, onClose: () -> Unit) {
    val sky = Sky.colors(-6.0, true)
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky.zenith, sky.mid, sky.horizon))).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_notification), null, tint = Sky.GoldOnSky, modifier = Modifier.size(44.dp))
            Text(now?.title ?: "", style = MaterialTheme.typography.displaySmall, color = Sky.OnSky, textAlign = TextAlign.Center)
            if (!now?.body.isNullOrBlank()) Text(now!!.body, style = MaterialTheme.typography.titleMedium, color = Sky.OnSkySecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(onClick = onStop, shape = CircleShape, modifier = Modifier.size(132.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky.GoldOnSky, contentColor = Color(0xFF14110A))) {
                Text(stringResource(R.string.adhan_screen_stop), fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.adhan_screen_close), color = Sky.OnSkySecondary, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
