package com.noor.app.bench

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.StrictMode

/**
 * The bench build only (tools/devicetest/smooth.sh): Android's StrictMode on the main thread from the start of the
 * process, logging (tag StrictMode) every disk read or write, every slow call and every unbuffered stream there: each
 * is a frame the screen may wait for. Logging only; nothing is stopped.
 */
class BenchStrictMode : ContentProvider() {
    override fun onCreate(): Boolean {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectCustomSlowCalls().detectUnbufferedIo().detectNetwork()
                .penaltyLog()
                .build(),
        )
        return true
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
