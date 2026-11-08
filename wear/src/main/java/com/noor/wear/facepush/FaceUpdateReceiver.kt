package com.noor.wear.facepush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noor.wear.data.StatusPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After the watch app is updated (0.9.8): if Noor's face is on the watch from an older build, it is replaced in place
 * with this build's (the same design and colour, or the Horizon face for a face from 0.9.7), so a new face reaches the
 * watch without the user doing anything. A face that is not installed is left alone.
 */
class FaceUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(25_000) {
                    val push = FacePush(context)
                    val st = push.state()
                    if (st is FaceState.Ready && st.installed && (st.installedBuild ?: 0) < st.bundledBuild) push.installOrUpdate()
                }
                StatusPublisher.publish(context)
            } finally { pending.finish() }
        }
    }
}
