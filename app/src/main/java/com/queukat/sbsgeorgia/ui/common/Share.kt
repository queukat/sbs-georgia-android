package com.queukat.sbsgeorgia.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

fun Context.sharePlainText(title: String, value: String) {
    if (value.isBlank()) return
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(Intent.createChooser(sendIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.sharePlainTextToTelegramOrChooser(title: String, value: String) {
    if (value.isBlank()) return
    for (packageName in telegramPackageNames) {
        val telegramIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, value)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            startActivity(telegramIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next Telegram package, then fall back to the standard Android share sheet.
        }
    }
    sharePlainText(title, value)
}

private val telegramPackageNames =
    listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )
