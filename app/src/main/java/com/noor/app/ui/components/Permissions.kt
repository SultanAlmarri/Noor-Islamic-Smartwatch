package com.noor.app.ui.components

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

/** The activity behind a Compose context, if any. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Asks for a permission, and once Android no longer shows its dialog (the user said no twice, or "Don't ask again"),
 * opens the page in Android's settings where it can be allowed instead (0.18: the request used to do nothing then).
 * [settings] is that page; [onResult] gets whether it was granted.
 */
@Composable
fun rememberPermissionRequest(permission: String, settings: (Context) -> Intent, onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            val activity = context.findActivity()
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                runCatching { context.startActivity(settings(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        result(granted)
    }
    return { launcher.launch(permission) }
}

/** The notification permission (Android 13+); below 13 there is nothing to ask, and [onResult] gets true. */
@Composable
fun rememberNotificationRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val result by rememberUpdatedState(onResult)
    val ask = rememberPermissionRequest(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else "", ::notificationSettings, onResult)
    return { if (Build.VERSION.SDK_INT >= 33) ask() else result(true) }
}

/** Android's notification settings page for this app. */
fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Android's page for this app (permissions, battery…). */
fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
