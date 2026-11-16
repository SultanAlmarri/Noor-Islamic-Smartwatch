package com.noor.wear.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.noor.alerts.AlertScheduler
import com.noor.prayer.WatchConfig
import com.noor.prayer.alerts.AlertDevice
import com.noor.prayer.alerts.AlertPlanner
import com.noor.wear.R
import com.noor.wear.WearText
import com.noor.wear.data.StatusPublisher
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Shown when the user chose to get prayer alerts on the watch. Asks for what the watch needs (notifications,
 * exact alarms) in plain words, and otherwise shows the next alert.
 */
@Composable
fun AlertSection(c: WatchConfig, t: WearText) {
    val alerts = c.alerts ?: return
    if (!alerts.enabled || !alerts.device.includes(AlertDevice.WATCH)) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val refreshAll: () -> Unit = {
        tick++
        scope.launch { AlertScheduler.reschedule(ctx.applicationContext); StatusPublisher.publish(ctx.applicationContext) }
    }
    val notifGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exact = remember(tick) { AlertScheduler.canScheduleExact(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAll() }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleSmall, color = Gold, textAlign = TextAlign.Center)
        when {
            !notifGranted -> {
                Text(stringResource(R.string.alerts_need_notifications), style = MaterialTheme.typography.bodySmall, color = Mist,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors()) { Text(stringResource(R.string.alerts_allow)) }
            }
            !exact && Build.VERSION.SDK_INT >= 31 -> {
                Text(stringResource(R.string.alerts_need_exact), style = MaterialTheme.typography.bodySmall, color = Mist,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
                Button(onClick = {
                    val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }.onFailure {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) { Text(stringResource(R.string.alerts_allow)) }
            }
            else -> {
                val next = remember(c, tick) { AlertPlanner.plan(c, alerts, AlertDevice.WATCH, Instant.now()).firstOrNull() }
                Text(
                    if (next != null) stringResource(R.string.alerts_next, t.name(next.prayer), t.time(next.at, c.zone)) else stringResource(R.string.alerts_none),
                    style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
