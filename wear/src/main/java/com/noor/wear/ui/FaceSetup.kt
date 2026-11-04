package com.noor.wear.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.noor.wear.R
import com.noor.wear.data.StatusPublisher
import com.noor.wear.facepush.FacePush
import com.noor.wear.facepush.FaceState
import kotlinx.coroutines.launch

/**
 * One flow for the Noor watch face: add it (or update it) and make it the active face. Making it active needs a
 * one-time permission the user grants on the watch; Wear OS lets an app do this only once, and if the user declines,
 * only from the watch's settings afterwards.
 */
private class FaceFlow(val push: FacePush) {
    var state by mutableStateOf<FaceState?>(null)
    var busy by mutableStateOf(false)
    var permissionBlocked by mutableStateOf(false)
}

@Composable
private fun rememberFaceFlow(autoStart: Boolean): Pair<FaceFlow, () -> Unit> {
    val context = LocalContext.current
    val flow = remember { FaceFlow(FacePush(context)) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            if (granted) { flow.busy = true; flow.state = flow.push.activate(); flow.busy = false }
            else flow.permissionBlocked = (context as? Activity)?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, FacePush.PERMISSION_ACTIVATE)
            } ?: true
            StatusPublisher.publish(context)
        }
    }
    val run: () -> Unit = {
        scope.launch {
            flow.busy = true
            var st = flow.push.state()
            if (st is FaceState.Ready && (!st.installed || st.updateAvailable)) st = flow.push.installOrUpdate()
            if (st is FaceState.Ready && st.installed && !st.active) {
                if (context.checkSelfPermission(FacePush.PERMISSION_ACTIVATE) == PackageManager.PERMISSION_GRANTED) st = flow.push.activate()
                else permission.launch(FacePush.PERMISSION_ACTIVATE)
            }
            flow.state = st; flow.busy = false
            StatusPublisher.publish(context)
        }
    }
    LaunchedEffect(Unit) {
        flow.state = flow.push.state()
        if (autoStart) run()
    }
    return flow to run
}

/** The face entry at the end of the main list. */
@Composable
fun FaceSection(autoStart: Boolean) {
    val (flow, run) = rememberFaceFlow(autoStart)
    val s = flow.state ?: return
    if (s is FaceState.Unsupported || s is FaceState.NotBundled) return // Nothing the user can act on here.
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.watch_face), style = MaterialTheme.typography.labelMedium, color = Gold)
        Spacer(Modifier.height(4.dp))
        FaceStatus(flow, run)
    }
}

/** Full-screen version, opened from the phone ("Use the Noor watch face"). */
@Composable
fun FaceSetupScreen() {
    val (flow, run) = rememberFaceFlow(autoStart = true)
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(52.dp), tint = Color.Unspecified) }
            item { Text(stringResource(R.string.watch_face), style = MaterialTheme.typography.titleMedium, color = Gold) }
            item {
                when (val s = flow.state) {
                    null -> CircularProgressIndicator(Modifier.size(28.dp))
                    is FaceState.Unsupported -> Info(stringResource(R.string.face_needs_wear6))
                    is FaceState.NotBundled -> Info(stringResource(R.string.face_unavailable))
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { FaceStatus(flow, run) }
                }
            }
        }
    }
}

@Composable
private fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center)

@Composable
private fun FaceStatus(flow: FaceFlow, run: () -> Unit) {
    val s = flow.state
    when {
        flow.busy -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(20.dp)); Info(stringResource(R.string.face_working)) }
        flow.permissionBlocked -> Info(stringResource(R.string.face_permission_settings))
        s is FaceState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Info(stringResource(R.string.face_error, s.message))
            Button(onClick = run, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(stringResource(R.string.try_again))
            }
        }
        s is FaceState.Ready && s.active && !s.updateAvailable -> Info(stringResource(R.string.face_active))
        s is FaceState.Ready -> {
            val label = when {
                !s.installed -> R.string.face_install
                s.updateAvailable -> R.string.face_update
                else -> R.string.face_activate
            }
            Button(onClick = run, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label), maxLines = 2)
            }
            if (s.installed && !s.active) Info(stringResource(R.string.face_added))
        }
    }
}
