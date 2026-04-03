package com.queukat.sbsgeorgia.startup

import android.os.Process
import android.os.SystemClock
import android.util.Log

object StartupTiming {
    private const val TAG = "SbsStartup"

    fun mark(stage: String) {
        val sinceProcessStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.i(TAG, "$stage at ${sinceProcessStart}ms")
    }
}
