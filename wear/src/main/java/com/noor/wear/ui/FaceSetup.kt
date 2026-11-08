package com.noor.wear.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.noor.prayer.WatchFaces
import com.noor.wear.R
import com.noor.wear.data.StatusPublisher
import com.noor.wear.facepush.FacePush
import com.noor.wear.facepush.FaceState
import kotlinx.coroutines.launch

/**
 * The Noor watch face on the watch (0.9.8): add it, keep it up to date, make it the active face (once, as Wear OS
 * allows), and choose its design and colour here as well as from the phone.
 */
private class FaceFlow(val push: FacePush) {
    var state by mutableStateOf<FaceState?>(null)
    var busy by mutableStateOf(false)
    var permissionBlocked by mutableStateOf(false)
}

@Composable
private fun rememberFaceFlow(autoStart: Boolean): Triple<FaceFlow, () -> Unit, (String) -> Unit> {
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
    // Brings the chosen face onto the watch, then (if this app still may) asks to make it the active one.
    suspend fun settle(start: FaceState) {
        var st = start
        if (st is FaceState.Ready && (!st.installed || st.updateAvailable)) st = flow.push.installOrUpdate()
        if (st is FaceState.Ready && st.installed && !st.active && !st.activationUsed) {
            if (context.checkSelfPermission(FacePush.PERMISSION_ACTIVATE) == PackageManager.PERMISSION_GRANTED) st = flow.push.activate()
            else permission.launch(FacePush.PERMISSION_ACTIVATE)
        }
        flow.state = st
    }
    val run: () -> Unit = {
        scope.launch {
            flow.busy = true
            settle(flow.push.state())
            flow.busy = false
            StatusPublisher.publish(context)
        }
    }
    val choose: (String) -> Unit = { id ->
        scope.launch {
            flow.busy = true
            settle(flow.push.choose(id))
            flow.busy = false
            StatusPublisher.publish(context)
        }
    }
    LaunchedEffect(Unit) {
        flow.state = flow.push.state()
        if (autoStart) run()
    }
    return Triple(flow, run, choose)
}

/** The face entry at the end of Settings: what is on the watch, and the designs. */
@Composable
fun FaceSection(autoStart: Boolean) {
    val (flow, run, choose) = rememberFaceFlow(autoStart)
    val s = flow.state ?: return
    if (s is FaceState.Unsupported || s is FaceState.NotBundled) return // Nothing the user can act on here.
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.watch_face), style = MaterialTheme.typography.labelMedium, color = Gold)
        Spacer(Modifier.height(4.dp))
        FaceStatus(flow, run)
        if (s is FaceState.Ready && !flow.busy) FacePicker(s.chosen, choose)
    }
}

/** Full-screen version, opened from the phone or from a link. */
@Composable
fun FaceSetupScreen() {
    val (flow, run, choose) = rememberFaceFlow(autoStart = true)
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
            (flow.state as? FaceState.Ready)?.let { s -> if (!flow.busy) item { FacePicker(s.chosen, choose) } }
        }
    }
}

@Composable
private fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = Mist, textAlign = TextAlign.Center)

/** "Horizon · Gold" (the original face has its own colours). */
@Composable
internal fun faceName(id: String?): String {
    val c = WatchFaces.parse(id) ?: return ""
    val design = stringResource(designName(c.design))
    return if (c.design == WatchFaces.Design.ORIGINAL) design else design + " · " + stringResource(colourName(c.colour))
}

internal fun designName(d: WatchFaces.Design) = when (d) {
    WatchFaces.Design.HORIZON -> R.string.face_design_horizon
    WatchFaces.Design.DIAL -> R.string.face_design_dial
    WatchFaces.Design.MINIMAL -> R.string.face_design_minimal
    WatchFaces.Design.ORIGINAL -> R.string.face_design_original
}

internal fun colourName(c: WatchFaces.Colour) = when (c) {
    WatchFaces.Colour.GOLD -> R.string.face_colour_gold
    WatchFaces.Colour.EMERALD -> R.string.face_colour_emerald
    WatchFaces.Colour.TURQUOISE -> R.string.face_colour_turquoise
    WatchFaces.Colour.ROSE -> R.string.face_colour_rose
    WatchFaces.Colour.SILVER -> R.string.face_colour_silver
}

@Composable
private fun FaceStatus(flow: FaceFlow, run: () -> Unit) {
    val s = flow.state
    when {
        flow.busy -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(20.dp)); Info(stringResource(R.string.face_working)) }
        s is FaceState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Info(stringResource(R.string.face_error, s.message))
            Button(onClick = run, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(stringResource(R.string.try_again))
            }
        }
        s is FaceState.Ready && s.active && !s.updateAvailable -> Info(stringResource(R.string.face_in_use, faceName(s.installedVariant)))
        // Wear OS lets an app make its face active only once: from here the user chooses it, and that is no error.
        s is FaceState.Ready && !s.updateAvailable && (s.needsPick || (flow.permissionBlocked && s.installed && !s.active)) ->
            Info(stringResource(R.string.face_pick_hint))
        s is FaceState.Ready -> {
            val label = when {
                !s.installed -> R.string.face_install
                s.updateAvailable -> R.string.face_update
                else -> R.string.face_activate
            }
            Button(onClick = run, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label), maxLines = 2)
            }
        }
    }
}

/** Design and colour, chosen on the watch (the phone's editor offers the same, with large previews). */
@Composable
private fun FacePicker(chosen: String, choose: (String) -> Unit) {
    val c = WatchFaces.parse(chosen) ?: WatchFaces.DEFAULT
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.face_design), style = MaterialTheme.typography.labelSmall, color = Mist)
        for (d in WatchFaces.Design.entries) {
            val selected = d == c.design
            Button(
                onClick = { choose(WatchFaces.Choice(d, c.colour).id) },
                colors = if (selected) ButtonDefaults.filledTonalButtonColors(containerColor = Gold.copy(alpha = 0.22f), contentColor = Gold)
                else ButtonDefaults.filledTonalButtonColors(containerColor = Night, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { this.selected = selected },
            ) { Text(stringResource(designName(d)), maxLines = 1) }
        }
        if (c.design != WatchFaces.Design.ORIGINAL) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.face_colour), style = MaterialTheme.typography.labelSmall, color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (col in WatchFaces.Colour.entries) {
                    val selected = col == c.colour
                    val name = stringResource(colourName(col))
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(Color(col.argb))
                            .border(if (selected) 2.5.dp else 0.dp, if (selected) Ink else Color.Transparent, CircleShape)
                            .clickable { choose(WatchFaces.Choice(c.design, col).id) }
                            .semantics { contentDescription = name; this.selected = selected },
                    )
                }
            }
        }
    }
}
