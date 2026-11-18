package com.queukat.sbsgeorgia.data.export

import android.util.Log
import kotlinx.coroutines.CancellationException

internal object BackupRestoreLog {
    const val TAG = "SbsBackupRestore"

    fun info(stage: String, message: String) {
        safeLog { Log.i(TAG, "[$stage] $message") }
    }

    fun warn(stage: String, message: String) {
        safeLog { Log.w(TAG, "[$stage] $message") }
    }

    fun error(stage: String, message: String, throwable: Throwable? = null) {
        safeLog {
            if (throwable != null) {
                Log.e(TAG, "[$stage] $message", throwable)
            } else {
                Log.e(TAG, "[$stage] $message")
            }
        }
    }

    private inline fun safeLog(block: () -> Int) {
        runCatching { block() }.onFailure { error ->
            if (error !is RuntimeException || error.message?.contains("not mocked") != true) {
                throw error
            }
        }
    }
}

internal inline fun <T> runLoggedOperation(stage: String, failureMessage: String, block: () -> T): T =
    runCatching { block() }.getOrElse { error ->
        if (error is CancellationException) {
            BackupRestoreLog.warn(stage, "cancelled ($failureMessage)")
            throw error
        }
        BackupRestoreLog.error(stage, failureMessage, error)
        throw error
    }

internal fun Throwable.toBackupDiagnosticSummary(maxCauses: Int = 5): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current) && parts.size < maxCauses) {
        val type = current::class.java.simpleName.ifBlank { "Throwable" }
        val detail = current.message?.trim()?.takeIf(String::isNotBlank)
        parts += if (detail != null) "$type: $detail" else type
        current = current.cause
    }
    return parts.joinToString(separator = "\nCaused by: ")
}

internal fun safeUriDiagnostic(uriString: String): String {
    val scheme = uriString.substringBefore("://", missingDelimiterValue = "unknown")
    val afterScheme = uriString.substringAfter("://", missingDelimiterValue = "")
    val authority = afterScheme.substringBefore('/', missingDelimiterValue = "none")
    return "scheme=$scheme, authority=$authority, uriLength=${uriString.length}"
}
