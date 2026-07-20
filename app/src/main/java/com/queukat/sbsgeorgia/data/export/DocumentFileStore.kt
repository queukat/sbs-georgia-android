package com.queukat.sbsgeorgia.data.export

import android.content.Context
import androidx.core.net.toUri
import com.queukat.sbsgeorgia.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface TextDocumentStore {
    suspend fun writeText(uriString: String, content: String)

    suspend fun readText(uriString: String): String
}

@Singleton
class AndroidTextDocumentStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TextDocumentStore {
    override suspend fun writeText(uriString: String, content: String) = withContext(ioDispatcher) {
        val uri = uriString.toUri()
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.writer(Charsets.UTF_8).use { writer -> writer.write(content) }
        } ?: error("Unable to open the selected destination.")
    }

    override suspend fun readText(uriString: String): String = withContext(ioDispatcher) {
        val uri = uriString.toUri()
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        } ?: error("Unable to open the selected backup file.")
    }
}
