package com.queukat.sbsgeorgia.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeFalse

fun assumePhoneLikeComposeTestDevice() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageManager = context.packageManager
    val uiModeManager = context.getSystemService(UiModeManager::class.java)
    val isLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isTelevisionMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    assumeFalse(
        "Compose UI tests are validated on phone/emulator devices. Android TV devices are skipped intentionally.",
        isLeanback || isTelevisionMode
    )
}
