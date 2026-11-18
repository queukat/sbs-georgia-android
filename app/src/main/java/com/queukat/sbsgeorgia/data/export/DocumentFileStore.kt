package com.queukat.sbsgeorgia.data.export

import android.content.Context
import androidx.core.net.toUri
import com.queukat.sbsgeorgia.data.io.InputLimits
import com.queukat.sbsgeorgia.data.io.readBytesLimited
import com.queukat.sbsgeorgia.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

typealias TextDocumentStore = com.queukat.sbsgeorgia.domain.repository.TextDocumentStore

@Singleton
class AndroidTextDocumentStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TextDocumentStore {
    override suspend fun writeText(uriString: String, content: String) = withContext(ioDispatcher) {
        val uri = uriString.toUri()
        val uriDiag = safeUriDiagnostic(uriString)
        BackupRestoreLog.info("DocumentStore", "writeText start ($uriDiag, chars=${content.length})")
        runLoggedOperation("DocumentStore", "writeText failed ($uriDiag)") {
            val output = context.contentResolver.openOutputStream(uri, "wt")
            if (output == null) {
                BackupRestoreLog.error("DocumentStore", "writeText openOutputStream returned null ($uriDiag)")
                error("Unable to open the selected destination.")
            }
            output.use { stream ->
                stream.writer(Charsets.UTF_8).use { writer -> writer.write(content) }
            }
            BackupRestoreLog.info("DocumentStore", "writeText completed ($uriDiag)")
        }
    }

    override suspend fun readText(uriString: String): String = withContext(ioDispatcher) {
        val uri = uriString.toUri()
        val uriDiag = safeUriDiagnostic(uriString)
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        BackupRestoreLog.info("DocumentStore", "readText start ($uriDiag, mimeType=$mimeType)")
        runLoggedOperation("DocumentStore", "readText failed ($uriDiag)") {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                BackupRestoreLog.error("DocumentStore", "readText openInputStream returned null ($uriDiag)")
                error("Unable to open the selected backup file.")
            }
            val bytes = input.use { stream -> stream.readBytesLimited(InputLimits.BACKUP_BYTES) }
            val text = bytes.toString(Charsets.UTF_8)
            BackupRestoreLog.info(
                "DocumentStore",
                "readText completed ($uriDiag, bytes=${bytes.size}, chars=${text.length})"
            )
            text
        }
    }
}
