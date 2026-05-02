package com.queukat.sbsgeorgia.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun Context.copyPlainTextToClipboard(label: String, value: String) {
    if (value.isBlank()) return
    val clipboardManager = getSystemService(ClipboardManager::class.java)
    clipboardManager?.setPrimaryClip(ClipData.newPlainText(label, value))
}
