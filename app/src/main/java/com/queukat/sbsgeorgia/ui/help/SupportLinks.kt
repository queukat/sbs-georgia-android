package com.queukat.sbsgeorgia.ui.help

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import java.util.Locale

fun openPlayStoreListing(context: Context): Boolean {
    val marketIntent =
        Intent(
            Intent.ACTION_VIEW,
            "market://details?id=${context.packageName}".toUri()
        ).setPackage("com.android.vending")
    if (context.safeStartActivity(marketIntent)) {
        return true
    }
    return context.safeStartActivity(
        Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()
        )
    )
}

fun openFeedbackPage(context: Context): Boolean = context.safeStartActivity(
    Intent(
        Intent.ACTION_VIEW,
        buildFeedbackIssueUri(context)
    )
)

private fun buildFeedbackIssueUri(context: Context): Uri {
    val packageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags
                    .of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    val versionName = packageInfo.versionName ?: "unknown"
    val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    val body =
        """
        ## What happened?
        <!-- Please do not include taxpayer IDs, imported PDFs, or backup/export files. -->

        ## Steps to reproduce

        ## Expected result

        ## Environment
        - App version: $versionName ($versionCode)
        - Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        - Device: ${Build.MANUFACTURER} ${Build.MODEL}
        - App language: ${Locale.getDefault().toLanguageTag()}
        """.trimIndent()

    return "https://github.com/queukat/sbs-georgia-android/issues/new"
        .toUri()
        .buildUpon()
        .appendQueryParameter("title", "[Feedback] ")
        .appendQueryParameter("body", body)
        .build()
}

private fun Context.safeStartActivity(intent: Intent): Boolean {
    val preparedIntent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        startActivity(preparedIntent)
        true
    }.getOrDefault(false)
}
