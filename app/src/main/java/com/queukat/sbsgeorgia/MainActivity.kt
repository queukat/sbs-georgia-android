package com.queukat.sbsgeorgia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.startup.StartupTiming
import com.queukat.sbsgeorgia.ui.SbsGeorgiaApp
import com.queukat.sbsgeorgia.widget.HomeWidgetRefreshObserver
import com.queukat.sbsgeorgia.widget.requestHomeWidgetsUpdate
import com.queukat.sbsgeorgia.worker.ReminderBootstrapper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var reminderBootstrapper: ReminderBootstrapper

    @Inject
    lateinit var homeWidgetRefreshObserver: HomeWidgetRefreshObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTiming.mark("MainActivity.onCreate.start")
        setTheme(R.style.Theme_SbsGeorgiaAndroid)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StartupTiming.mark("MainActivity.edgeToEdge")
        setContent {
            SbsGeorgiaApp()
        }
        StartupTiming.mark("MainActivity.setContent")
        window.decorView.post {
            StartupTiming.mark("MainActivity.post.firstFrameQueue")
            reminderBootstrapper.scheduleStoredConfigIfPresent()
            homeWidgetRefreshObserver.ensureStarted()
            requestHomeWidgetsUpdate(this)
            StartupTiming.mark("Reminder bootstrap queued")
        }
    }
}
