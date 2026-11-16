package com.noor.wear.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * 0.18: asks for the notification permission; once Android no longer shows its dialog (refused for good), opens the
 * watch's page for the app instead, so the button never does nothing.
 */
@Composable
internal fun rememberNotificationRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            val a = context.activity()
            val prefs = context.getSharedPreferences("minhad_permissions", android.content.Context.MODE_PRIVATE)
            val before = prefs.getBoolean(Manifest.permission.POST_NOTIFICATIONS, false)
            prefs.edit().putBoolean(Manifest.permission.POST_NOTIFICATIONS, true).apply()
            // (Not when the dialog was only closed the first time: Android gives "no rationale" then as well.)
            if (a != null && before && !ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.POST_NOTIFICATIONS)) runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        result(granted)
    }
    return { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else result(true) }
}
